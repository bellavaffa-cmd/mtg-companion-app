-- Manabind — more social: blocking and reporting, direct messages, trade reputation, a friends'
-- activity feed, and cards marked for trade.
--
-- Safe to run more than once: tables and indexes are "if not exists", functions "create or replace",
-- triggers and policies dropped and made again.
--
-- As in 20260919000000_social.sql, every table here has row-level security on and no policies, so
-- nothing reads or writes it directly. All access goes through the functions below, which run as
-- their owner and check the caller (auth.uid()) themselves — including whether either side has
-- blocked the other.
--
-- The apps check for this migration with social_more_version() and hide what needs it until then
-- (web: src/social/more.ts; Android: data/social/SocialMore.kt).
--
-- What it changes in functions that already exist (bodies kept as they were, plus the block check):
--   * social_private.can_view_share / can_view_item — nothing is visible across a block, whichever
--     way it goes (pods included). get_shared_item, get_shared_collection, search_shared_cards,
--     wishlist_matches, who_has_cards and social_overview's shared lists all go through these.
--   * public.request_friend — someone who blocked the caller looks like no such user; someone the
--     caller blocked answers 'blocked'.
--   * public.get_shared_by_link — a signed-in viewer blocked either way gets nothing.
--   * social_private.clean_pod_players — a player blocked either way by whoever records the game is
--     kept as a guest by name, not tied to their account (so a blocked person can't add you to games).
-- Blocking also ends the friendship (so trades, shares and messages stop: they all need one), cancels
-- open trades between the two, and takes the blocked person off the blocker's one-by-one shares.
-- Pods stay as they are, and a pod's game log stays readable to everyone in it.

-- ============================================================================================
-- Tables
-- ============================================================================================

create table if not exists public.blocks (
  blocker uuid not null references public.profiles (user_id) on delete cascade,
  blocked uuid not null references public.profiles (user_id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (blocker, blocked),
  check (blocker <> blocked)
);
create index if not exists blocks_blocked_idx on public.blocks (blocked);
alter table public.blocks enable row level security;
revoke all on public.blocks from anon, authenticated;

-- Reports for the owner to read in the dashboard (status: new → seen → actioned / dismissed).
create table if not exists public.reports (
  id uuid primary key default gen_random_uuid(),
  reporter uuid not null references public.profiles (user_id) on delete cascade,
  reported uuid not null references public.profiles (user_id) on delete cascade,
  reason text not null check (reason in ('spam', 'abuse', 'scam', 'inappropriate', 'other')),
  note text check (char_length(note) <= 1000),
  -- What it's about, when it's about one thing: 'profile', 'deck', 'collection', 'trade' or 'message', and its id.
  item_kind text check (item_kind in ('profile', 'deck', 'collection', 'trade', 'message')),
  item_id text check (char_length(item_id) <= 100),
  status text not null default 'new' check (status in ('new', 'seen', 'actioned', 'dismissed')),
  created_at timestamptz not null default now(),
  check (reporter <> reported)
);
create index if not exists reports_reporter_idx on public.reports (reporter, created_at desc);
create index if not exists reports_status_idx on public.reports (status, created_at);
alter table public.reports enable row level security;
revoke all on public.reports from anon, authenticated;

-- One conversation per pair of people (user_a < user_b). read_a / read_b: the newest message id each
-- has read.
create table if not exists public.conversations (
  id uuid primary key default gen_random_uuid(),
  user_a uuid not null references public.profiles (user_id) on delete cascade,
  user_b uuid not null references public.profiles (user_id) on delete cascade,
  created_at timestamptz not null default now(),
  last_message_at timestamptz not null default now(),
  read_a bigint not null default 0,
  read_b bigint not null default 0,
  unique (user_a, user_b),
  check (user_a < user_b)
);
create index if not exists conversations_a_idx on public.conversations (user_a, last_message_at desc);
create index if not exists conversations_b_idx on public.conversations (user_b, last_message_at desc);
alter table public.conversations enable row level security;
revoke all on public.conversations from anon, authenticated;

create table if not exists public.messages (
  id bigint generated always as identity primary key,
  conversation_id uuid not null references public.conversations (id) on delete cascade,
  sender uuid not null references public.profiles (user_id) on delete cascade,
  body text not null check (char_length(body) between 1 and 2000),
  created_at timestamptz not null default now()
);
create index if not exists messages_conversation_idx on public.messages (conversation_id, id desc);
create index if not exists messages_sender_idx on public.messages (sender, created_at desc);
alter table public.messages enable row level security;
revoke all on public.messages from anon, authenticated;

-- A thumbs up or down after a trade: one per side per trade.
create table if not exists public.trade_ratings (
  trade_id uuid not null references public.trades (id) on delete cascade,
  rater uuid not null references public.profiles (user_id) on delete cascade,
  rated uuid not null references public.profiles (user_id) on delete cascade,
  positive boolean not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (trade_id, rater),
  check (rater <> rated)
);
create index if not exists trade_ratings_rated_idx on public.trade_ratings (rated);
alter table public.trade_ratings enable row level security;
revoke all on public.trade_ratings from anon, authenticated;

-- Things for the activity feed that the other tables can't tell: a deck or binder newly shared
-- (item_id null: all decks / the whole collection), and cards newly marked for trade. Kept 90 days.
create table if not exists public.social_events (
  id bigint generated always as identity primary key,
  actor uuid not null references public.profiles (user_id) on delete cascade,
  kind text not null check (kind in ('shared', 'for_trade')),
  item_kind text check (item_kind in ('deck', 'collection')),
  item_id text,
  -- for_trade: {"count": n, "cards": [{"name", "imageUrl"}]} (up to 6 cards).
  detail jsonb not null default '{}',
  created_at timestamptz not null default now()
);
create index if not exists social_events_actor_idx on public.social_events (actor, created_at desc);
alter table public.social_events enable row level security;
revoke all on public.social_events from anon, authenticated;

-- ============================================================================================
-- Helpers (private)
-- ============================================================================================

-- Whether either of [a] and [b] has blocked the other.
create or replace function social_private.blocked_either(a uuid, b uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.blocks k
    where (k.blocker = a and k.blocked = b) or (k.blocker = b and k.blocked = a)
  );
$$;

-- How many of a binder entry's copies are marked for trade ("forTrade"), never more than it holds.
create or replace function social_private.for_trade_count(e jsonb)
returns integer
language sql
immutable
set search_path = ''
as $$
  select case when jsonb_typeof(e -> 'forTrade') = 'number' then
    greatest(0, least(
      floor(greatest(least((e ->> 'forTrade')::numeric, 9999), 0))::int,
      (case when jsonb_typeof(e -> 'quantity') = 'number' then floor(greatest(least((e ->> 'quantity')::numeric, 9999), 0))::int else 0 end)
      + (case when jsonb_typeof(e -> 'foilQuantity') = 'number' then floor(greatest(least((e ->> 'foilQuantity')::numeric, 9999), 0))::int else 0 end)
    ))
  else 0 end;
$$;

create or replace function social_private.ms(t timestamptz)
returns bigint
language sql
stable
set search_path = ''
as $$
  select (extract(epoch from t) * 1000)::bigint;
$$;

-- As in 20260920000000_share_with_friends.sql, and nothing across a block.
create or replace function social_private.can_view_share(s public.library_shares, viewer uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select s.owner = viewer
    or (not social_private.blocked_either(s.owner, viewer) and (
      ((s.all_friends or viewer = any(s.friend_ids)) and social_private.are_friends(s.owner, viewer))
      or exists (
        select 1 from unnest(s.pod_ids) as p(id)
        where social_private.is_pod_member(p.id, viewer) and social_private.is_pod_member(p.id, s.owner)
      )
    ));
$$;

-- As in 20260920000000_share_with_friends.sql, and nothing across a block.
create or replace function social_private.can_view_item(p_owner uuid, p_kind text, p_item_id text, p_viewer uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select p_owner = p_viewer
    or (not social_private.blocked_either(p_owner, p_viewer) and (
      social_private.shares_all_with(p_owner, p_kind, p_viewer)
      or exists (
        select 1 from public.library_shares s
        where s.owner = p_owner and s.kind = p_kind and s.item_id = p_item_id and social_private.can_view_share(s, p_viewer)
      )
    ));
$$;

-- As in 20261005000000_pod_games.sql, and a player blocked either way by whoever records the game
-- stays a guest by name.
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
      if puser is not null and puser <> auth.uid() and social_private.blocked_either(auth.uid(), puser) then puser := null; end if;
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

-- ============================================================================================
-- Existing API, with the block check
-- ============================================================================================

-- As in 20260919000000_social.sql. Someone who blocked the caller looks like nobody; someone the
-- caller blocked answers 'blocked' (unblock them first).
create or replace function public.request_friend(p_username text)
returns text
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me_with_profile();
  other uuid;
  existing public.friendships;
begin
  select user_id into other from public.profiles where username = lower(btrim(p_username));
  if other is null then
    raise exception 'no_such_user' using errcode = 'P0001';
  end if;
  if other = uid then
    raise exception 'self' using errcode = 'P0001';
  end if;
  if exists (select 1 from public.blocks where blocker = other and blocked = uid) then
    raise exception 'no_such_user' using errcode = 'P0001';
  end if;
  if exists (select 1 from public.blocks where blocker = uid and blocked = other) then
    raise exception 'blocked' using errcode = 'P0001';
  end if;
  select * into existing from public.friendships f
  where (f.requester = uid and f.addressee = other) or (f.requester = other and f.addressee = uid);
  if found then
    if existing.status = 'accepted' or existing.requester = uid then
      return 'already';
    end if;
    update public.friendships set status = 'accepted', accepted_at = now()
    where requester = other and addressee = uid;
    return 'accepted';
  end if;
  if (select count(*) from public.friendships where requester = uid and status = 'pending') >= 50 then
    raise exception 'too_many_requests' using errcode = 'P0001';
  end if;
  insert into public.friendships (requester, addressee) values (uid, other);
  return 'requested';
end;
$$;

-- As in 20260919000000_social.sql; a signed-in viewer blocked either way by the owner gets nothing.
create or replace function public.get_shared_by_link(p_token text)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  s public.library_shares;
  li public.library_items;
begin
  if p_token is null or char_length(p_token) <> 32 then
    return null;
  end if;
  select * into s from public.library_shares where link_token = p_token;
  if not found then
    return null;
  end if;
  if auth.uid() is not null and auth.uid() <> s.owner and social_private.blocked_either(s.owner, auth.uid()) then
    return null;
  end if;
  select * into li from public.library_items where user_id = s.owner and kind = s.kind and id = s.item_id and not deleted;
  if not found then
    return null;
  end if;
  return jsonb_build_object('owner', social_private.profile_json(s.owner), 'kind', li.kind, 'data', li.data, 'edited_ms', li.edited_ms);
end;
$$;

-- ============================================================================================
-- API: is this migration there?
-- ============================================================================================

create or replace function public.social_more_version()
returns integer
language sql
immutable
set search_path = ''
as $$
  select 1;
$$;

-- ============================================================================================
-- API: blocking and reporting
-- ============================================================================================

-- Blocks [p_user]: ends the friendship (or request), cancels open trades between the two, takes them
-- off the caller's one-by-one shares, and from then on neither sees the other's things or can send
-- the other anything.
create or replace function public.block_user(p_user uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me_with_profile();
begin
  if p_user is null or p_user = uid then
    raise exception 'self' using errcode = 'P0001';
  end if;
  if not exists (select 1 from public.profiles where user_id = p_user) then
    raise exception 'no_such_user' using errcode = 'P0001';
  end if;
  if (select count(*) from public.blocks where blocker = uid) >= 1000 then
    raise exception 'too_many_blocks' using errcode = 'P0001';
  end if;
  insert into public.blocks (blocker, blocked) values (uid, p_user) on conflict do nothing;
  delete from public.friendships
  where (requester = uid and addressee = p_user) or (requester = p_user and addressee = uid);
  update public.trades set status = 'cancelled', updated_at = now()
  where status = 'open' and ((from_user = uid and to_user = p_user) or (from_user = p_user and to_user = uid));
  update public.library_shares set friend_ids = array_remove(friend_ids, p_user), updated_at = now()
  where owner = uid and p_user = any(friend_ids);
  delete from public.library_shares
  where owner = uid and not all_friends and cardinality(pod_ids) = 0 and link_token is null and cardinality(friend_ids) = 0;
  delete from public.library_share_all
  where (owner = uid and viewer = p_user) or (owner = p_user and viewer = uid);
end;
$$;

create or replace function public.unblock_user(p_user uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me();
begin
  delete from public.blocks where blocker = uid and blocked = p_user;
end;
$$;

-- The people the caller has blocked: [{user_id, username, display_name, avatar_path, blocked_at}].
create or replace function public.blocked_users()
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
    select jsonb_agg(social_private.profile_json(k.blocked) || jsonb_build_object('blocked_at', social_private.ms(k.created_at)) order by k.created_at desc)
    from public.blocks k
    where k.blocker = uid and exists (select 1 from public.profiles p where p.user_id = k.blocked)
  ), '[]'::jsonb);
end;
$$;

-- Reports [p_user] to the people who run Manabind. [p_item_kind]/[p_item_id]: what it's about, if
-- one thing ('profile', 'deck', 'collection', 'trade', 'message'). Up to 20 reports a day.
create or replace function public.report_user(p_user uuid, p_reason text, p_note text default null, p_item_kind text default null, p_item_id text default null)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me_with_profile();
  note text := nullif(btrim(coalesce(p_note, '')), '');
begin
  if p_user is null or p_user = uid then
    raise exception 'self' using errcode = 'P0001';
  end if;
  if not exists (select 1 from public.profiles where user_id = p_user) then
    raise exception 'no_such_user' using errcode = 'P0001';
  end if;
  if p_reason is null or p_reason not in ('spam', 'abuse', 'scam', 'inappropriate', 'other') then
    raise exception 'bad_reason' using errcode = 'P0001';
  end if;
  if char_length(coalesce(note, '')) > 1000 then
    raise exception 'note_too_long' using errcode = 'P0001';
  end if;
  if p_item_kind is not null and p_item_kind not in ('profile', 'deck', 'collection', 'trade', 'message') then
    raise exception 'bad_reason' using errcode = 'P0001';
  end if;
  if (select count(*) from public.reports where reporter = uid and created_at > now() - interval '1 day') >= 20 then
    raise exception 'too_many_reports' using errcode = 'P0001';
  end if;
  insert into public.reports (reporter, reported, reason, note, item_kind, item_id)
  values (uid, p_user, p_reason, note, p_item_kind, left(p_item_id, 100));
end;
$$;

-- ============================================================================================
-- API: direct messages
-- ============================================================================================

-- A message as the apps read it.
create or replace function social_private.message_json(m public.messages, recipient uuid)
returns jsonb
language sql
stable
set search_path = ''
as $$
  select jsonb_build_object(
    'id', m.id, 'conversation_id', m.conversation_id, 'sender', m.sender, 'recipient', recipient,
    'body', m.body, 'created_at', social_private.ms(m.created_at));
$$;

-- Sends [p_body] to the friend [p_to]: up to 2,000 characters, 30 messages a minute. Live to both
-- people's "dm:<id>" channels, and a notification for the friend. Answers the message.
create or replace function public.send_message(p_to uuid, p_body text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me_with_profile();
  body text := btrim(coalesce(p_body, ''));
  conv uuid;
  msg public.messages;
  payload jsonb;
begin
  if char_length(body) = 0 then
    raise exception 'empty_message' using errcode = 'P0001';
  end if;
  if char_length(body) > 2000 then
    raise exception 'dm_too_long' using errcode = 'P0001';
  end if;
  if p_to is null or p_to = uid then
    raise exception 'self' using errcode = 'P0001';
  end if;
  if social_private.blocked_either(uid, p_to) or not social_private.are_friends(uid, p_to) then
    raise exception 'cant_message' using errcode = 'P0001';
  end if;
  if (select count(*) from public.messages where sender = uid and created_at > now() - interval '1 minute') >= 30 then
    raise exception 'slow_down' using errcode = 'P0001';
  end if;
  insert into public.conversations (user_a, user_b) values (least(uid, p_to), greatest(uid, p_to))
  on conflict (user_a, user_b) do nothing;
  select c.id into conv from public.conversations c where c.user_a = least(uid, p_to) and c.user_b = greatest(uid, p_to);
  insert into public.messages (conversation_id, sender, body) values (conv, uid, body) returning * into msg;
  update public.conversations c
  set last_message_at = msg.created_at,
      read_a = case when c.user_a = uid then msg.id else c.read_a end,
      read_b = case when c.user_b = uid then msg.id else c.read_b end
  where c.id = conv;
  payload := social_private.message_json(msg, p_to);
  -- Live delivery is a nicety: the message is saved either way, and the apps also reload.
  begin
    perform realtime.send(payload, 'message', 'dm:' || p_to, true);
    perform realtime.send(payload, 'message', 'dm:' || uid, true);
  exception when others then
    raise warning 'message not broadcast: %', sqlerrm;
  end;
  perform social_private.notify(p_to, 'friends', social_private.display_name(uid), left(body, 140), 'messages', 'dm-' || uid);
  return payload;
end;
$$;

-- The caller's conversations, newest first (up to 200), leaving out anyone blocked either way:
-- [{id, other (profile), last {id, sender, body, created_at}, unread, can_send}].
create or replace function public.list_conversations()
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
    select jsonb_agg(x.row order by x.at desc)
    from (
      select jsonb_build_object(
        'id', c.id,
        'other', social_private.profile_json(o.other),
        'last', (select jsonb_build_object('id', m.id, 'sender', m.sender, 'body', m.body, 'created_at', social_private.ms(m.created_at))
                 from public.messages m where m.conversation_id = c.id order by m.id desc limit 1),
        'unread', (select count(*) from public.messages m
                   where m.conversation_id = c.id and m.sender <> uid and m.id > (case when c.user_a = uid then c.read_a else c.read_b end)),
        'can_send', social_private.are_friends(uid, o.other)
      ) as row, c.last_message_at as at
      from public.conversations c,
      lateral (select case when c.user_a = uid then c.user_b else c.user_a end as other) o
      where (c.user_a = uid or c.user_b = uid)
        and not social_private.blocked_either(uid, o.other)
        and exists (select 1 from public.profiles p where p.user_id = o.other)
      order by c.last_message_at desc
      limit 200
    ) x
  ), '[]'::jsonb);
end;
$$;

-- One page of the conversation with [p_with], oldest first: the [p_limit] (up to 100) messages
-- before message [p_before] (null: the newest). Empty across a block.
create or replace function public.get_messages(p_with uuid, p_before bigint default null, p_limit integer default 50)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me();
  conv uuid;
begin
  if p_with is null or p_with = uid or social_private.blocked_either(uid, p_with) then
    return '[]'::jsonb;
  end if;
  select c.id into conv from public.conversations c where c.user_a = least(uid, p_with) and c.user_b = greatest(uid, p_with);
  if conv is null then
    return '[]'::jsonb;
  end if;
  return coalesce((
    select jsonb_agg(social_private.message_json(m, case when m.sender = uid then p_with else uid end) order by m.id)
    from public.messages m
    where m.id in (
      select p.id from public.messages p
      where p.conversation_id = conv and (p_before is null or p.id < p_before)
      order by p.id desc
      limit least(greatest(coalesce(p_limit, 50), 1), 100)
    )
  ), '[]'::jsonb);
end;
$$;

-- The caller has read everything in the conversation with [p_with].
create or replace function public.mark_read(p_with uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me();
begin
  update public.conversations c
  set read_a = case when c.user_a = uid then coalesce((select max(m.id) from public.messages m where m.conversation_id = c.id), c.read_a) else c.read_a end,
      read_b = case when c.user_b = uid then coalesce((select max(m.id) from public.messages m where m.conversation_id = c.id), c.read_b) else c.read_b end
  where c.user_a = least(uid, p_with) and c.user_b = greatest(uid, p_with);
end;
$$;

-- How many messages wait unread, for the badge (blocked people left out).
create or replace function public.unread_messages()
returns integer
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce(sum((
    select count(*) from public.messages m
    where m.conversation_id = c.id and m.sender <> auth.uid()
      and m.id > (case when c.user_a = auth.uid() then c.read_a else c.read_b end)
  )), 0)::int
  from public.conversations c
  where (c.user_a = auth.uid() or c.user_b = auth.uid())
    and not social_private.blocked_either(c.user_a, c.user_b);
$$;

-- Whether the caller may listen on [p_topic]: only "dm:<their own id>". Nobody broadcasts on it
-- directly (no insert policy); send_message does.
create or replace function public.dm_channel_member(p_topic text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select auth.uid() is not null and p_topic is not null and p_topic = 'dm:' || auth.uid()::text;
$$;

drop policy if exists "people listen on their own message channel" on realtime.messages;
create policy "people listen on their own message channel" on realtime.messages
  for select to authenticated
  using (realtime.messages.extension = 'broadcast' and public.dm_channel_member(realtime.topic()));

-- ============================================================================================
-- API: trade history and reputation
-- ============================================================================================

-- A thumbs up ([p_positive]) or down for the other side of an accepted trade, once the caller has
-- updated their binders for it. Saying again changes it: one per side per trade.
create or replace function public.rate_trade(p_trade uuid, p_positive boolean)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me_with_profile();
  t public.trades;
  other uuid;
begin
  if p_positive is null then
    raise exception 'cant_rate' using errcode = 'P0001';
  end if;
  select * into t from public.trades
  where id = p_trade and status = 'accepted'
    and ((from_user = uid and from_applied) or (to_user = uid and to_applied));
  if not found then
    raise exception 'cant_rate' using errcode = 'P0001';
  end if;
  other := case when t.from_user = uid then t.to_user else t.from_user end;
  if social_private.blocked_either(uid, other) then
    raise exception 'cant_rate' using errcode = 'P0001';
  end if;
  insert into public.trade_ratings as r (trade_id, rater, rated, positive)
  values (t.id, uid, other, p_positive)
  on conflict (trade_id, rater) do update set positive = excluded.positive, updated_at = now();
end;
$$;

-- [p_user]'s trading record, for the caller (themself or a friend; null otherwise, or across a
-- block): {total (accepted trades), with_you, since (ms of their first, or null), positive, negative}.
create or replace function public.trade_reputation(p_user uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me();
  result jsonb;
begin
  if p_user is null or (p_user <> uid and (social_private.blocked_either(uid, p_user) or not social_private.are_friends(uid, p_user))) then
    return null;
  end if;
  select jsonb_build_object(
    'total', count(*),
    'with_you', case when p_user = uid then 0 else count(*) filter (where t.from_user = uid or t.to_user = uid) end,
    'since', social_private.ms(min(t.created_at)),
    'positive', (select count(*) from public.trade_ratings r where r.rated = p_user and r.positive),
    'negative', (select count(*) from public.trade_ratings r where r.rated = p_user and not r.positive)
  ) into result
  from public.trades t
  where t.status = 'accepted' and (t.from_user = p_user or t.to_user = p_user);
  return result;
end;
$$;

-- The caller's own ratings of their recent trades: {trade id: positive}.
create or replace function public.my_trade_ratings()
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce(jsonb_object_agg(r.trade_id, r.positive), '{}'::jsonb)
  from (select * from public.trade_ratings where rater = auth.uid() order by created_at desc limit 300) r;
$$;

-- ============================================================================================
-- Activity feed
-- ============================================================================================

-- Notes a deck or binder newly shared with more people (once an hour per item at most). Never fails
-- the share itself.
create or replace function social_private.on_share_event()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if tg_table_name = 'library_share_all' then
    if not exists (select 1 from public.social_events e where e.actor = new.owner and e.kind = 'shared' and e.item_kind = new.kind
                   and e.item_id is null and e.created_at > now() - interval '1 hour') then
      insert into public.social_events (actor, kind, item_kind, item_id) values (new.owner, 'shared', new.kind, null);
    end if;
  elsif (tg_op = 'INSERT' and (new.all_friends or cardinality(new.pod_ids) > 0 or cardinality(new.friend_ids) > 0))
     or (tg_op = 'UPDATE' and ((new.all_friends and not old.all_friends) or not (new.pod_ids <@ old.pod_ids) or not (new.friend_ids <@ old.friend_ids))) then
    if not exists (select 1 from public.social_events e where e.actor = new.owner and e.kind = 'shared' and e.item_kind = new.kind
                   and e.item_id = new.item_id and e.created_at > now() - interval '1 hour') then
      insert into public.social_events (actor, kind, item_kind, item_id) values (new.owner, 'shared', new.kind, new.item_id);
    end if;
  end if;
  delete from public.social_events where actor = new.owner and created_at < now() - interval '90 days';
  return null;
exception when others then
  raise warning 'activity not noted: %', sqlerrm;
  return null;
end;
$$;

drop trigger if exists library_shares_event on public.library_shares;
create trigger library_shares_event
  after insert or update on public.library_shares
  for each row execute function social_private.on_share_event();

drop trigger if exists library_share_all_event on public.library_share_all;
create trigger library_share_all_event
  after insert on public.library_share_all
  for each row execute function social_private.on_share_event();

-- The apps call this after the user marks cards for trade ([p_cards]: [{name, imageUrl}], up to 50),
-- for friends' activity feeds. Marks within 6 hours of each other make one entry.
create or replace function public.note_for_trade(p_cards jsonb)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me_with_profile();
  added jsonb;
  n integer;
  recent public.social_events;
begin
  if p_cards is null or jsonb_typeof(p_cards) <> 'array' or jsonb_array_length(p_cards) = 0 then
    return;
  end if;
  if jsonb_array_length(p_cards) > 50 then
    raise exception 'too_many_cards' using errcode = 'P0001';
  end if;
  select coalesce(jsonb_agg(jsonb_build_object('name', left(c ->> 'name', 200), 'imageUrl', left(c ->> 'imageUrl', 500))), '[]'::jsonb), count(*)
  into added, n
  from jsonb_array_elements(p_cards) c
  where jsonb_typeof(c) = 'object' and coalesce(c ->> 'name', '') <> '';
  if n = 0 then
    return;
  end if;
  select * into recent from public.social_events
  where actor = uid and kind = 'for_trade' and created_at > now() - interval '6 hours'
  order by created_at desc limit 1;
  if found then
    update public.social_events
    set detail = jsonb_build_object(
          'count', coalesce((recent.detail ->> 'count')::int, 0) + n,
          'cards', (select coalesce(jsonb_agg(x.c), '[]'::jsonb) from (
                      select c from jsonb_array_elements(coalesce(recent.detail -> 'cards', '[]'::jsonb) || added) with ordinality as a(c, i)
                      order by i limit 6) x)),
        created_at = now()
    where id = recent.id;
  else
    insert into public.social_events (actor, kind, detail)
    values (uid, 'for_trade', jsonb_build_object('count', n, 'cards', (
      select coalesce(jsonb_agg(x.c), '[]'::jsonb) from (select c from jsonb_array_elements(added) with ordinality as a(c, i) order by i limit 6) x)));
  end if;
  delete from public.social_events where actor = uid and created_at < now() - interval '90 days';
end;
$$;

-- What friends (and pod-mates) have been doing, newest first, [p_limit] (up to 50) items before
-- [p_before] (ms; null: now), from the last 60 days. Only what the caller may see, nobody blocked:
--   shared       a deck or binder newly shared (item_id null: all decks / the whole collection)
--   deck_updated a deck shared with the caller was changed
--   pod_game     a game recorded in a pod the caller is in
--   for_trade    cards marked for trade (friends only)
-- [{kind, actor (profile), at, item_kind, item_id, name, cover, pod_id, pod_name, format, winner, players, count, cards}]
create or replace function public.activity_feed(p_before bigint default null, p_limit integer default 30)
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
  ),
  people as (
    select user_id from friends
    union
    select m2.user_id
    from public.pod_members m1
    join public.pod_members m2 on m2.pod_id = m1.pod_id
    where m1.user_id = uid and m2.user_id <> uid
  ),
  shared as (
    select 'shared'::text as kind, e.actor, e.created_at as at, e.item_kind, e.item_id,
           li.data ->> 'name' as name,
           case when e.item_kind = 'deck' then li.data -> 'commander' ->> 'imageUrl' else li.data -> 'entries' -> 0 ->> 'imageUrl' end as cover,
           null::uuid as pod_id, null::jsonb as detail
    from public.social_events e
    join people p on p.user_id = e.actor
    left join public.library_items li on li.user_id = e.actor and li.kind = e.item_kind and li.id = e.item_id and not li.deleted
    where e.kind = 'shared' and e.created_at < before_at and e.created_at > since_at
      and not social_private.blocked_either(uid, e.actor)
      and case when e.item_id is null then social_private.shares_all_with(e.actor, e.item_kind, uid)
               else li.id is not null and social_private.can_view_item(e.actor, e.item_kind, e.item_id, uid) end
    order by e.created_at desc
    limit lim
  ),
  for_trade as (
    select 'for_trade'::text, e.actor, e.created_at, null::text, null::text, null::text, null::text, null::uuid, e.detail
    from public.social_events e
    join friends f on f.user_id = e.actor
    where e.kind = 'for_trade' and e.created_at < before_at and e.created_at > since_at
      and not social_private.blocked_either(uid, e.actor)
    order by e.created_at desc
    limit lim
  ),
  decks as (
    select 'deck_updated'::text, v.owner, to_timestamp(v.edited_ms / 1000.0), 'deck'::text, v.id, v.data ->> 'name',
           v.data -> 'commander' ->> 'imageUrl', null::uuid, null::jsonb
    from social_private.visible_items(uid) v
    where v.kind = 'deck' and v.edited_ms < social_private.ms(before_at) and v.edited_ms > social_private.ms(since_at)
    order by v.edited_ms desc
    limit lim
  ),
  games as (
    select 'pod_game'::text, g.recorded_by, g.created_at, null::text, g.id::text, null::text, null::text, g.pod_id,
           jsonb_build_object('format', g.format, 'players', jsonb_array_length(g.players),
             'winner', (select p ->> 'name' from jsonb_array_elements(g.players) p where p ->> 'result' = 'WIN' limit 1)) as detail
    from public.pod_games g
    join public.pod_members m on m.pod_id = g.pod_id and m.user_id = uid
    where g.recorded_by <> uid and g.created_at < before_at and g.created_at > since_at
      and not social_private.blocked_either(uid, g.recorded_by)
    order by g.created_at desc
    limit lim
  ),
  merged as (
    select * from shared
    union all select * from for_trade
    union all select * from decks
    union all select * from games
    order by at desc
    limit lim
  )
  select coalesce(jsonb_agg(jsonb_strip_nulls(jsonb_build_object(
      'kind', x.kind,
      'actor', social_private.profile_json(x.actor),
      'at', social_private.ms(x.at),
      'item_kind', x.item_kind,
      'item_id', x.item_id,
      'name', x.name,
      'cover', x.cover,
      'pod_id', x.pod_id,
      'pod_name', (select pd.name from public.pods pd where pd.id = x.pod_id),
      'format', nullif(x.detail ->> 'format', ''),
      'winner', x.detail ->> 'winner',
      'players', (x.detail ->> 'players')::int,
      'count', (x.detail ->> 'count')::int,
      'cards', x.detail -> 'cards'
    )) order by x.at desc), '[]'::jsonb)
  into result
  from merged x
  where exists (select 1 from public.profiles pr where pr.user_id = x.actor);
  return result;
end;
$$;

-- ============================================================================================
-- API: cards for trade
-- ============================================================================================

-- The copies [p_owner] has marked for trade (a binder card's "forTrade"), for the caller: themself
-- or a friend (marking a card for trade shows it to all friends, shared binder or not). Null
-- otherwise, or across a block. Up to 500:
-- [{item_id, item_name, scryfall_id, name, image_url, for_trade, quantity, foil_quantity, condition}].
create or replace function public.for_trade_list(p_owner uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me();
begin
  if p_owner is null or (p_owner <> uid and (social_private.blocked_either(uid, p_owner) or not social_private.are_friends(uid, p_owner))) then
    return null;
  end if;
  return coalesce((
    select jsonb_agg(h.row order by h.row ->> 'name', h.row ->> 'item_name')
    from (
      select jsonb_strip_nulls(jsonb_build_object(
        'item_id', li.id, 'item_name', li.data ->> 'name',
        'scryfall_id', e ->> 'scryfallId', 'name', e ->> 'name', 'image_url', e ->> 'imageUrl',
        'for_trade', social_private.for_trade_count(e),
        'quantity', coalesce((e ->> 'quantity')::int, 0), 'foil_quantity', coalesce((e ->> 'foilQuantity')::int, 0),
        'condition', e ->> 'condition'
      )) as row
      from public.library_items li,
      lateral jsonb_array_elements(coalesce(li.data -> 'entries', '[]')) e
      where li.user_id = p_owner and li.kind = 'collection' and not li.deleted
        and coalesce(li.data ->> 'type', 'OWNED') <> 'WISHLIST'
        and social_private.for_trade_count(e) > 0
      limit 500
    ) h
  ), '[]'::jsonb);
end;
$$;

-- Wishlist matches both ways, per friend (blocked people left out):
--   they_have: their copies of cards on the caller's wishlists — marked for trade, or in a binder
--              they share with the caller;
--   they_want: the caller's copies of cards on wishlists the friend shares with the caller.
-- Each as trade lines, one copy of each card (up to 50 a side), copies marked for trade first:
-- [{friend, they_have: [{scryfallId, name, imageUrl, foil, quantity, collectionId, condition, forTrade}], they_want: [...]}].
create or replace function public.trade_matches()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me();
  result jsonb;
begin
  with
  friends as (
    select case when f.requester = uid then f.addressee else f.requester end as user_id
    from public.friendships f
    where (f.requester = uid or f.addressee = uid) and f.status = 'accepted'
      and not social_private.blocked_either(f.requester, f.addressee)
  ),
  my_wants as (
    select distinct lower(e ->> 'name') as name
    from public.library_items li,
    lateral jsonb_array_elements(coalesce(li.data -> 'entries', '[]')) e
    where li.user_id = uid and li.kind = 'collection' and not li.deleted and li.data ->> 'type' = 'WISHLIST'
  ),
  have_rows as (
    select li.user_id as owner, li.id as item_id, e, social_private.for_trade_count(e) as ft,
           coalesce((e ->> 'quantity')::int, 0) as q, coalesce((e ->> 'foilQuantity')::int, 0) as fq
    from friends f
    join public.library_items li on li.user_id = f.user_id and li.kind = 'collection' and not li.deleted
      and coalesce(li.data ->> 'type', 'OWNED') <> 'WISHLIST',
    lateral jsonb_array_elements(coalesce(li.data -> 'entries', '[]')) e
    where lower(e ->> 'name') in (select name from my_wants)
  ),
  have as (
    select distinct on (h.owner, lower(h.e ->> 'name')) h.*
    from have_rows h
    where h.q + h.fq > 0 and (h.ft > 0 or social_private.can_view_item(h.owner, 'collection', h.item_id, uid))
    order by h.owner, lower(h.e ->> 'name'), (h.ft > 0) desc, h.q desc, h.fq desc
  ),
  their_wants as (
    select distinct v.owner, lower(e ->> 'name') as name
    from social_private.visible_items(uid) v
    join friends f on f.user_id = v.owner,
    lateral jsonb_array_elements(coalesce(v.data -> 'entries', '[]')) e
    where v.kind = 'collection' and v.data ->> 'type' = 'WISHLIST'
  ),
  mine_rows as (
    select tw.owner, li.id as item_id, e, social_private.for_trade_count(e) as ft,
           coalesce((e ->> 'quantity')::int, 0) as q, coalesce((e ->> 'foilQuantity')::int, 0) as fq
    from public.library_items li
    cross join lateral jsonb_array_elements(coalesce(li.data -> 'entries', '[]')) e
    join their_wants tw on tw.name = lower(e ->> 'name')
    where li.user_id = uid and li.kind = 'collection' and not li.deleted
      and coalesce(li.data ->> 'type', 'OWNED') <> 'WISHLIST'
  ),
  mine as (
    select distinct on (m.owner, lower(m.e ->> 'name')) m.*
    from mine_rows m
    where m.q + m.fq > 0
    order by m.owner, lower(m.e ->> 'name'), (m.ft > 0) desc, m.q desc, m.fq desc
  ),
  cards as (
    select owner, 'have'::text as side, ft, e ->> 'name' as name, jsonb_strip_nulls(jsonb_build_object(
      'scryfallId', e ->> 'scryfallId', 'name', e ->> 'name', 'imageUrl', e ->> 'imageUrl',
      'foil', q <= 0, 'quantity', 1, 'collectionId', item_id, 'condition', e ->> 'condition', 'forTrade', ft > 0)) as card
    from have
    union all
    select owner, 'want', ft, e ->> 'name', jsonb_strip_nulls(jsonb_build_object(
      'scryfallId', e ->> 'scryfallId', 'name', e ->> 'name', 'imageUrl', e ->> 'imageUrl',
      'foil', q <= 0, 'quantity', 1, 'collectionId', item_id, 'condition', e ->> 'condition', 'forTrade', ft > 0))
    from mine
  ),
  ranked as (
    select c.*, row_number() over (partition by c.owner, c.side order by (c.ft > 0) desc, c.name) as n from cards c
  )
  select coalesce(jsonb_agg(jsonb_build_object(
      'friend', r.owner,
      'they_have', r.have,
      'they_want', r.want
    ) order by jsonb_array_length(r.have) + jsonb_array_length(r.want) desc), '[]'::jsonb)
  into result
  from (
    select owner,
           coalesce(jsonb_agg(card order by (ft > 0) desc, name) filter (where side = 'have'), '[]'::jsonb) as have,
           coalesce(jsonb_agg(card order by (ft > 0) desc, name) filter (where side = 'want'), '[]'::jsonb) as want
    from ranked
    where n <= 50
    group by owner
  ) r;
  return result;
end;
$$;

-- ============================================================================================
-- Who may call what
-- ============================================================================================

revoke execute on all functions in schema social_private from public, anon, authenticated;

do $$
declare
  f text;
begin
  foreach f in array array[
    'social_more_version()',
    'block_user(uuid)', 'unblock_user(uuid)', 'blocked_users()', 'report_user(uuid, text, text, text, text)',
    'send_message(uuid, text)', 'list_conversations()', 'get_messages(uuid, bigint, integer)', 'mark_read(uuid)',
    'unread_messages()', 'dm_channel_member(text)',
    'rate_trade(uuid, boolean)', 'trade_reputation(uuid)', 'my_trade_ratings()',
    'note_for_trade(jsonb)', 'activity_feed(bigint, integer)',
    'for_trade_list(uuid)', 'trade_matches()',
    'request_friend(text)'
  ] loop
    execute format('revoke execute on function public.%s from public, anon', f);
    execute format('grant execute on function public.%s to authenticated', f);
  end loop;
end;
$$;

-- Still open to anyone with the link, signed in or not.
revoke execute on function public.get_shared_by_link(text) from public;
grant execute on function public.get_shared_by_link(text) to anon, authenticated;
