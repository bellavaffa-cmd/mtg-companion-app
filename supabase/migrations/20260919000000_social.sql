-- MTG Companion — social: profiles, friends, pods, sharing decks and binders, joining a life
-- counter, and trades.
--
-- Safe to run more than once.
--
-- Every table here has row-level security on and no policies, so nothing can read or write it
-- directly. All access goes through the functions below, which run as their owner and check the
-- caller (auth.uid()) themselves. That keeps every rule about who sees what in one place.
--
-- Shared decks and binders are read through get_shared_item() / get_shared_by_link(), never by
-- loosening library_items' own policies: the apps' sync pulls every library_items row it is
-- allowed to see, so a wider policy would sync friends' decks into your own library.
--
-- Profile pictures live in the public 'avatars' bucket under <user id>/<random name>. Only people
-- who can see your profile learn that name, and each user can only write their own folder.

create schema if not exists social_private;
revoke all on schema social_private from public, anon, authenticated;

-- ============================================================================================
-- Profiles
-- ============================================================================================

create table if not exists public.profiles (
  user_id uuid primary key references auth.users (id) on delete cascade,
  username text not null,
  display_name text not null,
  avatar_path text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint profiles_username_format check (username ~ '^[a-z0-9_]{3,20}$'),
  constraint profiles_display_name_length check (char_length(btrim(display_name)) between 1 and 40),
  constraint profiles_avatar_path_own check (avatar_path is null or (avatar_path like user_id::text || '/%' and char_length(avatar_path) <= 200))
);
create unique index if not exists profiles_username_key on public.profiles (username);
alter table public.profiles enable row level security;
revoke all on public.profiles from anon, authenticated;

-- ============================================================================================
-- Friends: one row per pair, pending until the addressee accepts
-- ============================================================================================

create table if not exists public.friendships (
  requester uuid not null references public.profiles (user_id) on delete cascade,
  addressee uuid not null references public.profiles (user_id) on delete cascade,
  status text not null default 'pending' check (status in ('pending', 'accepted')),
  created_at timestamptz not null default now(),
  accepted_at timestamptz,
  primary key (requester, addressee),
  check (requester <> addressee)
);
create unique index if not exists friendships_pair_key
  on public.friendships (least(requester, addressee), greatest(requester, addressee));
create index if not exists friendships_addressee_idx on public.friendships (addressee);
alter table public.friendships enable row level security;
revoke all on public.friendships from anon, authenticated;

-- ============================================================================================
-- Pods: a named group of friends, made by its owner
-- ============================================================================================

create table if not exists public.pods (
  id uuid primary key default gen_random_uuid(),
  owner uuid not null references public.profiles (user_id) on delete cascade,
  name text not null check (char_length(btrim(name)) between 1 and 40),
  created_at timestamptz not null default now()
);
create index if not exists pods_owner_idx on public.pods (owner);
alter table public.pods enable row level security;
revoke all on public.pods from anon, authenticated;

create table if not exists public.pod_members (
  pod_id uuid not null references public.pods (id) on delete cascade,
  user_id uuid not null references public.profiles (user_id) on delete cascade,
  added_at timestamptz not null default now(),
  primary key (pod_id, user_id)
);
create index if not exists pod_members_user_idx on public.pod_members (user_id);
alter table public.pod_members enable row level security;
revoke all on public.pod_members from anon, authenticated;

-- ============================================================================================
-- Sharing a deck or binder, view only: with all friends, with pods, and/or by link
-- ============================================================================================

create table if not exists public.library_shares (
  owner uuid not null references public.profiles (user_id) on delete cascade,
  kind text not null check (kind in ('deck', 'collection')),
  item_id text not null,
  all_friends boolean not null default false,
  pod_ids uuid[] not null default '{}',
  link_token text unique,
  updated_at timestamptz not null default now(),
  primary key (owner, kind, item_id)
);
alter table public.library_shares enable row level security;
revoke all on public.library_shares from anon, authenticated;

-- ============================================================================================
-- Life counter matches: the host's device shows a QR code per seat; a player scans it to sit there
-- ============================================================================================

