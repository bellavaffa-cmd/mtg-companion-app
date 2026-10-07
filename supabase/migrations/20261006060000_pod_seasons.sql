-- Manabind — league mode: a pod runs seasons over its shared game log.
--
-- NOT applied automatically. The owner reviews it and runs it by hand (Supabase SQL editor, or
-- `npx supabase db push`). Safe to run more than once: the table and indexes are "if not exists",
-- functions "create or replace". Nothing here deletes or drops anything.
--
-- The apps call pod_seasons() and say "Leagues aren't available yet" until this exists
-- (Android: data/League.kt and ui/lifecounter/LeagueView.kt; web: src/decks/league.ts and
-- src/pages/LeagueView.tsx).
--
-- A season is a name, the days it runs (starts_on to ends_on, or until max_nights game nights have
-- been played) and its scoring rules. It holds no games of its own: the games that count are the
-- pod's games (public.pod_games, 20261005000000_pod_games.sql) played in its days. The standings —
-- points, wins, streaks, points per game night, tie-breaks — are worked out by the apps from that
-- log, the way Playgroup stats already are: every member downloads the same log and the same rules,
-- so everyone sees the same table, and the scoring lives in one tested place per app (League.kt,
-- league.ts) instead of being written a third time in SQL. When a season ends, whoever ends it sends
-- the final table and the champion, which are kept as they were (a game deleted later doesn't
-- rewrite a past season).
--
-- One more thing scoring needs: who came second, and who drew first blood. clean_pod_players (from
-- 20261005000000_pod_games.sql) is replaced so a seat may also carry "place" (1–10) and
-- "firstBlood" (true, one seat per game). Everything else it checks is unchanged; games without
-- those keys are stored exactly as before.
--
-- Rules every function checks: signed in with a profile, and a member of the pod. Anyone in the pod
-- starts a season (one running at a time per pod, 100 per pod in all); whoever started it, or the
-- pod's owner, changes or ends it.

create table if not exists public.pod_seasons (
  id uuid primary key default gen_random_uuid(),
  pod_id uuid not null references public.pods (id) on delete cascade,
  name text not null check (char_length(btrim(name)) between 1 and 40),
  starts_on date not null,
  ends_on date,
  max_nights integer check (max_nights is null or max_nights between 1 and 100),
  -- {"preset": text, "win": n, "second": n, "draw": n, "played": n, "firstBlood": n, "newDeckWin": n}, n 0–10.
  rules jsonb not null default '{}'::jsonb check (jsonb_typeof(rules) = 'object' and pg_column_size(rules) <= 2048),
  created_by uuid references public.profiles (user_id) on delete set null,
  created_at timestamptz not null default now(),
  ended_at timestamptz,
  -- The champion's name ("Priya", or "Priya & Sam" for a shared title); null when nobody played.
  champion text check (champion is null or char_length(champion) <= 200),
  -- The final table: [{"key", "userId", "name", "rank", "points", "games", "wins", "losses", "draws", "streak"}].
  standings jsonb check (standings is null or (jsonb_typeof(standings) = 'array' and pg_column_size(standings) <= 32768)),
  check (ends_on is null or ends_on >= starts_on)
);
create index if not exists pod_seasons_pod_idx on public.pod_seasons (pod_id, starts_on desc);
-- One running season per pod.
create unique index if not exists pod_seasons_running_idx on public.pod_seasons (pod_id) where ended_at is null;
alter table public.pod_seasons enable row level security;
revoke all on public.pod_seasons from anon, authenticated;

-- ============================================================================================
-- Helpers (private)
-- ============================================================================================

-- Checks a season's rules and returns them cleaned: only the known keys, each point value a whole
-- number 0–10.
create or replace function social_private.clean_season_rules(p_rules jsonb)
returns jsonb
language plpgsql
immutable
set search_path = ''
as $$
declare
  k text;
  v jsonb;
  cleaned jsonb := '{}'::jsonb;
