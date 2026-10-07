-- Manabind — game night invites with RSVP, and a group chat per pod.
--
-- NOT applied automatically. The owner reviews it and runs it by hand (Supabase SQL editor, or
-- `npx supabase db push`). Needs the migrations before it (social, push notifications, pod games,
-- social_more for blocking, pod seasons). Safe to run more than once: tables and indexes are
-- "if not exists", functions "create or replace", the one trigger dropped and made again. Nothing at
-- the top level deletes rows or drops a table; the only DELETEs are inside functions (a guest taken
-- off a night, a pod's oldest chat messages past 5,000) and "drop trigger if exists" before the
-- trigger is made again.
--
-- The apps check for this migration with game_nights_version() and say "Game night invites aren't
-- available yet" / "Pod chat isn't available yet" until then (Android: data/social/GameNightsApi.kt;
-- web: src/social/nights.ts).
--
-- As everywhere else, the tables have row-level security on and no policies: the functions below
-- run as their owner and check the caller (auth.uid()) themselves.
--
-- Game nights
--   A pod member plans a night: when (and the organiser's time zone, for the times written in
--   notifications), where (free text: "Priya's"), an optional note, and optionally a few friends
--   from outside the pod (guests). Everyone in the pod plus the guests is invited and answers
--   going / maybe / cant, with the deck they'll bring if they like. Invitees are told by a
--   notification when it's planned, when its time or place changes and when it's called off; the
--   organiser when someone answers. The day-before reminder is the apps' own (Android schedules it
--   from the list of nights). The organiser (or the pod's owner) changes or cancels it.
--
-- Pod chat
--   One conversation per pod, for its members only (guests aren't in it). Text messages (up to
--   2,000 characters, 30 a minute), shared things ("share": a game night, a deck or a card) and
--   posts Manabind makes itself: a new game night ("night"), a night moved or called off ("system")
--   and every pod game recorded ("league": "Priya won with Atraxa. Sam took first blood.", with the
--   running season's name when one runs). Messages from someone blocked either way are left out for
--   both. Live to each member's own "dm:<user id>" channel (the one direct messages use) as event
--   "pod_message"; game night changes go out there as "game_night". Read markers per member give
--   the unread counts. A pod keeps its newest 5,000 messages.
--
-- Deleting an account: every table here cascades from public.profiles (and pods), so
-- delete_my_account (20261006040000_delete_account.sql) removes the user's nights, answers, guest
-- places, messages and read markers with their profile.

-- ============================================================================================
-- Tables
-- ============================================================================================

create table if not exists public.game_nights (
  id uuid primary key default gen_random_uuid(),
  pod_id uuid not null references public.pods (id) on delete cascade,
  organiser uuid not null references public.profiles (user_id) on delete cascade,
  starts_at timestamptz not null,
  -- The organiser's time zone (IANA, "Europe/London"), for times written in notifications.
  tz text not null default 'UTC' check (char_length(tz) between 1 and 64),
  place text not null check (char_length(btrim(place)) between 1 and 80),
  note text check (note is null or char_length(note) <= 500),
  cancelled_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists game_nights_pod_idx on public.game_nights (pod_id, starts_at desc);
create index if not exists game_nights_organiser_idx on public.game_nights (organiser, created_at desc);
alter table public.game_nights enable row level security;
revoke all on public.game_nights from anon, authenticated;

-- Friends from outside the pod invited to one night.
create table if not exists public.game_night_guests (
  night_id uuid not null references public.game_nights (id) on delete cascade,
  user_id uuid not null references public.profiles (user_id) on delete cascade,
  added_at timestamptz not null default now(),
  primary key (night_id, user_id)
);
create index if not exists game_night_guests_user_idx on public.game_night_guests (user_id);
alter table public.game_night_guests enable row level security;
revoke all on public.game_night_guests from anon, authenticated;

create table if not exists public.game_night_rsvps (
  night_id uuid not null references public.game_nights (id) on delete cascade,
  user_id uuid not null references public.profiles (user_id) on delete cascade,
  answer text not null check (answer in ('going', 'maybe', 'cant')),
  -- The deck they'll bring, as they name it ("Krenko").
  deck text check (deck is null or char_length(deck) <= 80),
  updated_at timestamptz not null default now(),
  primary key (night_id, user_id)
);
create index if not exists game_night_rsvps_user_idx on public.game_night_rsvps (user_id);
alter table public.game_night_rsvps enable row level security;
revoke all on public.game_night_rsvps from anon, authenticated;

create table if not exists public.pod_messages (
  id bigint generated always as identity primary key,
  pod_id uuid not null references public.pods (id) on delete cascade,
  -- Null for what Manabind posts itself (league results, a night moved or called off).
  sender uuid references public.profiles (user_id) on delete cascade,
  kind text not null default 'text' check (kind in ('text', 'share', 'night', 'league', 'system')),
  body text not null default '' check (char_length(body) <= 2000),
  -- share / night: {"type": "night", "nightId", "startsAt" (ms), "place"} | {"type": "deck",
  -- "ownerId", "itemId", "name"} | {"type": "card", "name"}; league: {"gameId", "seasonId",
  -- "season"}; system: {"nightId"}.
  ref jsonb check (ref is null or (jsonb_typeof(ref) = 'object' and pg_column_size(ref) <= 4096)),
  created_at timestamptz not null default now(),
  check (sender is not null or kind in ('league', 'system'))
);
create index if not exists pod_messages_pod_idx on public.pod_messages (pod_id, id desc);
create index if not exists pod_messages_sender_idx on public.pod_messages (sender, created_at desc);
alter table public.pod_messages enable row level security;
revoke all on public.pod_messages from anon, authenticated;

-- The newest message each member has read in their pod's chat.
create table if not exists public.pod_reads (
  pod_id uuid not null references public.pods (id) on delete cascade,
  user_id uuid not null references public.profiles (user_id) on delete cascade,
  read_id bigint not null default 0,
  primary key (pod_id, user_id)
);
create index if not exists pod_reads_user_idx on public.pod_reads (user_id);
alter table public.pod_reads enable row level security;
revoke all on public.pod_reads from anon, authenticated;

-- ============================================================================================
-- Helpers (private)
-- ============================================================================================

-- Everyone invited to [p_night]: its pod's members and its guests.
create or replace function social_private.night_invitees(p_night uuid)
returns setof uuid
language sql
stable
security definer
set search_path = ''
as $$
  select m.user_id from public.game_nights n join public.pod_members m on m.pod_id = n.pod_id where n.id = p_night
  union
  select g.user_id from public.game_night_guests g where g.night_id = p_night;
$$;

create or replace function social_private.is_invited(p_night uuid, who uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (select 1 from social_private.night_invitees(p_night) i where i = who);
$$;

-- "Fri 10 Oct · 7pm" (or "· 7:30pm") in the night's own time zone, as the apps write it.
create or replace function social_private.night_when(p_at timestamptz, p_tz text)
returns text
language plpgsql
stable
set search_path = ''
as $$
declare
  local timestamp;
begin
  begin
    local := p_at at time zone coalesce(p_tz, 'UTC');
  exception when others then
    local := p_at at time zone 'UTC';
  end;
  return to_char(local, 'Dy FMDD Mon') || ' · '
    || case when extract(minute from local) = 0 then lower(to_char(local, 'FMHH12am')) else lower(to_char(local, 'FMHH12:MIam')) end;
end;
$$;

-- The season running in [p_pod] on [p_day] — {id, name} — or null (also before pod seasons exist).
create or replace function social_private.season_on(p_pod uuid, p_day date)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  found jsonb;
begin
  if to_regclass('public.pod_seasons') is null then
    return null;
  end if;
  execute 'select jsonb_build_object(''id'', s.id, ''name'', s.name) from public.pod_seasons s
           where s.pod_id = $1 and s.ended_at is null and s.starts_on <= $2 and (s.ends_on is null or s.ends_on >= $2)
           order by s.starts_on desc limit 1'
    into found using p_pod, p_day;
  return found;
end;
$$;

-- A night as the apps read it, for [viewer] (people blocked either way left out of the invitees).
create or replace function social_private.night_json(n public.game_nights, viewer uuid)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select jsonb_build_object(
    'id', n.id,
    'podId', n.pod_id,
    'podName', (select p.name from public.pods p where p.id = n.pod_id),
    'organiser', social_private.profile_json(n.organiser),
    'startsAt', social_private.ms(n.starts_at),
    'tz', n.tz,
    'place', n.place,
    'note', n.note,
    'cancelled', n.cancelled_at is not null,
    'createdAt', social_private.ms(n.created_at),
    'updatedAt', social_private.ms(n.updated_at),
    'season', social_private.season_on(n.pod_id, (n.starts_at at time zone n.tz)::date),
    'invitees', coalesce((
      select jsonb_agg(jsonb_build_object(
          'user', social_private.profile_json(i.user_id),
          'member', social_private.is_pod_member(n.pod_id, i.user_id),
          'answer', r.answer,
          'deck', r.deck,
          'answeredAt', social_private.ms(r.updated_at))
        order by (i.user_id = n.organiser) desc, pr.display_name)
      from social_private.night_invitees(n.id) as i(user_id)
      join public.profiles pr on pr.user_id = i.user_id
      left join public.game_night_rsvps r on r.night_id = n.id and r.user_id = i.user_id
      where i.user_id = viewer or not social_private.blocked_either(viewer, i.user_id)
    ), '[]'::jsonb)
  );
$$;

-- A chat message as the apps read it.
create or replace function social_private.pod_message_json(m public.pod_messages)
returns jsonb
language sql
stable
set search_path = ''
as $$
  select jsonb_build_object(
    'id', m.id, 'podId', m.pod_id, 'sender', m.sender, 'kind', m.kind, 'body', m.body,
    'ref', m.ref, 'createdAt', social_private.ms(m.created_at));
$$;

-- Live delivery of [p_payload] as [p_event] to every member of [p_pod] (but those blocked either
-- way by [p_from]). A nicety: never fails the caller.
create or replace function social_private.pod_broadcast(p_pod uuid, p_from uuid, p_event text, p_payload jsonb)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  who uuid;
begin
  for who in select m.user_id from public.pod_members m where m.pod_id = p_pod loop
    if p_from is null or who = p_from or not social_private.blocked_either(p_from, who) then
      begin
        perform realtime.send(p_payload, p_event, 'dm:' || who, true);
      exception when others then
        raise warning 'not broadcast: %', sqlerrm;
      end;
    end if;
  end loop;
end;
$$;

-- Adds a message to [p_pod]'s chat (the sender's own read marker moves past it), sends it live and
-- keeps the pod's newest 5,000. Answers the message.
create or replace function social_private.post_to_pod(p_pod uuid, p_sender uuid, p_kind text, p_body text, p_ref jsonb)
returns public.pod_messages
language plpgsql
security definer
set search_path = ''
as $$
declare
  msg public.pod_messages;
begin
  insert into public.pod_messages (pod_id, sender, kind, body, ref)
  values (p_pod, p_sender, p_kind, left(coalesce(p_body, ''), 2000), p_ref)
  returning * into msg;
  if p_sender is not null then
    insert into public.pod_reads (pod_id, user_id, read_id) values (p_pod, p_sender, msg.id)
    on conflict (pod_id, user_id) do update set read_id = greatest(public.pod_reads.read_id, excluded.read_id);
  end if;
  perform social_private.pod_broadcast(p_pod, p_sender, 'pod_message', social_private.pod_message_json(msg));
  if msg.id % 100 = 0 then
    delete from public.pod_messages
    where pod_id = p_pod and id < (select x.id from public.pod_messages x where x.pod_id = p_pod order by x.id desc offset 4999 limit 1);
  end if;
  return msg;
end;
$$;

-- Tells everyone invited to [n] (but [p_except], and anyone blocked either way by the organiser).
create or replace function social_private.notify_invitees(n public.game_nights, p_except uuid, p_title text, p_body text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  who uuid;
begin
  for who in select i from social_private.night_invitees(n.id) i loop
    if who is distinct from p_except and (who = n.organiser or not social_private.blocked_either(n.organiser, who)) then
      perform social_private.notify(who, 'friends', p_title, p_body, 'night:' || n.id, 'night-' || n.id);
    end if;
  end loop;
end;
$$;

-- A night changed: open screens reload it.
create or replace function social_private.night_changed(n public.game_nights)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  who uuid;
begin
  for who in select i from social_private.night_invitees(n.id) i loop
    begin
      perform realtime.send(jsonb_build_object('nightId', n.id, 'podId', n.pod_id), 'game_night', 'dm:' || who, true);
    exception when others then
      raise warning 'not broadcast: %', sqlerrm;
    end;
  end loop;
end;
$$;

-- ============================================================================================
-- API: is this migration there?
-- ============================================================================================

create or replace function public.game_nights_version()
returns integer
language sql
immutable
set search_path = ''
as $$
  select 1;
$$;

-- ============================================================================================
-- API: game nights
-- ============================================================================================

-- Plans a night in [p_pod] ([p_night] null), or changes one the caller organises (or whose pod they
-- own). [p_guests]: friends from outside the pod to invite as well (up to 20; replaces the list when
-- changing). Answers the night.
create or replace function public.save_game_night(
  p_night uuid, p_pod uuid, p_starts_at timestamptz, p_tz text, p_place text, p_note text, p_guests uuid[] default '{}')
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me_with_profile();
  n public.game_nights;
  old public.game_nights;
  v_place text := btrim(coalesce(p_place, ''));
  v_note text := nullif(btrim(coalesce(p_note, '')), '');
  v_tz text := coalesce(nullif(btrim(coalesce(p_tz, '')), ''), 'UTC');
  g uuid;
  pod_name text;
  when_text text;
begin
  if not exists (select 1 from pg_catalog.pg_timezone_names z where z.name = v_tz) then
    v_tz := 'UTC';
  end if;
  if p_starts_at is null or p_starts_at > now() + interval '366 days'
      or char_length(v_place) not between 1 and 80 or char_length(coalesce(v_note, '')) > 500 then
    raise exception 'bad_night' using errcode = 'P0001';
  end if;
  if coalesce(array_length(p_guests, 1), 0) > 20 then
    raise exception 'too_many_guests' using errcode = 'P0001';
  end if;

  if p_night is null then
    if not social_private.is_pod_member(p_pod, uid) then
      raise exception 'not_in_pod' using errcode = 'P0001';
    end if;
    if p_starts_at < now() - interval '1 hour' then
      raise exception 'bad_night' using errcode = 'P0001';
    end if;
    if (select count(*) from public.game_nights where pod_id = p_pod and cancelled_at is null and starts_at > now()) >= 10 then
      raise exception 'too_many_nights' using errcode = 'P0001';
    end if;
    if (select count(*) from public.game_nights where organiser = uid and created_at > now() - interval '1 day') >= 10 then
      raise exception 'too_many_nights' using errcode = 'P0001';
    end if;
    insert into public.game_nights (pod_id, organiser, starts_at, tz, place, note)
    values (p_pod, uid, p_starts_at, v_tz, v_place, v_note)
    returning * into n;
    insert into public.game_night_rsvps (night_id, user_id, answer) values (n.id, uid, 'going');
  else
    select * into old from public.game_nights where id = p_night;
    if old.id is null or not social_private.is_invited(old.id, uid) then
      raise exception 'no_such_night' using errcode = 'P0001';
    end if;
    if old.organiser <> uid and not exists (select 1 from public.pods p where p.id = old.pod_id and p.owner = uid) then
      raise exception 'not_organiser' using errcode = 'P0001';
    end if;
    if old.cancelled_at is not null then
      raise exception 'night_cancelled' using errcode = 'P0001';
    end if;
    update public.game_nights
      set starts_at = p_starts_at, tz = v_tz, place = v_place, note = v_note, updated_at = now()
      where id = old.id
      returning * into n;
  end if;

  -- Guests: the organiser's friends from outside the pod, nobody blocked either way.
  delete from public.game_night_guests where night_id = n.id and not (user_id = any(coalesce(p_guests, '{}')));
  delete from public.game_night_rsvps r
    where r.night_id = n.id and not social_private.is_pod_member(n.pod_id, r.user_id)
      and not exists (select 1 from public.game_night_guests x where x.night_id = n.id and x.user_id = r.user_id);
  foreach g in array coalesce(p_guests, '{}') loop
    if g is not null and g <> n.organiser and not social_private.is_pod_member(n.pod_id, g) then
      if not social_private.are_friends(uid, g) or social_private.blocked_either(uid, g) then
        raise exception 'not_a_friend' using errcode = 'P0001';
      end if;
      insert into public.game_night_guests (night_id, user_id) values (n.id, g) on conflict do nothing;
    end if;
  end loop;

  select p.name into pod_name from public.pods p where p.id = n.pod_id;
  when_text := social_private.night_when(n.starts_at, n.tz);
  if p_night is null then
    perform social_private.post_to_pod(n.pod_id, uid, 'night', '',
      jsonb_build_object('type', 'night', 'nightId', n.id, 'startsAt', social_private.ms(n.starts_at), 'place', n.place));
    perform social_private.notify_invitees(n, uid,
      social_private.display_name(uid) || ' invited you to game night',
      when_text || ' at ' || n.place || ' · ' || pod_name || '. Going?');
  elsif old.starts_at is distinct from n.starts_at or old.place is distinct from n.place then
    perform social_private.post_to_pod(n.pod_id, null, 'system',
      'Game night moved to ' || when_text || ' at ' || n.place, jsonb_build_object('nightId', n.id));
    perform social_private.notify_invitees(n, uid, 'Game night changed', 'Now ' || when_text || ' at ' || n.place || ' · ' || pod_name);
  end if;
  perform social_private.night_changed(n);
  return social_private.night_json(n, uid);
end;
$$;

-- The organiser (or the pod's owner) calls a night off. Everyone invited is told.
create or replace function public.cancel_game_night(p_night uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me_with_profile();
  n public.game_nights;
  when_text text;
begin
  select * into n from public.game_nights where id = p_night;
  if n.id is null or not social_private.is_invited(n.id, uid) then
    raise exception 'no_such_night' using errcode = 'P0001';
  end if;
  if n.organiser <> uid and not exists (select 1 from public.pods p where p.id = n.pod_id and p.owner = uid) then
    raise exception 'not_organiser' using errcode = 'P0001';
  end if;
  if n.cancelled_at is not null then
    return;
  end if;
  update public.game_nights set cancelled_at = now(), updated_at = now() where id = n.id returning * into n;
  when_text := social_private.night_when(n.starts_at, n.tz);
  perform social_private.post_to_pod(n.pod_id, null, 'system', 'Game night on ' || when_text || ' is off', jsonb_build_object('nightId', n.id));
  perform social_private.notify_invitees(n, uid, 'Game night called off', when_text || ' at ' || n.place || ' is off.');
  perform social_private.night_changed(n);
end;
$$;

-- The caller's answer to a night they're invited to: 'going', 'maybe' or 'cant', and the deck
-- they'll bring (going or maybe; up to 80 characters). The organiser is told. Answers the night.
create or replace function public.rsvp_game_night(p_night uuid, p_answer text, p_deck text default null)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me_with_profile();
  n public.game_nights;
  answer text := lower(btrim(coalesce(p_answer, '')));
  deck text := nullif(left(btrim(coalesce(p_deck, '')), 80), '');
  before text;
begin
  select * into n from public.game_nights where id = p_night;
  if n.id is null or not social_private.is_invited(n.id, uid) then
    raise exception 'no_such_night' using errcode = 'P0001';
  end if;
  if answer not in ('going', 'maybe', 'cant') then
    raise exception 'bad_answer' using errcode = 'P0001';
  end if;
  if n.cancelled_at is not null then
    raise exception 'night_cancelled' using errcode = 'P0001';
  end if;
  if n.starts_at < now() - interval '12 hours' then
    raise exception 'night_over' using errcode = 'P0001';
  end if;
  if answer = 'cant' then deck := null; end if;
  select r.answer into before from public.game_night_rsvps r where r.night_id = n.id and r.user_id = uid;
  insert into public.game_night_rsvps as r (night_id, user_id, answer, deck)
  values (n.id, uid, answer, deck)
  on conflict (night_id, user_id) do update set answer = excluded.answer, deck = excluded.deck, updated_at = now();
  if uid <> n.organiser and before is distinct from answer and not social_private.blocked_either(uid, n.organiser) then
    perform social_private.notify(n.organiser, 'friends',
      social_private.display_name(uid) || case answer when 'going' then ' is going' when 'maybe' then ' might come' else ' can''t make it' end,
      'Game night · ' || social_private.night_when(n.starts_at, n.tz) || coalesce(' · ' || deck, ''),
      'night:' || n.id, 'night-' || n.id || '-' || uid);
  end if;
  perform social_private.night_changed(n);
  return social_private.night_json(n, uid);
end;
$$;

-- The nights the caller is invited to that haven't been and gone (from 12 hours ago on), soonest
-- first, up to 50 — in [p_pod] only when given. Called-off ones stay until their day has passed.
create or replace function public.game_nights(p_pod uuid default null)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me();
begin
  return coalesce((
    select jsonb_agg(social_private.night_json(n, uid) order by n.starts_at)
    from (
      select x.* from public.game_nights x
      where x.starts_at > now() - interval '12 hours'
        and (p_pod is null or x.pod_id = p_pod)
        and (social_private.is_pod_member(x.pod_id, uid)
             or exists (select 1 from public.game_night_guests g where g.night_id = x.id and g.user_id = uid))
        and (x.organiser = uid or not social_private.blocked_either(uid, x.organiser))
      order by x.starts_at
      limit 50
    ) n
  ), '[]'::jsonb);
end;
$$;

-- One night the caller is invited to (past ones too), or null.
create or replace function public.game_night(p_night uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me();
  n public.game_nights;
begin
  select * into n from public.game_nights where id = p_night;
  if n.id is null or not social_private.is_invited(n.id, uid) then
    return null;
  end if;
  return social_private.night_json(n, uid);
end;
$$;

-- ============================================================================================
-- API: pod chat
-- ============================================================================================

-- Sends to [p_pod]'s chat: text ([p_kind] 'text', 1–2,000 characters) or something shared
-- ('share', [p_ref] {"type": "night", "nightId"} | {"type": "deck", "itemId", "name"} |
-- {"type": "card", "name"}, with [p_body] as an optional caption). 30 messages a minute. Members are
-- told by a notification (one while earlier ones from the last 10 minutes wait unread). Answers the
-- message.
create or replace function public.send_pod_message(p_pod uuid, p_body text, p_kind text default 'text', p_ref jsonb default null)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me_with_profile();
  body text := btrim(coalesce(p_body, ''));
  kind text := coalesce(p_kind, 'text');
  ref jsonb;
  night public.game_nights;
  msg public.pod_messages;
  pod_name text;
  who uuid;
  preview text;
begin
  if not social_private.is_pod_member(p_pod, uid) then
    raise exception 'not_in_pod' using errcode = 'P0001';
  end if;
  if char_length(body) > 2000 then
    raise exception 'dm_too_long' using errcode = 'P0001';
  end if;
  if kind = 'text' then
    if char_length(body) = 0 then
      raise exception 'empty_message' using errcode = 'P0001';
    end if;
  elsif kind = 'share' then
    if p_ref is null or jsonb_typeof(p_ref) <> 'object' then
      raise exception 'bad_share' using errcode = 'P0001';
    end if;
    case p_ref ->> 'type'
      when 'night' then
        if coalesce(p_ref ->> 'nightId', '') !~ '^[0-9a-f-]{36}$' then
          raise exception 'bad_share' using errcode = 'P0001';
        end if;
        select * into night from public.game_nights where id = (p_ref ->> 'nightId')::uuid and pod_id = p_pod;
        if night.id is null then
          raise exception 'bad_share' using errcode = 'P0001';
        end if;
        ref := jsonb_build_object('type', 'night', 'nightId', night.id, 'startsAt', social_private.ms(night.starts_at), 'place', night.place);
      when 'deck' then
        if char_length(coalesce(p_ref ->> 'itemId', '')) not between 1 and 100 or char_length(btrim(coalesce(p_ref ->> 'name', ''))) = 0 then
          raise exception 'bad_share' using errcode = 'P0001';
        end if;
        ref := jsonb_build_object('type', 'deck', 'ownerId', uid, 'itemId', p_ref ->> 'itemId', 'name', left(btrim(p_ref ->> 'name'), 80));
      when 'card' then
        if char_length(btrim(coalesce(p_ref ->> 'name', ''))) not between 1 and 150 then
          raise exception 'bad_share' using errcode = 'P0001';
        end if;
        ref := jsonb_build_object('type', 'card', 'name', btrim(p_ref ->> 'name'));
      else
        raise exception 'bad_share' using errcode = 'P0001';
    end case;
  else
    raise exception 'bad_share' using errcode = 'P0001';
  end if;
  if (select count(*) from public.pod_messages where sender = uid and created_at > now() - interval '1 minute') >= 30 then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;

  msg := social_private.post_to_pod(p_pod, uid, kind, body, ref);

  select p.name into pod_name from public.pods p where p.id = p_pod;
  preview := case
    when kind = 'text' then left(body, 140)
    when ref ->> 'type' = 'night' then 'Shared a game night'
    when ref ->> 'type' = 'deck' then 'Shared a deck: ' || (ref ->> 'name')
    else 'Shared a card: ' || (ref ->> 'name') end;
  for who in select m.user_id from public.pod_members m where m.pod_id = p_pod and m.user_id <> uid loop
    if social_private.blocked_either(uid, who) then continue; end if;
    -- Earlier messages from the last 10 minutes still unread: they've been told already.
    if exists (
      select 1 from public.pod_messages x
      where x.pod_id = p_pod and x.id < msg.id and x.created_at > now() - interval '10 minutes'
        and x.sender is not null and x.sender <> who
        and x.id > coalesce((select r.read_id from public.pod_reads r where r.pod_id = p_pod and r.user_id = who), 0)
    ) then continue; end if;
    perform social_private.notify(who, 'friends', social_private.display_name(uid) || ' · ' || pod_name, preview, 'pod:' || p_pod, 'pod-' || p_pod);
  end loop;
  return social_private.pod_message_json(msg);
end;
$$;

-- One page of [p_pod]'s chat, oldest first: the [p_limit] (up to 100) messages before [p_before]
-- (null: the newest). Messages from anyone blocked either way are left out.
create or replace function public.pod_messages(p_pod uuid, p_before bigint default null, p_limit integer default 50)
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
    select jsonb_agg(social_private.pod_message_json(m) order by m.id)
    from (
      select x.* from public.pod_messages x
      where x.pod_id = p_pod and (p_before is null or x.id < p_before)
        and (x.sender is null or x.sender = uid or not social_private.blocked_either(uid, x.sender))
      order by x.id desc
      limit least(greatest(coalesce(p_limit, 50), 1), 100)
    ) m
  ), '[]'::jsonb);
end;
$$;

-- The caller has read everything in [p_pod]'s chat.
create or replace function public.mark_pod_read(p_pod uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me();
  newest bigint;
begin
  if not social_private.is_pod_member(p_pod, uid) then
    return;
  end if;
  select max(id) into newest from public.pod_messages where pod_id = p_pod;
  insert into public.pod_reads (pod_id, user_id, read_id) values (p_pod, uid, coalesce(newest, 0))
  on conflict (pod_id, user_id) do update set read_id = greatest(public.pod_reads.read_id, excluded.read_id);
end;
$$;

-- How many of [p_pod]'s messages wait unread for [who]: others' (and Manabind's), since they joined,
-- nobody blocked.
create or replace function social_private.pod_unread(p_pod uuid, who uuid)
returns integer
language sql
stable
security definer
set search_path = ''
as $$
  select count(*)::int from public.pod_messages x
  where x.pod_id = p_pod
    and x.id > coalesce((select r.read_id from public.pod_reads r where r.pod_id = p_pod and r.user_id = who), 0)
    and x.created_at >= coalesce((select m.added_at from public.pod_members m where m.pod_id = p_pod and m.user_id = who), now())
    and (x.sender is null or (x.sender <> who and not social_private.blocked_either(who, x.sender)));
$$;

-- The caller's pods' chats, newest first: [{podId, name, members, last, unread}], last being
-- {id, sender, senderName, kind, body, ref, createdAt} or null.
create or replace function public.pod_chats()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me();
begin
  return coalesce((
    select jsonb_agg(c.row order by c.at desc nulls last, c.name)
    from (
      select jsonb_build_object(
          'podId', p.id,
          'name', p.name,
          'members', (select count(*) from public.pod_members x where x.pod_id = p.id),
          'last', (
            select social_private.pod_message_json(l) || jsonb_build_object('senderName', case when l.sender is null then null else social_private.display_name(l.sender) end)
            from public.pod_messages l
            where l.pod_id = p.id and (l.sender is null or l.sender = uid or not social_private.blocked_either(uid, l.sender))
            order by l.id desc limit 1),
          'unread', social_private.pod_unread(p.id, uid)
        ) as row,
        (select max(l.created_at) from public.pod_messages l where l.pod_id = p.id) as at,
        p.name
      from public.pods p
      join public.pod_members m on m.pod_id = p.id and m.user_id = uid
    ) c
  ), '[]'::jsonb);
end;
$$;

-- How many pod chat messages wait unread, for the badge.
create or replace function public.unread_pod_messages()
returns integer
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce(sum(social_private.pod_unread(m.pod_id, auth.uid())), 0)::int
  from public.pod_members m where m.user_id = auth.uid();
$$;

-- ============================================================================================
-- League results in the chat
-- ============================================================================================

-- A pod game recorded (not one recorded again): "Priya won with Atraxa. Sam took first blood." in
-- the pod's chat, with the season it counts for. Never fails the game itself.
create or replace function social_private.on_pod_game()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  winner jsonb;
  blood jsonb;
  season jsonb;
  body text;
  nm text;
begin
  select p into winner from jsonb_array_elements(new.players) p where p ->> 'result' = 'WIN' limit 1;
  select p into blood from jsonb_array_elements(new.players) p where p -> 'firstBlood' = 'true'::jsonb limit 1;
  if winner is not null then
    nm := case when coalesce(winner ->> 'userId', '') ~ '^[0-9a-f-]{36}$' then social_private.display_name((winner ->> 'userId')::uuid) else winner ->> 'name' end;
    body := nm || ' won' || coalesce(' with ' || coalesce(winner ->> 'commander', winner ->> 'deck'), '') || '.';
  elsif exists (select 1 from jsonb_array_elements(new.players) p where p ->> 'result' = 'DRAW') then
    body := 'The game was a draw.';
  else
    body := 'A game was recorded.';
  end if;
  if blood is not null then
    nm := case when coalesce(blood ->> 'userId', '') ~ '^[0-9a-f-]{36}$' then social_private.display_name((blood ->> 'userId')::uuid) else blood ->> 'name' end;
    body := body || ' ' || nm || ' took first blood.';
  end if;
  season := social_private.season_on(new.pod_id, new.played_at::date);
  perform social_private.post_to_pod(new.pod_id, null, 'league', body,
    jsonb_build_object('gameId', new.id, 'seasonId', season ->> 'id', 'season', season ->> 'name'));
  return null;
exception when others then
  raise warning 'game not posted to the chat: %', sqlerrm;
  return null;
end;
$$;

drop trigger if exists pod_games_chat on public.pod_games;
create trigger pod_games_chat
  after insert on public.pod_games
  for each row execute function social_private.on_pod_game();

-- ============================================================================================
-- Who may call what
-- ============================================================================================

revoke execute on all functions in schema social_private from public, anon, authenticated;

do $$
declare
  f text;
begin
  foreach f in array array[
    'game_nights_version()',
    'save_game_night(uuid, uuid, timestamptz, text, text, text, uuid[])',
    'cancel_game_night(uuid)',
    'rsvp_game_night(uuid, text, text)',
    'game_nights(uuid)',
    'game_night(uuid)',
    'send_pod_message(uuid, text, text, jsonb)',
    'pod_messages(uuid, bigint, integer)',
    'mark_pod_read(uuid)',
    'pod_chats()',
    'unread_pod_messages()'
  ] loop
    execute format('revoke execute on function public.%s from public, anon', f);
    execute format('grant execute on function public.%s to authenticated', f);
  end loop;
end;
$$;
