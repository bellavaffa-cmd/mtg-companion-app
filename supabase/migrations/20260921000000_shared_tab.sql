-- The Collection tab's Shared page: who shares what, "who has a card?", and wishlist matches.
--
--   * social_overview: shared binders now carry a cover (their first card's art), like decks do.
--   * search_shared_cards: finds a card by name across every binder and deck shared with the caller.
--   * wishlist_matches: cards in friends' shared binders that are on the caller's wishlists.

-- Every deck and binder of someone else that [viewer] may see, however it was shared: from
-- friends and pod-mates only, so the whole library table is never scanned.
create or replace function social_private.visible_items(viewer uuid)
returns table (owner uuid, kind text, id text, data jsonb, edited_ms bigint)
language sql
stable
security definer
set search_path = ''
as $$
  with people as (
    select case when f.requester = viewer then f.addressee else f.requester end as user_id
    from public.friendships f
    where (f.requester = viewer or f.addressee = viewer) and f.status = 'accepted'
    union
    select m2.user_id
    from public.pod_members m1
    join public.pod_members m2 on m2.pod_id = m1.pod_id
    where m1.user_id = viewer and m2.user_id <> viewer
  )
  select li.user_id, li.kind, li.id, li.data, li.edited_ms
  from people p
  join public.library_items li on li.user_id = p.user_id and not li.deleted
  where social_private.can_view_item(li.user_id, li.kind, li.id, viewer);
$$;

-- The cards of a deck (commanders included) or binder, as one JSON array.
create or replace function social_private.item_cards(kind text, data jsonb)
returns jsonb
language sql
immutable
set search_path = ''
as $$
  select case when kind = 'deck' then
      coalesce(data -> 'cards', '[]')
      || case when jsonb_typeof(data -> 'commander') = 'object' then jsonb_build_array(data -> 'commander') else '[]' end
      || case when jsonb_typeof(data -> 'partnerCommander') = 'object' then jsonb_build_array(data -> 'partnerCommander') else '[]' end
    else coalesce(data -> 'entries', '[]')
  end;
$$;

-- As before, with a cover for shared binders too (their first card).
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
           case when k.kind = 'deck' then li.data -> 'commander' ->> 'imageUrl' else li.data -> 'entries' -> 0 ->> 'imageUrl' end as cover,
           social_private.card_count(k.kind, li.data) as cards,
           li.data ->> 'type' as type
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
    'shared_with_me', coalesce((select jsonb_agg(jsonb_build_object('owner', s.owner, 'kind', s.kind, 'item_id', s.item_id, 'name', s.name, 'cover', s.cover, 'cards', s.cards, 'edited_ms', s.edited_ms, 'whole', s.whole, 'type', s.type) order by s.edited_ms desc) from shared s), '[]'),
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

-- "Who has a card?": every copy of a card whose name contains [p_query] in the binders and decks
-- shared with the caller (wishlists left out — wanting a card isn't having it). At most 200 rows:
-- [{owner, kind, item_id, item_name, scryfall_id, name, image_url, quantity, foil_quantity}].
create or replace function public.search_shared_cards(p_query text)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me();
  q text := btrim(coalesce(p_query, ''));
  pattern text;
  result jsonb;
begin
  if char_length(q) < 2 then
    return '[]'::jsonb;
  end if;
  pattern := '%' || replace(replace(replace(left(q, 100), '\', '\\'), '%', '\%'), '_', '\_') || '%';
  select coalesce(jsonb_agg(h.row order by h.row ->> 'name', h.row ->> 'item_name'), '[]') into result
  from (
    select jsonb_build_object(
      'owner', v.owner, 'kind', v.kind, 'item_id', v.id, 'item_name', v.data ->> 'name',
      'scryfall_id', e ->> 'scryfallId', 'name', e ->> 'name', 'image_url', e ->> 'imageUrl',
      'quantity', coalesce((e ->> 'quantity')::int, 0), 'foil_quantity', coalesce((e ->> 'foilQuantity')::int, 0)
    ) as row
    from social_private.visible_items(uid) v,
    lateral jsonb_array_elements(social_private.item_cards(v.kind, v.data)) e
    where (v.kind = 'deck' or coalesce(v.data ->> 'type', 'OWNED') <> 'WISHLIST')
      and (e ->> 'name') ilike pattern
    limit 200
  ) h;
  return result;
end;
$$;

-- Cards in friends' shared binders (not their wishlists or decks) that are on one of the caller's
-- own wishlists, matched by name so any printing counts:
-- [{owner, item_id, item_name, scryfall_id, name, image_url, quantity, foil_quantity}].
create or replace function public.wishlist_matches()
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
  with wants as (
    select distinct lower(e ->> 'name') as name
    from public.library_items li,
    lateral jsonb_array_elements(coalesce(li.data -> 'entries', '[]')) e
    where li.user_id = uid and li.kind = 'collection' and not li.deleted and li.data ->> 'type' = 'WISHLIST'
  )
  select coalesce(jsonb_agg(jsonb_build_object(
      'owner', v.owner, 'item_id', v.id, 'item_name', v.data ->> 'name',
      'scryfall_id', e ->> 'scryfallId', 'name', e ->> 'name', 'image_url', e ->> 'imageUrl',
      'quantity', coalesce((e ->> 'quantity')::int, 0), 'foil_quantity', coalesce((e ->> 'foilQuantity')::int, 0)
    ) order by e ->> 'name'), '[]') into result
  from social_private.visible_items(uid) v,
  lateral jsonb_array_elements(coalesce(v.data -> 'entries', '[]')) e
  where exists (select 1 from wants)
    and v.kind = 'collection' and coalesce(v.data ->> 'type', 'OWNED') <> 'WISHLIST'
    and lower(e ->> 'name') in (select name from wants);
  return result;
end;
$$;

revoke all on function public.search_shared_cards(text) from public, anon;
revoke all on function public.wishlist_matches() from public, anon;
grant execute on function public.search_shared_cards(text) to authenticated;
grant execute on function public.wishlist_matches() to authenticated;
revoke execute on all functions in schema social_private from public, anon, authenticated;