create table if not exists public.matches (
  id uuid primary key default gen_random_uuid(),
  host uuid not null references public.profiles (user_id) on delete cascade,
  code text not null unique,
  seats integer not null check (seats between 1 and 10),
  created_at timestamptz not null default now(),
  ended_at timestamptz
);
create index if not exists matches_host_idx on public.matches (host, created_at);
alter table public.matches enable row level security;
revoke all on public.matches from anon, authenticated;

create table if not exists public.match_players (
  match_id uuid not null references public.matches (id) on delete cascade,
  seat integer not null,
  user_id uuid not null references public.profiles (user_id) on delete cascade,
  joined_at timestamptz not null default now(),
  primary key (match_id, seat),
  unique (match_id, user_id)
);
create index if not exists match_players_user_idx on public.match_players (user_id);
alter table public.match_players enable row level security;
revoke all on public.match_players from anon, authenticated;

-- ============================================================================================
-- Trades: a friend asks for cards from your shared binder, optionally offering cards back
-- ============================================================================================

create table if not exists public.trades (
  id uuid primary key default gen_random_uuid(),
  from_user uuid not null references public.profiles (user_id) on delete cascade,
  to_user uuid not null references public.profiles (user_id) on delete cascade,
  -- Cards: [{scryfallId, name, imageUrl, setCode, collectorNumber, foil, quantity, collectionId}].
  -- want = what from_user asks for (from to_user's binders); give = what from_user offers.
  want jsonb not null default '[]',
  give jsonb not null default '[]',
  message text check (char_length(message) <= 500),
  reply text check (char_length(reply) <= 500),
  status text not null default 'open' check (status in ('open', 'accepted', 'declined', 'cancelled', 'countered')),
  reply_to uuid references public.trades (id) on delete set null,
  from_applied boolean not null default false,
  to_applied boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (from_user <> to_user)
);
create index if not exists trades_from_idx on public.trades (from_user, updated_at desc);
create index if not exists trades_to_idx on public.trades (to_user, updated_at desc);
alter table public.trades enable row level security;
revoke all on public.trades from anon, authenticated;

-- ============================================================================================
-- Helpers (private: not callable through the API)
-- ============================================================================================

create or replace function social_private.me()
returns uuid
language plpgsql
stable
set search_path = ''
as $$
begin
  if auth.uid() is null then
    raise exception 'not_signed_in' using errcode = 'P0001';
  end if;
  return auth.uid();
end;
$$;

-- The caller's own id, and their profile must exist: everything social starts from a username.
create or replace function social_private.me_with_profile()
returns uuid
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me();
begin
  if not exists (select 1 from public.profiles where user_id = uid) then
    raise exception 'no_profile' using errcode = 'P0001';
  end if;
  return uid;
end;
$$;

create or replace function social_private.are_friends(a uuid, b uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.friendships f
    where f.status = 'accepted'
      and ((f.requester = a and f.addressee = b) or (f.requester = b and f.addressee = a))
  );
$$;

create or replace function social_private.is_pod_member(pod uuid, who uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (select 1 from public.pod_members m where m.pod_id = pod and m.user_id = who);
$$;

-- Whether [viewer] may see the deck or binder behind [s].
create or replace function social_private.can_view_share(s public.library_shares, viewer uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select s.owner = viewer
    or (s.all_friends and social_private.are_friends(s.owner, viewer))
    or exists (
      select 1 from unnest(s.pod_ids) as p(id)
      where social_private.is_pod_member(p.id, viewer) and social_private.is_pod_member(p.id, s.owner)
    );
$$;

create or replace function social_private.profile_json(uid uuid)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select jsonb_build_object('user_id', p.user_id, 'username', p.username, 'display_name', p.display_name, 'avatar_path', p.avatar_path)
  from public.profiles p where p.user_id = uid;
$$;

-- A deck's or binder's card count, as the apps count it.
create or replace function social_private.card_count(kind text, data jsonb)
returns integer
language sql
immutable
set search_path = ''
as $$
  select case
    when kind = 'deck' then coalesce((
      select sum(coalesce((c ->> 'quantity')::int, 0)) from jsonb_array_elements(coalesce(data -> 'cards', '[]')) c
    ), 0)
    else coalesce((
      select sum(coalesce((e ->> 'quantity')::int, 0) + coalesce((e ->> 'foilQuantity')::int, 0))
      from jsonb_array_elements(coalesce(data -> 'entries', '[]')) e
    ), 0)
  end::int;
$$;

-- Checks a trade's list of cards and keeps only the known fields.
create or replace function social_private.clean_trade_cards(cards jsonb)
returns jsonb
language plpgsql
immutable
set search_path = ''
as $$
declare
  c jsonb;
  out jsonb := '[]';
  qty integer;
begin
  if cards is null or jsonb_typeof(cards) <> 'array' then
    return '[]';
  end if;
  if jsonb_array_length(cards) > 100 then
    raise exception 'too_many_cards' using errcode = 'P0001';
  end if;
  for c in select value from jsonb_array_elements(cards) loop
    qty := coalesce((c ->> 'quantity')::int, 0);
    if jsonb_typeof(c) <> 'object' or coalesce(c ->> 'scryfallId', '') = '' or coalesce(c ->> 'name', '') = ''
       or qty < 1 or qty > 99 then
      raise exception 'bad_card' using errcode = 'P0001';
    end if;
    out := out || jsonb_build_array(jsonb_strip_nulls(jsonb_build_object(
      'scryfallId', left(c ->> 'scryfallId', 64),
      'name', left(c ->> 'name', 200),
      'imageUrl', left(c ->> 'imageUrl', 500),
      'setCode', left(c ->> 'setCode', 12),
      'collectorNumber', left(c ->> 'collectorNumber', 12),
      'foil', coalesce((c ->> 'foil')::boolean, false),
      'quantity', qty,
      'collectionId', left(c ->> 'collectionId', 64)
    )));
  end loop;
  return out;
end;
$$;

-- ============================================================================================
-- API: profile
-- ============================================================================================

-- Creates or updates the caller's profile. [avatar_path]: null keeps the current picture, '' removes it.
create or replace function public.save_profile(p_username text, p_display_name text, p_avatar_path text default null)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me();
  uname text := lower(btrim(p_username));
  dname text := btrim(p_display_name);
begin
  if uname !~ '^[a-z0-9_]{3,20}$' then
    raise exception 'bad_username' using errcode = 'P0001';
  end if;
  if char_length(coalesce(dname, '')) not between 1 and 40 then
    raise exception 'bad_display_name' using errcode = 'P0001';
  end if;
  if p_avatar_path is not null and p_avatar_path <> '' and p_avatar_path not like uid::text || '/%' then
    raise exception 'bad_avatar' using errcode = 'P0001';
  end if;
  if exists (select 1 from public.profiles where username = uname and user_id <> uid) then
    raise exception 'username_taken' using errcode = 'P0001';
  end if;
  insert into public.profiles as p (user_id, username, display_name, avatar_path)
  values (uid, uname, dname, nullif(p_avatar_path, ''))
  on conflict (user_id) do update
    set username = excluded.username,
        display_name = excluded.display_name,
        avatar_path = case when p_avatar_path is null then p.avatar_path else nullif(p_avatar_path, '') end,
        updated_at = now();
  return social_private.profile_json(uid);
end;
$$;

-- Whether a username is free (for the profile form, as the user types).
create or replace function public.username_available(p_username text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select lower(btrim(p_username)) ~ '^[a-z0-9_]{3,20}$'
    and not exists (select 1 from public.profiles where username = lower(btrim(p_username)) and user_id <> auth.uid());
$$;

-- ============================================================================================
-- API: everything the Friends screen shows, in one call
-- ============================================================================================

create or replace function public.social_overview()
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
  if not exists (select 1 from public.profiles where user_id = uid) then
    return jsonb_build_object('me', null);
  end if;

  with
  friends as (
    select case when f.requester = uid then f.addressee else f.requester end as user_id,
           f.status, f.addressee = uid as incoming, coalesce(f.accepted_at, f.created_at) as since
    from public.friendships f
    where f.requester = uid or f.addressee = uid
  ),
  my_pods as (
    select p.id, p.name, p.owner, p.created_at,
           (select coalesce(jsonb_agg(m.user_id order by m.added_at), '[]') from public.pod_members m where m.pod_id = p.id) as members
    from public.pods p
    where social_private.is_pod_member(p.id, uid)
  ),
  shared as (
    select s.owner, s.kind, s.item_id, li.data ->> 'name' as name, li.edited_ms,
           case when s.kind = 'deck' then li.data -> 'commander' ->> 'imageUrl' end as cover,
           social_private.card_count(s.kind, li.data) as cards
    from public.library_shares s
    join public.library_items li on li.user_id = s.owner and li.kind = s.kind and li.id = s.item_id and not li.deleted
    where s.owner <> uid and social_private.can_view_share(s, uid)
  ),
  my_shares as (
    select s.kind, s.item_id, s.all_friends, s.pod_ids, s.link_token from public.library_shares s where s.owner = uid
  ),
  my_trades as (
    select t.* from public.trades t
    where t.from_user = uid or t.to_user = uid
    order by t.updated_at desc
    limit 100
  ),
  people as (
    select user_id from friends
    union select (jsonb_array_elements_text(members))::uuid from my_pods
    union select owner from shared
    union select from_user from my_trades
    union select to_user from my_trades
  )
  select jsonb_build_object(
    'me', social_private.profile_json(uid),
    'people', coalesce((select jsonb_object_agg(pe.user_id, social_private.profile_json(pe.user_id)) from people pe
                        where exists (select 1 from public.profiles pr where pr.user_id = pe.user_id)), '{}'),
    'friends', coalesce((select jsonb_agg(jsonb_build_object('user_id', f.user_id, 'status', f.status, 'incoming', f.incoming, 'since', f.since) order by f.since desc) from friends f), '[]'),
    'pods', coalesce((select jsonb_agg(jsonb_build_object('id', p.id, 'name', p.name, 'owner', p.owner, 'members', p.members) order by p.created_at) from my_pods p), '[]'),
    'shared_with_me', coalesce((select jsonb_agg(jsonb_build_object('owner', s.owner, 'kind', s.kind, 'item_id', s.item_id, 'name', s.name, 'cover', s.cover, 'cards', s.cards, 'edited_ms', s.edited_ms) order by s.edited_ms desc) from shared s), '[]'),
    'my_shares', coalesce((select jsonb_agg(to_jsonb(s)) from my_shares s), '[]'),
    'trades', coalesce((select jsonb_agg(jsonb_build_object(
        'id', t.id, 'from_user', t.from_user, 'to_user', t.to_user, 'want', t.want, 'give', t.give,
        'message', t.message, 'reply', t.reply, 'status', t.status, 'reply_to', t.reply_to,
        'from_applied', t.from_applied, 'to_applied', t.to_applied, 'created_at', t.created_at, 'updated_at', t.updated_at
      ) order by t.updated_at desc) from my_trades t), '[]')
  ) into result;
  return result;
end;
$$;

-- Just the counts for a badge: friend requests and trades waiting on the caller.
create or replace function public.social_inbox()
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select jsonb_build_object(
    'friend_requests', (select count(*) from public.friendships f where f.addressee = auth.uid() and f.status = 'pending'),
    'trades', (select count(*) from public.trades t where
                 (t.to_user = auth.uid() and t.status = 'open')
                 or (t.status = 'accepted' and ((t.from_user = auth.uid() and not t.from_applied) or (t.to_user = auth.uid() and not t.to_applied))))
  );
$$;

-- ============================================================================================
-- API: friends
-- ============================================================================================

-- Asks [p_username] to be friends. If they already asked the caller, it's accepted at once.
-- Answers 'requested', 'accepted' or 'already'.
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

-- Accepts or declines a request from [p_user].
create or replace function public.respond_friend(p_user uuid, p_accept boolean)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me();
begin
  if p_accept then
    update public.friendships set status = 'accepted', accepted_at = now()
    where requester = p_user and addressee = uid and status = 'pending';
  else
    delete from public.friendships where requester = p_user and addressee = uid and status = 'pending';
  end if;
end;
$$;

-- Unfriends [p_user], or takes back a request to them.
create or replace function public.remove_friend(p_user uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me();
begin
  delete from public.friendships
  where (requester = uid and addressee = p_user) or (requester = p_user and addressee = uid);
end;
$$;

-- ============================================================================================
-- API: pods
-- ============================================================================================

-- Creates a pod ([p_pod] null) or renames and re-members the caller's pod. Members must be the
-- owner's friends; anyone already in the pod may stay even if no longer a friend.
create or replace function public.save_pod(p_pod uuid, p_name text, p_members uuid[])
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me_with_profile();
  pod uuid := p_pod;
  m uuid;
begin
  if char_length(btrim(coalesce(p_name, ''))) not between 1 and 40 then
    raise exception 'bad_name' using errcode = 'P0001';
  end if;
  if coalesce(array_length(p_members, 1), 0) > 50 then
    raise exception 'too_many_members' using errcode = 'P0001';
  end if;
  if pod is null then
    if (select count(*) from public.pods where owner = uid) >= 30 then
      raise exception 'too_many_pods' using errcode = 'P0001';
    end if;
    insert into public.pods (owner, name) values (uid, btrim(p_name)) returning id into pod;
    insert into public.pod_members (pod_id, user_id) values (pod, uid);
  else
    update public.pods set name = btrim(p_name) where id = pod and owner = uid;
    if not found then
      raise exception 'not_yours' using errcode = 'P0001';
    end if;
  end if;
  foreach m in array coalesce(p_members, '{}') loop
    if m <> uid and not social_private.is_pod_member(pod, m) then
      if not social_private.are_friends(uid, m) then
        raise exception 'not_a_friend' using errcode = 'P0001';
      end if;
      insert into public.pod_members (pod_id, user_id) values (pod, m);
    end if;
  end loop;
  delete from public.pod_members where pod_id = pod and user_id <> uid and not (user_id = any(coalesce(p_members, '{}')));
  return pod;
end;
$$;

-- The owner deletes their pod; anyone else leaves it.
create or replace function public.leave_pod(p_pod uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me();
begin
  delete from public.pods where id = p_pod and owner = uid;
  if not found then
    delete from public.pod_members where pod_id = p_pod and user_id = uid;
  end if;
end;
$$;

-- ============================================================================================
-- API: sharing
-- ============================================================================================

-- Sets who can see one of the caller's decks or binders. Sharing with nobody removes the share.
-- A link keeps its token while it stays on; turning it off and on again makes a new one.
create or replace function public.set_library_share(p_kind text, p_item_id text, p_all_friends boolean, p_pod_ids uuid[], p_link boolean)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me_with_profile();
  pods uuid[];
  token text;
  s public.library_shares;
begin
  if p_kind not in ('deck', 'collection') then
    raise exception 'bad_kind' using errcode = 'P0001';
  end if;
  if not exists (select 1 from public.library_items where user_id = uid and kind = p_kind and id = p_item_id and not deleted) then
    raise exception 'no_such_item' using errcode = 'P0001';
  end if;
  select coalesce(array_agg(distinct p.id), '{}') into pods
  from unnest(coalesce(p_pod_ids, '{}')) as p(id)
  where social_private.is_pod_member(p.id, uid);
  if not coalesce(p_all_friends, false) and cardinality(pods) = 0 and not coalesce(p_link, false) then
    delete from public.library_shares where owner = uid and kind = p_kind and item_id = p_item_id;
    return null;
  end if;
  select link_token into token from public.library_shares where owner = uid and kind = p_kind and item_id = p_item_id;
  if coalesce(p_link, false) then
    token := coalesce(token, replace(gen_random_uuid()::text, '-', ''));
  else
    token := null;
  end if;
  insert into public.library_shares as ls (owner, kind, item_id, all_friends, pod_ids, link_token)
  values (uid, p_kind, p_item_id, coalesce(p_all_friends, false), pods, token)
  on conflict (owner, kind, item_id) do update
    set all_friends = excluded.all_friends, pod_ids = excluded.pod_ids, link_token = excluded.link_token, updated_at = now()
  returning * into s;
  return jsonb_build_object('kind', s.kind, 'item_id', s.item_id, 'all_friends', s.all_friends, 'pod_ids', s.pod_ids, 'link_token', s.link_token);
end;
$$;

-- A deck or binder shared with the caller: {owner (profile), kind, data, edited_ms}, or null.
create or replace function public.get_shared_item(p_owner uuid, p_kind text, p_item_id text)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me();
  s public.library_shares;
  li public.library_items;
begin
  select * into s from public.library_shares where owner = p_owner and kind = p_kind and item_id = p_item_id;
  if not found or not social_private.can_view_share(s, uid) then
    return null;
  end if;
  select * into li from public.library_items where user_id = p_owner and kind = p_kind and id = p_item_id and not deleted;
  if not found then
    return null;
  end if;
  return jsonb_build_object('owner', social_private.profile_json(p_owner), 'kind', li.kind, 'data', li.data, 'edited_ms', li.edited_ms);
end;
$$;

-- A deck or binder shared by link — anyone with the link, signed in or not.
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
  select * into li from public.library_items where user_id = s.owner and kind = s.kind and id = s.item_id and not deleted;
  if not found then
    return null;
  end if;
  return jsonb_build_object('owner', social_private.profile_json(s.owner), 'kind', li.kind, 'data', li.data, 'edited_ms', li.edited_ms);
end;
$$;

-- ============================================================================================
-- API: life counter matches
-- ============================================================================================

-- The host starts a match for a table of [p_seats]: {id, code}. The QR codes carry the code.
create or replace function public.start_match(p_seats integer)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me_with_profile();
  m public.matches;
begin
  if p_seats not between 1 and 10 then
    raise exception 'bad_seats' using errcode = 'P0001';
  end if;
  if (select count(*) from public.matches where host = uid and created_at > now() - interval '1 hour') >= 30 then
    raise exception 'too_many_matches' using errcode = 'P0001';
  end if;
  update public.matches set ended_at = now() where host = uid and ended_at is null;
  insert into public.matches (host, code, seats)
  values (uid, substr(replace(gen_random_uuid()::text, '-', ''), 1, 16), p_seats)
  returning * into m;
  return jsonb_build_object('id', m.id, 'code', m.code, 'seats', m.seats);
end;
$$;

-- A player scans a seat's QR code and sits there (moving from another seat of the same match).
-- Answers {match_id, seat, host (profile)}. A match can be joined for 12 hours.
create or replace function public.join_match(p_code text, p_seat integer)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me_with_profile();
  m public.matches;
  taken uuid;
begin
  select * into m from public.matches where code = p_code;
  if not found or m.ended_at is not null or m.created_at < now() - interval '12 hours' then
    raise exception 'match_over' using errcode = 'P0001';
  end if;
  if p_seat not between 1 and m.seats then
    raise exception 'bad_seat' using errcode = 'P0001';
  end if;
  select user_id into taken from public.match_players where match_id = m.id and seat = p_seat;
  if taken is not null and taken <> uid then
    raise exception 'seat_taken' using errcode = 'P0001';
  end if;
  delete from public.match_players where match_id = m.id and user_id = uid and seat <> p_seat;
  insert into public.match_players (match_id, seat, user_id) values (m.id, p_seat, uid)
  on conflict (match_id, seat) do nothing;
  return jsonb_build_object('match_id', m.id, 'seat', p_seat, 'host', social_private.profile_json(m.host));
end;
$$;

-- Who sits where: [{seat, profile}]. For the host and the players of the match.
create or replace function public.match_seats(p_match uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me();
begin
  if not exists (select 1 from public.matches m where m.id = p_match and m.host = uid)
     and not exists (select 1 from public.match_players mp where mp.match_id = p_match and mp.user_id = uid) then
    return '[]';
  end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object('seat', mp.seat, 'profile', social_private.profile_json(mp.user_id)) order by mp.seat)
    from public.match_players mp where mp.match_id = p_match
  ), '[]');
end;
$$;

-- The host frees a seat; a player can free their own.
create or replace function public.clear_match_seat(p_match uuid, p_seat integer)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me();
begin
  delete from public.match_players mp
  where mp.match_id = p_match and mp.seat = p_seat
    and (mp.user_id = uid or exists (select 1 from public.matches m where m.id = p_match and m.host = uid));
end;
$$;

-- The host ends the match (a new game, a different table): nobody can join it any more.
create or replace function public.end_match(p_match uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me();
begin
  update public.matches set ended_at = now() where id = p_match and host = uid and ended_at is null;
end;
$$;

-- ============================================================================================
-- API: trades
-- ============================================================================================

-- Proposes a trade to a friend: [p_want] from their binders, [p_give] from the caller's.
-- [p_reply_to]: an open trade they sent the caller, which this counters.
create or replace function public.propose_trade(p_to uuid, p_want jsonb, p_give jsonb, p_message text default null, p_reply_to uuid default null)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me_with_profile();
  want jsonb := social_private.clean_trade_cards(p_want);
  give jsonb := social_private.clean_trade_cards(p_give);
  new_id uuid;
begin
  if not social_private.are_friends(uid, p_to) then
    raise exception 'not_a_friend' using errcode = 'P0001';
  end if;
  if jsonb_array_length(want) + jsonb_array_length(give) = 0 then
    raise exception 'no_cards' using errcode = 'P0001';
  end if;
  if char_length(coalesce(p_message, '')) > 500 then
    raise exception 'message_too_long' using errcode = 'P0001';
  end if;
  if (select count(*) from public.trades where from_user = uid and status = 'open') >= 50 then
    raise exception 'too_many_trades' using errcode = 'P0001';
  end if;
  if p_reply_to is not null then
    update public.trades set status = 'countered', updated_at = now()
    where trades.id = p_reply_to and to_user = uid and from_user = p_to and status = 'open';
    if not found then
      raise exception 'trade_closed' using errcode = 'P0001';
    end if;
  end if;
  insert into public.trades (from_user, to_user, want, give, message, reply_to)
  values (uid, p_to, want, give, nullif(btrim(p_message), ''), p_reply_to)
  returning trades.id into new_id;
  return new_id;
end;
$$;

-- 'accept' / 'decline' (the recipient, while open) or 'cancel' (the sender, while open).
create or replace function public.respond_trade(p_trade uuid, p_action text, p_reply text default null)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me();
begin
  if char_length(coalesce(p_reply, '')) > 500 then
    raise exception 'message_too_long' using errcode = 'P0001';
  end if;
  if p_action in ('accept', 'decline') then
    update public.trades
    set status = case when p_action = 'accept' then 'accepted' else 'declined' end,
        reply = nullif(btrim(p_reply), ''), updated_at = now()
    where id = p_trade and to_user = uid and status = 'open';
  elsif p_action = 'cancel' then
    update public.trades set status = 'cancelled', updated_at = now()
    where id = p_trade and from_user = uid and status = 'open';
  else
    raise exception 'bad_action' using errcode = 'P0001';
  end if;
  if not found then
    raise exception 'trade_closed' using errcode = 'P0001';
  end if;
end;
$$;

-- The caller has moved the cards of an accepted trade in their own binders.
create or replace function public.mark_trade_applied(p_trade uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me();
begin
  update public.trades
  set from_applied = from_applied or from_user = uid,
      to_applied = to_applied or to_user = uid,
      updated_at = now()
  where id = p_trade and status = 'accepted' and (from_user = uid or to_user = uid);
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
    'save_profile(text, text, text)', 'username_available(text)', 'social_overview()', 'social_inbox()',
    'request_friend(text)', 'respond_friend(uuid, boolean)', 'remove_friend(uuid)',
    'save_pod(uuid, text, uuid[])', 'leave_pod(uuid)',
    'set_library_share(text, text, boolean, uuid[], boolean)', 'get_shared_item(uuid, text, text)',
    'start_match(integer)', 'join_match(text, integer)', 'match_seats(uuid)', 'clear_match_seat(uuid, integer)', 'end_match(uuid)',
    'propose_trade(uuid, jsonb, jsonb, text, uuid)', 'respond_trade(uuid, text, text)', 'mark_trade_applied(uuid)'
  ] loop
    execute format('revoke execute on function public.%s from public, anon', f);
    execute format('grant execute on function public.%s to authenticated', f);
  end loop;
end;
$$;

revoke execute on function public.get_shared_by_link(text) from public;
grant execute on function public.get_shared_by_link(text) to anon, authenticated;

-- ============================================================================================
-- Profile pictures
-- ============================================================================================

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('avatars', 'avatars', true, 2097152, array['image/jpeg', 'image/png', 'image/webp', 'image/gif'])
on conflict (id) do update
  set public = excluded.public, file_size_limit = excluded.file_size_limit, allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "avatars_select_own" on storage.objects;
create policy "avatars_select_own" on storage.objects for select to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text);

drop policy if exists "avatars_insert_own" on storage.objects;
create policy "avatars_insert_own" on storage.objects for insert to authenticated
  with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text);

drop policy if exists "avatars_update_own" on storage.objects;
create policy "avatars_update_own" on storage.objects for update to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text)
  with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text);

drop policy if exists "avatars_delete_own" on storage.objects;
create policy "avatars_delete_own" on storage.objects for delete to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text);
