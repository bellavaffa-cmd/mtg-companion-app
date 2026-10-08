-- Manabind — completed collection goals in friends' Activity.
--
-- NOT applied automatically. The owner reviews it and runs it by hand (Supabase SQL editor, or
-- `npx supabase db push`). Safe to run more than once: the table, column and index are "if not
-- exists", functions and the trigger "create or replace". Nothing here deletes or drops anything:
-- no DELETE or DROP statement at all, and no existing function replaced —
-- friends_activity, activity_feed, activity_prefs, set_activity_prefs and social_private.activity_pref
-- (20261006080000_activity_comments.sql) stay exactly as they are.
--
-- As everywhere else, the table has row-level security on and no policies: all access goes through
-- the functions below, which run as their owner and check the caller (auth.uid()) themselves —
-- friendship and blocks either way.
--
-- 1. public.goal_activity: one row per collection goal a person completed — the goal's name, its kind
--    (SET, PLAYSET, DECK, CUSTOM), how many cards it took and, optionally, a card to show (a Scryfall
--    card id; the feed makes the picture's address from it, so nobody can point friends' apps at an
--    address of their choosing). A goal is noted once (its id, per person), whichever device sees it
--    complete first; the apps call post_goal_completed when their goal watcher marks one complete.
--
-- 2. What friends' Activity shows of you: a new switch "Share completed goals" (Settings › Privacy),
--    on unless turned off, kept as public.activity_prefs.goals (a new column; the four switches
--    set_activity_prefs saves are untouched, and a row it makes gets the column's default, on).
--    goal_activity_pref() reads it, set_goal_activity_pref(p_on) saves it. While it's off a completion
--    isn't noted at all (post_goal_completed answers false), and the feed leaves out past ones too —
--    so turning it on later doesn't show goals completed while it was off. Only friends ever see these
--    (not pod-mates), never anyone blocked either way.
--
-- 3. friends_goal_activity(p_before, p_limit): friends' completed goals, newest first, in the shape
--    of friends_activity's items (kind 'goal_completed'), from the last 60 days. The apps read it
--    beside friends_activity and merge the two, newest first; both take the same p_before/p_limit, so
--    paging stays right. An app that doesn't know the kind never calls it, and friends_activity
--    itself is unchanged, so older apps see exactly what they saw before.
--
-- 4. Live: a new completion pings each friend's private "dm:<user id>" channel (event 'social',
--    {what: 'activity', id: <row id>}) through social_private.ping from
--    20261007000000_live_social_updates.sql (needed first), so an open Activity tab reloads. Older apps
--    ignore a 'what' they don't know. A ping never fails the note.
--
-- The apps check for this migration with goal_activity_version() and, until it's there, don't call
-- the rest and hide the switch: the web app's src/social/activity.ts, the Android app's
-- data/social/ActivityComments.kt.
--
-- Deleting an account (20261006040000_delete_account.sql) needs no change: the table cascades from
-- public.profiles, which cascades from auth.users.

-- ============================================================================================
-- Tables
-- ============================================================================================