begin
  if p_rules is null or jsonb_typeof(p_rules) <> 'object' then
    raise exception 'bad_season' using errcode = 'P0001';
  end if;
  foreach k in array array['win', 'second', 'draw', 'played', 'firstBlood', 'newDeckWin'] loop
    v := p_rules -> k;
    if v is null or v = 'null'::jsonb then continue; end if;
    if jsonb_typeof(v) <> 'number' or (v #>> '{}')::numeric not between 0 and 10
        or (v #>> '{}')::numeric <> trunc((v #>> '{}')::numeric) then
      raise exception 'bad_season' using errcode = 'P0001';
    end if;
    cleaned := cleaned || jsonb_build_object(k, (v #>> '{}')::integer);
  end loop;
  if jsonb_typeof(p_rules -> 'preset') = 'string' then
    cleaned := cleaned || jsonb_build_object('preset', left(p_rules ->> 'preset', 20));
  end if;
  return cleaned;
end;
$$;

-- Checks a season's name and days.
create or replace function social_private.check_season(p_name text, p_starts_on date, p_ends_on date, p_max_nights integer)
returns void
language plpgsql
immutable
set search_path = ''
as $$
begin
  if char_length(btrim(coalesce(p_name, ''))) not between 1 and 40 or p_starts_on is null
      or (p_ends_on is not null and p_ends_on < p_starts_on)
      or (p_max_nights is not null and p_max_nights not between 1 and 100) then
    raise exception 'bad_season' using errcode = 'P0001';
  end if;
end;
$$;

-- The season [p_season], for whoever started it or the pod's owner; raises otherwise.
create or replace function social_private.my_season(p_season uuid, uid uuid)
returns public.pod_seasons
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  s public.pod_seasons;
begin
  select * into s from public.pod_seasons where id = p_season;
  if s.id is null or not social_private.is_pod_member(s.pod_id, uid) then
    raise exception 'not_in_pod' using errcode = 'P0001';
  end if;
  if s.created_by is distinct from uid and not exists (select 1 from public.pods p where p.id = s.pod_id and p.owner = uid) then
    raise exception 'not_season_owner' using errcode = 'P0001';
  end if;
  if s.ended_at is not null then
    raise exception 'season_over' using errcode = 'P0001';
  end if;
  return s;
end;
$$;

-- A pod game's players, as in 20261005000000_pod_games.sql, now also keeping each seat's "place"
-- (1–10) and "firstBlood" (true, at most one seat) when they're given.
create or replace function social_private.clean_pod_players(p_pod uuid, p_players jsonb)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  p jsonb;
  cleaned jsonb := '[]'::jsonb;
  seat jsonb;
  pname text;
  presult text;
  puser uuid;
  wins integer := 0;
  bloods integer := 0;
begin
  if p_players is null or jsonb_typeof(p_players) <> 'array'
      or jsonb_array_length(p_players) not between 2 and 10 then
    raise exception 'bad_players' using errcode = 'P0001';
  end if;
  for p in select * from jsonb_array_elements(p_players) loop
    pname := btrim(coalesce(p ->> 'name', ''));
    presult := upper(coalesce(p ->> 'result', ''));
    if char_length(pname) not between 1 and 40 or presult not in ('WIN', 'LOSS', 'DRAW') then
      raise exception 'bad_players' using errcode = 'P0001';
    end if;
    if presult = 'WIN' then wins := wins + 1; end if;
    puser := null;
    if coalesce(p ->> 'userId', '') ~ '^[0-9a-f-]{36}$' then
      puser := (p ->> 'userId')::uuid;
      if not social_private.is_pod_member(p_pod, puser) then puser := null; end if;
      if puser is not null and puser <> auth.uid() and social_private.blocked_either(auth.uid(), puser) then puser := null; end if;
    end if;
    seat := jsonb_build_object(
      'userId', puser,
      'name', pname,
      'commander', nullif(left(btrim(coalesce(p ->> 'commander', '')), 120), ''),
      'deck', nullif(left(btrim(coalesce(p ->> 'deck', '')), 80), ''),
      'result', presult);
    if p ? 'place' and p -> 'place' <> 'null'::jsonb then
      if jsonb_typeof(p -> 'place') <> 'number' or (p ->> 'place') !~ '^([1-9]|10)$' then
        raise exception 'bad_players' using errcode = 'P0001';
      end if;
      seat := seat || jsonb_build_object('place', (p ->> 'place')::integer);
    end if;
    if p -> 'firstBlood' = 'true'::jsonb then
      bloods := bloods + 1;
      seat := seat || jsonb_build_object('firstBlood', true);
    end if;
    cleaned := cleaned || jsonb_build_array(seat);
  end loop;
  if wins > 1 or bloods > 1 then
    raise exception 'bad_players' using errcode = 'P0001';
  end if;
  return cleaned;
end;
$$;

-- ============================================================================================
-- Functions
-- ============================================================================================

-- A pod's seasons, newest first, for its members. Days as 'YYYY-MM-DD', times in milliseconds.
create or replace function public.pod_seasons(p_pod uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me();
begin
  if not social_private.is_pod_member(p_pod, uid) then
    raise exception 'not_in_pod' using errcode = 'P0001';
  end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
      'id', s.id, 'podId', s.pod_id, 'name', s.name,
      'startsOn', to_char(s.starts_on, 'YYYY-MM-DD'),
      'endsOn', to_char(s.ends_on, 'YYYY-MM-DD'),
      'maxNights', s.max_nights, 'rules', s.rules, 'createdBy', s.created_by,
      'createdAt', (extract(epoch from s.created_at) * 1000)::bigint,
      'endedAt', (extract(epoch from s.ended_at) * 1000)::bigint,
      'champion', s.champion, 'standings', s.standings)
      order by s.starts_on desc, s.created_at desc)
    from public.pod_seasons s where s.pod_id = p_pod
  ), '[]'::jsonb);
end;
$$;

-- A pod member starts a season; answers its id. 'season_running' while another one hasn't ended.
create or replace function public.create_pod_season(
  p_pod uuid, p_name text, p_starts_on date, p_ends_on date, p_max_nights integer, p_rules jsonb)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me_with_profile();
  cleaned jsonb;
  season uuid;
begin
  if not social_private.is_pod_member(p_pod, uid) then
    raise exception 'not_in_pod' using errcode = 'P0001';
  end if;
  perform social_private.check_season(p_name, p_starts_on, p_ends_on, p_max_nights);
  cleaned := social_private.clean_season_rules(p_rules);
  if exists (select 1 from public.pod_seasons where pod_id = p_pod and ended_at is null) then
    raise exception 'season_running' using errcode = 'P0001';
  end if;
  if (select count(*) from public.pod_seasons where pod_id = p_pod) >= 100 then
    raise exception 'too_many_seasons' using errcode = 'P0001';
  end if;
  begin
    insert into public.pod_seasons (pod_id, name, starts_on, ends_on, max_nights, rules, created_by)
    values (p_pod, btrim(p_name), p_starts_on, p_ends_on, p_max_nights, cleaned, uid)
    returning id into season;
  exception when unique_violation then
    -- Two members started one at the same moment.
    raise exception 'season_running' using errcode = 'P0001';
  end;
  return season;
end;
$$;

-- Whoever started a running season, or the pod's owner, changes its name, days or rules.
create or replace function public.update_pod_season(
  p_season uuid, p_name text, p_starts_on date, p_ends_on date, p_max_nights integer, p_rules jsonb)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me_with_profile();
  s public.pod_seasons;
  cleaned jsonb;
begin
  s := social_private.my_season(p_season, uid);
  perform social_private.check_season(p_name, p_starts_on, p_ends_on, p_max_nights);
  cleaned := social_private.clean_season_rules(p_rules);
  update public.pod_seasons
    set name = btrim(p_name), starts_on = p_starts_on, ends_on = p_ends_on, max_nights = p_max_nights, rules = cleaned
    where id = s.id;
end;
$$;

-- Whoever started a running season, or the pod's owner, ends it, keeping the final table and the
-- champion the app worked out. [p_ended_at]: when it ended (its last day's end, or now); games
-- after it no longer count.
create or replace function public.end_pod_season(p_season uuid, p_ended_at timestamptz, p_champion text, p_standings jsonb)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me_with_profile();
  s public.pod_seasons;
begin
  s := social_private.my_season(p_season, uid);
  if p_standings is not null and (jsonb_typeof(p_standings) <> 'array' or pg_column_size(p_standings) > 32768) then
    raise exception 'bad_season' using errcode = 'P0001';
  end if;
  update public.pod_seasons
    set ended_at = least(coalesce(p_ended_at, now()), now()),
        champion = nullif(left(btrim(coalesce(p_champion, '')), 200), ''),
        standings = p_standings
    where id = s.id;
end;
$$;

revoke execute on function social_private.clean_season_rules(jsonb) from public, anon, authenticated;
revoke execute on function social_private.check_season(text, date, date, integer) from public, anon, authenticated;
revoke execute on function social_private.my_season(uuid, uuid) from public, anon, authenticated;
revoke execute on function social_private.clean_pod_players(uuid, jsonb) from public, anon, authenticated;
revoke execute on function public.pod_seasons(uuid) from public, anon;
revoke execute on function public.create_pod_season(uuid, text, date, date, integer, jsonb) from public, anon;
revoke execute on function public.update_pod_season(uuid, text, date, date, integer, jsonb) from public, anon;
revoke execute on function public.end_pod_season(uuid, timestamptz, text, jsonb) from public, anon;
grant execute on function public.pod_seasons(uuid) to authenticated;
grant execute on function public.create_pod_season(uuid, text, date, date, integer, jsonb) to authenticated;
grant execute on function public.update_pod_season(uuid, text, date, date, integer, jsonb) to authenticated;
grant execute on function public.end_pod_season(uuid, timestamptz, text, jsonb) to authenticated;
