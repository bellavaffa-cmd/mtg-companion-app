-- Manabind — a pod's shared game log, for playgroup stats the whole group sees.
--
-- A pod (public.pods) is a named group of friends. Any member can record a game played in it:
-- who sat down (members by account, guests by name), what each played and how it ended. Every
-- member reads the same log, so the apps can show one table for the group — record per player,
-- per commander, nemeses — instead of each person's own view from their own decks.
--
-- Rows are reached only through the functions below; the table itself is closed to the API.
-- Recording is idempotent per (pod, client_id), so a retry after a dropped connection, or game
-- night posting the same result from two phones, stores one game.
--
-- Safe to re-run: every statement is idempotent.

create table if not exists public.pod_games (
  id uuid primary key default gen_random_uuid(),
  pod_id uuid not null references public.pods (id) on delete cascade,
  recorded_by uuid not null references public.profiles (user_id) on delete cascade,
  -- The recording app's own id for the game.
  client_id text not null check (char_length(client_id) between 1 and 64),
  played_at timestamptz not null,
  -- A deck game mode (COMMANDER, MODERN, LIMITED…), as the apps name them.
  format text not null default '' check (char_length(format) <= 20),
  turns integer check (turns is null or turns between 0 and 1000),
  minutes integer check (minutes is null or minutes between 0 and 10000),
  -- [{"userId": uuid|null, "name": text, "commander": text|null, "deck": text|null,
  --   "result": "WIN"|"LOSS"|"DRAW"}], 2 to 10 players. userId is set for pod members only.
  players jsonb not null check (jsonb_typeof(players) = 'array' and pg_column_size(players) <= 16384),
  created_at timestamptz not null default now(),
  unique (pod_id, client_id)
);
create index if not exists pod_games_pod_idx on public.pod_games (pod_id, played_at desc);
alter table public.pod_games enable row level security;
revoke all on public.pod_games from anon, authenticated;

-- Checks a players list and returns it cleaned: names trimmed, unknown keys dropped, and a userId
-- kept only when that account is in the pod (anyone else counts as a guest by name).
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
  pname text;
  presult text;
  puser uuid;
  wins integer := 0;
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
    end if;
    cleaned := cleaned || jsonb_build_array(jsonb_build_object(
      'userId', puser,
      'name', pname,
      'commander', nullif(left(btrim(coalesce(p ->> 'commander', '')), 120), ''),
      'deck', nullif(left(btrim(coalesce(p ->> 'deck', '')), 80), ''),
      'result', presult));
  end loop;
  if wins > 1 then
    raise exception 'bad_players' using errcode = 'P0001';
  end if;
  return cleaned;
end;
$$;

-- A pod member records a game. Recording the same client_id again updates that game.
create or replace function public.record_pod_game(
  p_pod uuid, p_client_id text, p_played_at timestamptz, p_format text,
  p_turns integer, p_minutes integer, p_players jsonb)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me_with_profile();
  cleaned jsonb;
  game uuid;
begin
  if not social_private.is_pod_member(p_pod, uid) then
    raise exception 'not_in_pod' using errcode = 'P0001';
  end if;
  cleaned := social_private.clean_pod_players(p_pod, p_players);
  if (select count(*) from public.pod_games where pod_id = p_pod) >= 5000
      and not exists (select 1 from public.pod_games where pod_id = p_pod and client_id = p_client_id) then
    raise exception 'too_many_games' using errcode = 'P0001';
  end if;
  insert into public.pod_games (pod_id, recorded_by, client_id, played_at, format, turns, minutes, players)
  values (p_pod, uid, p_client_id, least(coalesce(p_played_at, now()), now() + interval '1 day'),
          upper(coalesce(p_format, '')), p_turns, p_minutes, cleaned)
  on conflict (pod_id, client_id) do update
    set played_at = excluded.played_at, format = excluded.format, turns = excluded.turns,
        minutes = excluded.minutes, players = excluded.players
  returning id into game;
  return game;
end;
$$;

-- A pod's games, newest first, for its members.
create or replace function public.pod_games(p_pod uuid, p_limit integer default 1000)
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
      'id', g.id, 'clientId', g.client_id, 'recordedBy', g.recorded_by,
      'playedAt', (extract(epoch from g.played_at) * 1000)::bigint,
      'format', g.format, 'turns', g.turns, 'minutes', g.minutes, 'players', g.players)
      order by g.played_at desc)
    from (select * from public.pod_games where pod_id = p_pod
          order by played_at desc limit least(greatest(coalesce(p_limit, 1000), 1), 5000)) g
  ), '[]'::jsonb);
end;
$$;

-- Whoever recorded a game, or the pod's owner, removes it.
create or replace function public.delete_pod_game(p_game uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me();
begin
  delete from public.pod_games g
  where g.id = p_game
    and (g.recorded_by = uid or exists (select 1 from public.pods p where p.id = g.pod_id and p.owner = uid));
end;
$$;

revoke execute on function social_private.clean_pod_players(uuid, jsonb) from public, anon, authenticated;
revoke execute on function public.record_pod_game(uuid, text, timestamptz, text, integer, integer, jsonb) from public, anon;
revoke execute on function public.pod_games(uuid, integer) from public, anon;
revoke execute on function public.delete_pod_game(uuid) from public, anon;
grant execute on function public.record_pod_game(uuid, text, timestamptz, text, integer, integer, jsonb) to authenticated;
grant execute on function public.pod_games(uuid, integer) to authenticated;
grant execute on function public.delete_pod_game(uuid) to authenticated;