create table if not exists public.goal_activity (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles (user_id) on delete cascade,
  -- The goal's id in the person's collection goals (the apps' CollectionGoal.id).
  goal_id text not null check (char_length(goal_id) between 1 and 100),
  name text not null check (char_length(name) between 1 and 200),
  kind text not null check (kind in ('SET', 'PLAYSET', 'DECK', 'CUSTOM')),
  cards integer not null check (cards between 0 and 100000),
  -- A Scryfall card id (a uuid), for the picture.
  cover_card text check (cover_card is null or cover_card ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'),
  created_at timestamptz not null default now(),
  unique (user_id, goal_id)
);
create index if not exists goal_activity_user_idx on public.goal_activity (user_id, created_at desc);
alter table public.goal_activity enable row level security;
revoke all on public.goal_activity from anon, authenticated;

-- "Share completed goals" (Settings › Privacy): on unless turned off.
alter table public.activity_prefs add column if not exists goals boolean not null default true;

-- ============================================================================================
-- Helpers (private)
-- ============================================================================================

-- Whether [u] lets friends' Activity show their completed goals: on unless they turned it off.
create or replace function social_private.goal_activity_on(u uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce((select p.goals from public.activity_prefs p where p.user_id = u), true);
$$;

-- A Scryfall card id's picture, as the apps show cards (cards.scryfall.io, the front face).
create or replace function social_private.scryfall_image(card text)
returns text
language sql
immutable
set search_path = ''
as $$
  select case when card ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    then 'https://cards.scryfall.io/normal/front/' || substr(card, 1, 1) || '/' || substr(card, 2, 1) || '/' || card || '.jpg' end;
$$;

-- ============================================================================================
-- API: is this migration there?
-- ============================================================================================

create or replace function public.goal_activity_version()
returns integer
language sql
immutable
set search_path = ''
as $$
  select 1;
$$;

-- ============================================================================================
-- API: "Share completed goals"
-- ============================================================================================

create or replace function public.goal_activity_pref()
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me();
begin
  return social_private.goal_activity_on(uid);
end;
$$;

-- Saves the switch. A row made here gets the other switches' defaults, as set_activity_prefs would.
create or replace function public.set_goal_activity_pref(p_on boolean)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me_with_profile();
begin
  if p_on is null then
    raise exception 'bad_prefs' using errcode = 'P0001';
  end if;
  insert into public.activity_prefs as a (user_id, goals, updated_at)
  values (uid, p_on, now())
  on conflict (user_id) do update set goals = excluded.goals, updated_at = now();
end;
$$;

-- ============================================================================================
-- API: noting a completed goal
-- ============================================================================================

-- The caller completed goal [p_goal_id] ([p_name], of [p_kind], [p_cards] cards; [p_cover]: a
-- Scryfall card id to show, or null). Noted once per goal — calling again changes nothing — and only
-- while "Share completed goals" is on. True when it was noted now. At most 30 a day per person.
create or replace function public.post_goal_completed(
  p_goal_id text, p_name text, p_kind text, p_cards integer, p_cover text default null)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me_with_profile();
  gid text := btrim(coalesce(p_goal_id, ''));
  nm text := btrim(coalesce(p_name, ''));
  cover text := lower(nullif(btrim(coalesce(p_cover, '')), ''));
  n integer;
begin
  if char_length(gid) not between 1 and 100 then
    raise exception 'bad_goal' using errcode = 'P0001';
  end if;
  if char_length(nm) = 0 then
    raise exception 'bad_goal' using errcode = 'P0001';
  end if;
  if p_kind is null or p_kind not in ('SET', 'PLAYSET', 'DECK', 'CUSTOM') then
    raise exception 'bad_goal' using errcode = 'P0001';
  end if;
  if p_cards is null or p_cards < 0 then
    raise exception 'bad_goal' using errcode = 'P0001';
  end if;
  if not social_private.goal_activity_on(uid) then
    return false;
  end if;
  if (select count(*) from public.goal_activity where user_id = uid and created_at > now() - interval '1 day') >= 30 then
    return false;
  end if;
  insert into public.goal_activity (user_id, goal_id, name, kind, cards, cover_card)
  values (uid, gid, left(nm, 200), p_kind, least(p_cards, 100000),
          case when social_private.scryfall_image(cover) is not null then cover end)
  on conflict (user_id, goal_id) do nothing;
  get diagnostics n = row_count;
  return n > 0;
end;
$$;

-- ============================================================================================
-- API: friends' completed goals, for the Activity feed
-- ============================================================================================

-- Friends' completed goals, newest first: [p_limit] (up to 50) before [p_before] (ms; null: now),
-- from the last 60 days. Only friends, only while they share completed goals, nobody blocked either
-- way. Items in friends_activity's shape:
--   {kind: 'goal_completed', actor (profile), at, item_id (the goal's id), name, goal_kind, count (cards), cover}
create or replace function public.friends_goal_activity(p_before bigint default null, p_limit integer default 30)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me();
  lim integer := least(greatest(coalesce(p_limit, 30), 1), 50);
  before_at timestamptz := case when p_before is null then now() + interval '1 minute' else to_timestamp(p_before / 1000.0) end;
  since_at timestamptz := now() - interval '60 days';
  result jsonb;
begin
  with
  friends as (
    select case when f.requester = uid then f.addressee else f.requester end as user_id
    from public.friendships f
    where (f.requester = uid or f.addressee = uid) and f.status = 'accepted'
      and not social_private.blocked_either(f.requester, f.addressee)
  ),
  goals as (
    select g.*
    from public.goal_activity g
    join friends f on f.user_id = g.user_id
    where g.created_at < before_at and g.created_at > since_at
      and social_private.goal_activity_on(g.user_id)
    order by g.created_at desc
    limit lim
  )
  select coalesce(jsonb_agg(jsonb_strip_nulls(jsonb_build_object(
      'kind', 'goal_completed',
      'actor', social_private.profile_json(x.user_id),
      'at', social_private.ms(x.created_at),
      'item_id', x.goal_id,
      'name', x.name,
      'goal_kind', x.kind,
      'count', x.cards,
      'cover', social_private.scryfall_image(x.cover_card)
    )) order by x.created_at desc), '[]'::jsonb)
  into result
  from goals x
  where exists (select 1 from public.profiles pr where pr.user_id = x.user_id);
  return result;
end;
$$;

-- ============================================================================================
-- Live: a new completion pings the person's friends
-- ============================================================================================

create or replace function social_private.on_goal_activity_ping()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  who uuid;
begin
  for who in
    select case when f.requester = new.user_id then f.addressee else f.requester end
    from public.friendships f
    where (f.requester = new.user_id or f.addressee = new.user_id) and f.status = 'accepted'
      and not social_private.blocked_either(f.requester, f.addressee)
  loop
    perform social_private.ping(who, 'activity', jsonb_build_object('id', new.id));
  end loop;
  return null;
exception when others then
  raise warning 'social ping not sent: %', sqlerrm;
  return null;
end;
$$;

create or replace trigger goal_activity_ping
  after insert on public.goal_activity
  for each row execute function social_private.on_goal_activity_ping();

-- ============================================================================================
-- Who may call what
-- ============================================================================================

revoke execute on all functions in schema social_private from public, anon, authenticated;

do $$
declare
  f text;
begin
  foreach f in array array[
    'goal_activity_version()', 'goal_activity_pref()', 'set_goal_activity_pref(boolean)',
    'post_goal_completed(text, text, text, integer, text)', 'friends_goal_activity(bigint, integer)'
  ] loop
    execute format('revoke execute on function public.%s from public, anon', f);
    execute format('grant execute on function public.%s to authenticated', f);
  end loop;
end;
$$;
