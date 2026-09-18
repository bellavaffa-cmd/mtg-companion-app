-- Sharing with one friend, and sharing everything at once.
--
-- Until now a deck or binder was shared with all friends, some pods, and/or by link. This adds:
--   * library_shares.friend_ids — a deck or binder shared with particular friends;
--   * library_share_all — the owner's whole collection (every binder and wishlist) or all their
--     decks, shared with one friend or all friends, including ones made later.
-- Seeing any of it still needs the friendship: unfriending hides it without anything to clean up.

alter table public.library_shares add column if not exists friend_ids uuid[] not null default '{}';

create table if not exists public.library_share_all (
  owner uuid not null references public.profiles (user_id) on delete cascade,
  kind text not null check (kind in ('deck', 'collection')),
  -- The friend it's shared with; null for all friends.
  viewer uuid references public.profiles (user_id) on delete cascade,
  created_at timestamptz not null default now()
);
create unique index if not exists library_share_all_key
  on public.library_share_all (owner, kind, coalesce(viewer, '00000000-0000-0000-0000-000000000000'::uuid));
create index if not exists library_share_all_viewer on public.library_share_all (viewer);
alter table public.library_share_all enable row level security;
revoke all on public.library_share_all from anon, authenticated;

-- Whether [s] lets [viewer] see its deck or binder.
create or replace function social_private.can_view_share(s public.library_shares, viewer uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select s.owner = viewer
    or ((s.all_friends or viewer = any(s.friend_ids)) and social_private.are_friends(s.owner, viewer))
    or exists (
      select 1 from unnest(s.pod_ids) as p(id)
      where social_private.is_pod_member(p.id, viewer) and social_private.is_pod_member(p.id, s.owner)
    );
$$;

-- Whether [p_owner] shares every [p_kind] (their whole collection, or all decks) with [p_viewer].
create or replace function social_private.shares_all_with(p_owner uuid, p_kind text, p_viewer uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.library_share_all a
    where a.owner = p_owner and a.kind = p_kind and (a.viewer = p_viewer or a.viewer is null)
  ) and social_private.are_friends(p_owner, p_viewer);
$$;

-- Whether [p_viewer] may see one deck or binder, however it was shared.
create or replace function social_private.can_view_item(p_owner uuid, p_kind text, p_item_id text, p_viewer uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select p_owner = p_viewer
    or social_private.shares_all_with(p_owner, p_kind, p_viewer)
    or exists (
      select 1 from public.library_shares s
      where s.owner = p_owner and s.kind = p_kind and s.item_id = p_item_id and social_private.can_view_share(s, p_viewer)
    );
$$;

-- Everything the Friends screen shows, in one call. As before, plus: shared_with_me rows say
-- whether they come with a whole collection/all decks ("whole"), shared_all_with_me lists those,
-- and my_shares/my_share_all say what the caller shares with whom.
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
  all_with_me as (
    select distinct a.owner, a.kind
    from public.library_share_all a
    where (a.viewer = uid or a.viewer is null) and a.owner <> uid and social_private.are_friends(a.owner, uid)
  ),
  shared_keys as (
    select li.user_id as owner, li.kind, li.id as item_id, true as whole
    from all_with_me w
    join public.library_items li on li.user_id = w.owner and li.kind = w.kind and not li.deleted
    union all
    select s.owner, s.kind, s.item_id, false
    from public.library_shares s
    where s.owner <> uid and social_private.can_view_share(s, uid)
  ),
  shared as (
    select k.owner, k.kind, k.item_id, bool_or(k.whole) as whole,
           li.data ->> 'name' as name, li.edited_ms,
           case when k.kind = 'deck' then li.data -> 'commander' ->> 'imageUrl' end as cover,
           social_private.card_count(k.kind, li.data) as cards
    from shared_keys k
    join public.library_items li on li.user_id = k.owner and li.kind = k.kind and li.id = k.item_id and not li.deleted
    group by k.owner, k.kind, k.item_id, li.data, li.edited_ms
  ),
  my_shares as (
    select s.kind, s.item_id, s.all_friends, s.pod_ids, s.friend_ids, s.link_token from public.library_shares s where s.owner = uid
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
    union select owner from all_with_me
    union select from_user from my_trades
    union select to_user from my_trades
  )
  select jsonb_build_object(
    'me', social_private.profile_json(uid),
    'people', coalesce((select jsonb_object_agg(pe.user_id, social_private.profile_json(pe.user_id)) from people pe
                        where exists (select 1 from public.profiles pr where pr.user_id = pe.user_id)), '{}'),
    'friends', coalesce((select jsonb_agg(jsonb_build_object('user_id', f.user_id, 'status', f.status, 'incoming', f.incoming, 'since', f.since) order by f.since desc) from friends f), '[]'),
    'pods', coalesce((select jsonb_agg(jsonb_build_object('id', p.id, 'name', p.name, 'owner', p.owner, 'members', p.members) order by p.created_at) from my_pods p), '[]'),
    'shared_with_me', coalesce((select jsonb_agg(jsonb_build_object('owner', s.owner, 'kind', s.kind, 'item_id', s.item_id, 'name', s.name, 'cover', s.cover, 'cards', s.cards, 'edited_ms', s.edited_ms, 'whole', s.whole) order by s.edited_ms desc) from shared s), '[]'),
    'shared_all_with_me', coalesce((select jsonb_agg(jsonb_build_object('owner', w.owner, 'kind', w.kind)) from all_with_me w), '[]'),
    'my_shares', coalesce((select jsonb_agg(to_jsonb(s)) from my_shares s), '[]'),
    'my_share_all', coalesce((select jsonb_agg(jsonb_build_object('kind', a.kind, 'viewer', a.viewer)) from public.library_share_all a where a.owner = uid), '[]'),
    'trades', coalesce((select jsonb_agg(jsonb_build_object(
        'id', t.id, 'from_user', t.from_user, 'to_user', t.to_user, 'want', t.want, 'give', t.give,
        'message', t.message, 'reply', t.reply, 'status', t.status, 'reply_to', t.reply_to,
        'from_applied', t.from_applied, 'to_applied', t.to_applied, 'created_at', t.created_at, 'updated_at', t.updated_at
      ) order by t.updated_at desc) from my_trades t), '[]')
  ) into result;
  return result;
end;
$$;

-- Who can see one deck or binder: all friends, some pods, and/or anyone with the link. Friends it's
-- shared with one by one (set_item_friend_share) stay as they are. Everything off unshares it.
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
  if not s.all_friends and cardinality(s.pod_ids) = 0 and s.link_token is null and cardinality(s.friend_ids) = 0 then
    delete from public.library_shares where owner = uid and kind = p_kind and item_id = p_item_id;
    return null;
  end if;
  return jsonb_build_object('kind', s.kind, 'item_id', s.item_id, 'all_friends', s.all_friends, 'pod_ids', s.pod_ids, 'friend_ids', s.friend_ids, 'link_token', s.link_token);
end;
$$;

-- Shares one deck or binder with one friend ([p_on]), or stops. Returns the share, or null once
-- nothing shares it any more.
create or replace function public.set_item_friend_share(p_kind text, p_item_id text, p_friend uuid, p_on boolean)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me_with_profile();
  s public.library_shares;
begin
  if p_kind not in ('deck', 'collection') then
    raise exception 'bad_kind' using errcode = 'P0001';
  end if;
  if not exists (select 1 from public.library_items where user_id = uid and kind = p_kind and id = p_item_id and not deleted) then
    raise exception 'no_such_item' using errcode = 'P0001';
  end if;
  if p_on and not social_private.are_friends(uid, p_friend) then
    raise exception 'not_friends' using errcode = 'P0001';
  end if;
  if p_on then
    insert into public.library_shares as ls (owner, kind, item_id, friend_ids)
    values (uid, p_kind, p_item_id, array[p_friend])
    on conflict (owner, kind, item_id) do update
      set friend_ids = case when p_friend = any(ls.friend_ids) then ls.friend_ids else ls.friend_ids || p_friend end, updated_at = now()
    returning * into s;
  else
    update public.library_shares
      set friend_ids = array_remove(friend_ids, p_friend), updated_at = now()
      where owner = uid and kind = p_kind and item_id = p_item_id
      returning * into s;
    if not found then
      return null;
    end if;
    if not s.all_friends and cardinality(s.pod_ids) = 0 and s.link_token is null and cardinality(s.friend_ids) = 0 then
      delete from public.library_shares where owner = uid and kind = p_kind and item_id = p_item_id;
      return null;
    end if;
  end if;
  return jsonb_build_object('kind', s.kind, 'item_id', s.item_id, 'all_friends', s.all_friends, 'pod_ids', s.pod_ids, 'friend_ids', s.friend_ids, 'link_token', s.link_token);
end;
$$;

-- Shares the caller's whole collection ('collection': every binder and wishlist) or all their
-- decks ('deck') — including ones made later — with [p_viewer], or with all friends when that's
-- null. [p_on] false stops it. A friend it's newly shared with gets a notification.
create or replace function public.set_share_all(p_kind text, p_viewer uuid, p_on boolean)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me_with_profile();
  added integer;
begin
  if p_kind not in ('deck', 'collection') then
    raise exception 'bad_kind' using errcode = 'P0001';
  end if;
  if not p_on then
    delete from public.library_share_all where owner = uid and kind = p_kind and viewer is not distinct from p_viewer;
    return;
  end if;
  if p_viewer is not null and not social_private.are_friends(uid, p_viewer) then
    raise exception 'not_friends' using errcode = 'P0001';
  end if;
  insert into public.library_share_all (owner, kind, viewer)
  select uid, p_kind, p_viewer
  where not exists (select 1 from public.library_share_all where owner = uid and kind = p_kind and viewer is not distinct from p_viewer);
  get diagnostics added = row_count;
  if added > 0 and p_viewer is not null then
    perform social_private.notify(
      p_viewer, 'friends',
      social_private.display_name(uid) || ' shared ' || case when p_kind = 'deck' then 'all their decks' else 'their collection' end || ' with you',
      'Tap to have a look.',
      'friends', 'share-' || uid || '-' || p_kind
    );
  end if;
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
  li public.library_items;
begin
  if not social_private.can_view_item(p_owner, p_kind, p_item_id, uid) then
    return null;
  end if;
  select * into li from public.library_items where user_id = p_owner and kind = p_kind and id = p_item_id and not deleted;
  if not found then
    return null;
  end if;
  return jsonb_build_object('owner', social_private.profile_json(p_owner), 'kind', li.kind, 'data', li.data, 'edited_ms', li.edited_ms);
end;
$$;

-- Every binder [p_owner] shares with the caller, in full, for looking through their collection
-- as a whole: {owner (profile), whole (their whole collection is shared), binders: [data]}, or
-- null when nothing is.
create or replace function public.get_shared_collection(p_owner uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me();
  whole boolean := social_private.shares_all_with(p_owner, 'collection', uid);
  binders jsonb;
begin
  if p_owner = uid then
    return null;
  end if;
  select coalesce(jsonb_agg(li.data order by li.data ->> 'name'), '[]') into binders
  from public.library_items li
  where li.user_id = p_owner and li.kind = 'collection' and not li.deleted
    and (whole or exists (
      select 1 from public.library_shares s
      where s.owner = p_owner and s.kind = 'collection' and s.item_id = li.id and social_private.can_view_share(s, uid)
    ));
  if jsonb_array_length(binders) = 0 and not whole then
    return null;
  end if;
  return jsonb_build_object('owner', social_private.profile_json(p_owner), 'whole', whole, 'binders', binders);
end;
$$;

revoke all on function public.set_item_friend_share(text, text, uuid, boolean) from public, anon;
revoke all on function public.set_share_all(text, uuid, boolean) from public, anon;
revoke all on function public.get_shared_collection(uuid) from public, anon;
grant execute on function public.set_item_friend_share(text, text, uuid, boolean) to authenticated;
grant execute on function public.set_share_all(text, uuid, boolean) to authenticated;
grant execute on function public.get_shared_collection(uuid) to authenticated;
revoke execute on all functions in schema social_private from public, anon, authenticated;
