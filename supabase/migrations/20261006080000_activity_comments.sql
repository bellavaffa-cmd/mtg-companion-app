-- Manabind — a richer friends' Activity feed, what each person lets it show, and comments on shared
-- decks.
--
-- NOT applied automatically. The owner reviews it and runs it by hand (Supabase SQL editor, or
-- `npx supabase db push`). Safe to run more than once: tables and indexes are "if not exists",
-- functions "create or replace". Nothing at the top level deletes or drops anything; the only
-- DELETE statements are inside delete_deck_comment (one comment, by its author or the deck's owner)
-- and nothing here drops a table, column or constraint.
--
-- As everywhere else, the tables have row-level security on and no policies: all access goes through
-- the functions below, which run as their owner and check the caller (auth.uid()) themselves —
-- friendship, what's shared with them, and blocks either way.
--
-- The apps check for this migration with activity_comments_version() and, until it's there, keep
-- the Activity tab on activity_feed (20261006020000_social_more.sql) and hide comments and the
-- Privacy switches (web: src/social/activity.ts; Android: data/social/ActivityComments.kt).
--
-- 1. What friends see of you in Activity (public.activity_prefs, Settings › Privacy):
--      decks      a deck newly shared, or one shared with them changed        on unless turned off
--      for_trade  cards newly marked for trade                                on unless turned off
--      selling    that you're selling cards (your To sell list), and how many  OFF unless turned on
--      leagues    your name in pod league news (who leads, who won)          on unless turned off
--    Selling is off by default: a To sell list is about money and isn't shared anywhere else, so
--    showing it is the person's choice. The others only announce things already shared with friends
--    (decks, cards for trade, a pod's league table). The switches change only the Activity feed:
--    what's shared stays shared. Turning one off hides past items as well as new ones.
--    activity_feed (the older apps' feed) now honours them too. A friend's selling item is dated
--    when they turned it on, and again (at most every 6 hours) when a synced binder puts more copies
--    on their To sell list — a trigger on library_items, so no app has to remember to say so.
--
-- 2. friends_activity(p_before, p_limit): the feed the apps read now. As activity_feed, from friends
--    only (pods' games and league news come from pods), plus:
--      shared/for_trade  new_deck (a deck made in the 14 days before it was shared), and "wanted":
--                        which of the cards marked for trade are on the caller's wishlists;
--      selling           a friend with cards to sell: count, and which are on the caller's wishlists;
--      league            a pod's running season (its latest game) or one just ended (its champion);
--                        the apps work the table out from the pod's games, as the league screen does;
--      comment           a comment on one of the caller's decks, or a reply to one of theirs.
--    Each kind is read newest first with the same limit, from the indexes it has, so a page costs a
--    handful of index scans; the caller's wishlist names are read once.
--
-- 3. Deck comments (public.deck_comments): anyone a deck is shared with who is the owner's friend
--    (and not blocked either way) comments on it, or on one of its cards, and replies one level deep.
--    The owner is notified (and a comment's author, of a reply). The owner hides or deletes any
--    comment on their deck; an author deletes their own. A report about a comment goes through
--    report_user with item_kind 'deck' and item_id 'comment:<id>'. Limits: 1,000 characters, 10
--    comments a minute and 200 a day per person, 1,000 per deck.
--
-- Deleting an account (20261006040000_delete_account.sql) needs no change: both tables cascade from
-- public.profiles, which cascades from auth.users.

-- ============================================================================================
-- Tables
-- ============================================================================================

create table if not exists public.activity_prefs (
  user_id uuid primary key references public.profiles (user_id) on delete cascade,
  decks boolean not null default true,
  for_trade boolean not null default true,
  selling boolean not null default false,
  leagues boolean not null default true,
  -- When more cards went on the To sell list (library_items_selling) or selling was turned on: the
  -- feed item's time.
  selling_at timestamptz,
  updated_at timestamptz not null default now()
);
create index if not exists activity_prefs_selling_idx on public.activity_prefs (selling_at desc) where selling;
alter table public.activity_prefs enable row level security;
revoke all on public.activity_prefs from anon, authenticated;

create table if not exists public.deck_comments (
  id uuid primary key default gen_random_uuid(),
  -- The deck: its owner's library item (kind 'deck').
  owner uuid not null references public.profiles (user_id) on delete cascade,
  deck_id text not null check (char_length(deck_id) between 1 and 100),
  author uuid not null references public.profiles (user_id) on delete cascade,
  -- A reply's comment (top-level only: replies go one level deep).
  parent uuid references public.deck_comments (id) on delete cascade,
  body text not null check (char_length(body) between 1 and 1000),
  -- The card it's about ("on Gray Merchant of Asphodel"), in the deck or suggested for it.
  card_name text check (card_name is null or char_length(card_name) between 1 and 200),
  card_image text check (card_image is null or char_length(card_image) <= 500),
  hidden boolean not null default false,
  created_at timestamptz not null default now()
);
create index if not exists deck_comments_deck_idx on public.deck_comments (owner, deck_id, created_at);
create index if not exists deck_comments_owner_idx on public.deck_comments (owner, created_at desc);
create index if not exists deck_comments_author_idx on public.deck_comments (author, created_at desc);
create index if not exists deck_comments_parent_idx on public.deck_comments (parent);
alter table public.deck_comments enable row level security;
revoke all on public.deck_comments from anon, authenticated;

-- ============================================================================================
-- Helpers (private)
-- ============================================================================================

-- Whether [u] lets friends' Activity show [what] ('decks', 'for_trade', 'selling', 'leagues').
create or replace function social_private.activity_pref(u uuid, what text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce((
    select case what when 'decks' then p.decks when 'for_trade' then p.for_trade
                     when 'selling' then p.selling when 'leagues' then p.leagues end
    from public.activity_prefs p where p.user_id = u
  ), what <> 'selling');
$$;

-- How many of a binder entry's copies are to sell ("forSale"), never more than it holds.
create or replace function social_private.for_sale_count(e jsonb)
returns integer
language sql
immutable
set search_path = ''
as $$
  select case when jsonb_typeof(e -> 'forSale') = 'number' then
    greatest(0, least(
      floor(greatest(least((e ->> 'forSale')::numeric, 9999), 0))::int,
      (case when jsonb_typeof(e -> 'quantity') = 'number' then floor(greatest(least((e ->> 'quantity')::numeric, 9999), 0))::int else 0 end)
      + (case when jsonb_typeof(e -> 'foilQuantity') = 'number' then floor(greatest(least((e ->> 'foilQuantity')::numeric, 9999), 0))::int else 0 end)
    ))
  else 0 end;
$$;

-- The card names (lower case) on [u]'s wishlists.
create or replace function social_private.wishlist_names(u uuid)
returns table (name text)
language sql
stable
security definer
set search_path = ''
as $$
  select distinct lower(e ->> 'name')
  from public.library_items li,
  lateral jsonb_array_elements(case when jsonb_typeof(li.data -> 'entries') = 'array' then li.data -> 'entries' else '[]'::jsonb end) e
  where li.user_id = u and li.kind = 'collection' and not li.deleted and li.data ->> 'type' = 'WISHLIST'
    and coalesce(e ->> 'name', '') <> '';
$$;

-- The members of [pod] whose names league news leaves out for [viewer]: those who turned league
-- results off, and anyone blocked either way.
create or replace function social_private.league_quiet(pod uuid, viewer uuid)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce(jsonb_agg(m.user_id), '[]'::jsonb)
  from public.pod_members m
  where m.pod_id = pod and m.user_id <> viewer
    and (not social_private.activity_pref(m.user_id, 'leagues') or social_private.blocked_either(viewer, m.user_id));
$$;

-- Whether [viewer] may read the comments on [owner]'s deck [deck]: it exists and they may see it.
create or replace function social_private.can_read_deck(owner uuid, deck text, viewer uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select viewer is not null
    and exists (select 1 from public.library_items li where li.user_id = owner and li.kind = 'deck' and li.id = deck and not li.deleted)
    and social_private.can_view_item(owner, 'deck', deck, viewer);
$$;

-- Whether [viewer] may comment on it: the owner, or a friend it's shared with (not blocked).
create or replace function social_private.can_comment_deck(owner uuid, deck text, viewer uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select social_private.can_read_deck(owner, deck, viewer)
    and (viewer = owner or (social_private.are_friends(owner, viewer) and not social_private.blocked_either(owner, viewer)));
$$;

-- A comment as the apps read it.
create or replace function social_private.comment_json(c public.deck_comments, viewer uuid)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select jsonb_build_object(
    'id', c.id, 'parent', c.parent, 'author', social_private.profile_json(c.author),
    'body', c.body, 'card_name', c.card_name, 'card_image', c.card_image, 'hidden', c.hidden,
    'created_at', social_private.ms(c.created_at), 'mine', c.author = viewer);
$$;

-- ============================================================================================
-- API: is this migration there?
-- ============================================================================================

create or replace function public.activity_comments_version()
returns integer
language sql
immutable
set search_path = ''
as $$
  select 1;
$$;

-- ============================================================================================
-- API: what friends' Activity shows of the caller (Settings › Privacy)
-- ============================================================================================

-- {decks, for_trade, selling, leagues}, defaults filled in.
create or replace function public.activity_prefs()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me();
begin
  return jsonb_build_object(
    'decks', social_private.activity_pref(uid, 'decks'),
    'for_trade', social_private.activity_pref(uid, 'for_trade'),
    'selling', social_private.activity_pref(uid, 'selling'),
    'leagues', social_private.activity_pref(uid, 'leagues'));
end;
$$;

-- Saves all four. Turning selling on dates the feed item now.
create or replace function public.set_activity_prefs(p_decks boolean, p_for_trade boolean, p_selling boolean, p_leagues boolean)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me_with_profile();
begin
  if p_decks is null or p_for_trade is null or p_selling is null or p_leagues is null then
    raise exception 'bad_prefs' using errcode = 'P0001';
  end if;
  insert into public.activity_prefs as a (user_id, decks, for_trade, selling, leagues, selling_at, updated_at)
  values (uid, p_decks, p_for_trade, p_selling, p_leagues, case when p_selling then now() end, now())
  on conflict (user_id) do update
    set decks = excluded.decks, for_trade = excluded.for_trade, selling = excluded.selling, leagues = excluded.leagues,
        selling_at = case when excluded.selling and not a.selling then now() else a.selling_at end,
        updated_at = now();
end;
$$;

-- How many copies a binder's data puts on the To sell list.
create or replace function social_private.sale_total(data jsonb)
returns integer
language sql
immutable
set search_path = ''
as $$
  select case when coalesce(data ->> 'type', 'OWNED') = 'WISHLIST' or jsonb_typeof(data -> 'entries') <> 'array' then 0
    else coalesce((select sum(social_private.for_sale_count(e)) from jsonb_array_elements(data -> 'entries') e), 0)::int end;
$$;

-- When a synced binder puts more copies on the To sell list, friends' feeds show it again (at most
-- every 6 hours), for someone who turned selling on. Never fails the sync itself.
create or replace function social_private.on_selling_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if new.kind <> 'collection' or new.deleted or new.data is null then
    return null;
  end if;
  if not exists (select 1 from public.activity_prefs a where a.user_id = new.user_id and a.selling
                 and (a.selling_at is null or a.selling_at < now() - interval '6 hours')) then
    return null;
  end if;
  if social_private.sale_total(new.data) > (case when tg_op = 'UPDATE' and old.data is not null and not old.deleted then social_private.sale_total(old.data) else 0 end) then
    update public.activity_prefs set selling_at = now() where user_id = new.user_id;
  end if;
  return null;
exception when others then
  raise warning 'selling not noted: %', sqlerrm;
  return null;
end;
$$;

create or replace trigger library_items_selling
  after insert or update of data, deleted on public.library_items
  for each row execute function social_private.on_selling_change();

-- [p_owner]'s To sell list, for a friend while they let Activity show it (or for themself); null
-- otherwise, or across a block. Up to 500, the caller's wishlist cards first:
-- [{item_id, item_name, scryfall_id, name, image_url, for_sale, quantity, foil_quantity, condition, wanted}].
create or replace function public.selling_list(p_owner uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me();
begin
  if p_owner is null or (p_owner <> uid and (social_private.blocked_either(uid, p_owner)
      or not social_private.are_friends(uid, p_owner) or not social_private.activity_pref(p_owner, 'selling'))) then
    return null;
  end if;
  return coalesce((
    select jsonb_agg(h.row order by (h.row ->> 'wanted')::boolean desc, h.row ->> 'name', h.row ->> 'item_name')
    from (
      select jsonb_strip_nulls(jsonb_build_object(
        'item_id', li.id, 'item_name', li.data ->> 'name',
        'scryfall_id', e ->> 'scryfallId', 'name', e ->> 'name', 'image_url', e ->> 'imageUrl',
        'for_sale', social_private.for_sale_count(e),
        'quantity', case when jsonb_typeof(e -> 'quantity') = 'number' then (e ->> 'quantity')::numeric::int else 0 end,
        'foil_quantity', case when jsonb_typeof(e -> 'foilQuantity') = 'number' then (e ->> 'foilQuantity')::numeric::int else 0 end,
        'condition', e ->> 'condition',
        'wanted', lower(e ->> 'name') in (select w.name from social_private.wishlist_names(uid) w)
      )) as row
      from public.library_items li,
      lateral jsonb_array_elements(case when jsonb_typeof(li.data -> 'entries') = 'array' then li.data -> 'entries' else '[]'::jsonb end) e
      where li.user_id = p_owner and li.kind = 'collection' and not li.deleted
        and coalesce(li.data ->> 'type', 'OWNED') <> 'WISHLIST'
        and social_private.for_sale_count(e) > 0
      limit 500
    ) h
  ), '[]'::jsonb);
end;
$$;

-- ============================================================================================
-- API: the activity feeds
-- ============================================================================================

-- As in 20261006020000_social_more.sql, for apps that don't read friends_activity yet, now leaving
-- out what each person turned off in Settings › Privacy (decks; cards for trade).
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
      and (e.item_kind <> 'deck' or social_private.activity_pref(e.actor, 'decks'))
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
      and social_private.activity_pref(e.actor, 'for_trade')
    order by e.created_at desc
    limit lim
  ),
  decks as (
    select 'deck_updated'::text, v.owner, to_timestamp(v.edited_ms / 1000.0), 'deck'::text, v.id, v.data ->> 'name',
           v.data -> 'commander' ->> 'imageUrl', null::uuid, null::jsonb
    from social_private.visible_items(uid) v
    where v.kind = 'deck' and v.edited_ms < social_private.ms(before_at) and v.edited_ms > social_private.ms(since_at)
      and social_private.activity_pref(v.owner, 'decks')
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

-- Friends' activity, newest first: [p_limit] (up to 50) items before [p_before] (ms; null: now),
-- from the last 60 days. Only what the caller may see, only what each person lets Activity show,
-- nobody blocked either way. Each item:
--   {kind, actor (profile; absent for league), at, item_kind, item_id, name, cover, pod_id, pod_name, ...}
-- and by kind:
--   shared       new_deck (deck made in the 14 days before)            — friends only
--   deck_updated                                                       — friends only
--   pod_game     format, winner, players                               — the caller's pods
--   for_trade    count, cards [{name, imageUrl}], wanted [names]       — friends only
--   selling      count (copies to sell), wanted_count, wanted [names]  — friends who turned it on
--   league       season_id, name (the season), ended, champion, champion_ids, quiet [user ids],
--                starts_on, ends_on, max_nights                        — the caller's pods
--   comment      item_owner (the deck's owner), comment_id, body, card_name, reply, on_mine
create or replace function public.friends_activity(p_before bigint default null, p_limit integer default 30)
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
  wants text[];
  result jsonb;
begin
  select coalesce(array_agg(w.name), '{}') into wants from social_private.wishlist_names(uid) w;
  with
  friends as (
    select case when f.requester = uid then f.addressee else f.requester end as user_id
    from public.friendships f
    where (f.requester = uid or f.addressee = uid) and f.status = 'accepted'
      and not social_private.blocked_either(f.requester, f.addressee)
  ),
  shared as (
    select 'shared'::text as kind, e.actor, e.created_at as at, e.item_kind, e.item_id,
           li.data ->> 'name' as name,
           case when e.item_kind = 'deck' then li.data -> 'commander' ->> 'imageUrl' else li.data -> 'entries' -> 0 ->> 'imageUrl' end as cover,
           null::uuid as pod_id,
           case when e.item_kind = 'deck' and e.item_id is not null then jsonb_build_object('new_deck',
             jsonb_typeof(li.data -> 'createdAt') = 'number'
             and (li.data ->> 'createdAt')::numeric > social_private.ms(e.created_at - interval '14 days')) end as detail
    from public.social_events e
    join friends p on p.user_id = e.actor
    left join public.library_items li on li.user_id = e.actor and li.kind = e.item_kind and li.id = e.item_id and not li.deleted
    where e.kind = 'shared' and e.created_at < before_at and e.created_at > since_at
      and (e.item_kind <> 'deck' or social_private.activity_pref(e.actor, 'decks'))
      and case when e.item_id is null then social_private.shares_all_with(e.actor, e.item_kind, uid)
               else li.id is not null and social_private.can_view_item(e.actor, e.item_kind, e.item_id, uid) end
    order by e.created_at desc
    limit lim
  ),
  for_trade as (
    select 'for_trade'::text, e.actor, e.created_at, null::text, null::text, null::text, null::text, null::uuid,
           e.detail || jsonb_build_object('wanted', (
             select coalesce(jsonb_agg(c ->> 'name'), '[]'::jsonb)
             from jsonb_array_elements(case when jsonb_typeof(e.detail -> 'cards') = 'array' then e.detail -> 'cards' else '[]'::jsonb end) c
             where lower(c ->> 'name') = any(wants)))
    from public.social_events e
    join friends f on f.user_id = e.actor
    where e.kind = 'for_trade' and e.created_at < before_at and e.created_at > since_at
      and social_private.activity_pref(e.actor, 'for_trade')
    order by e.created_at desc
    limit lim
  ),
  decks as (
    select 'deck_updated'::text, v.owner, to_timestamp(v.edited_ms / 1000.0), 'deck'::text, v.id, v.data ->> 'name',
           v.data -> 'commander' ->> 'imageUrl', null::uuid, null::jsonb
    from social_private.visible_items(uid) v
    join friends f on f.user_id = v.owner
    where v.kind = 'deck' and v.edited_ms < social_private.ms(before_at) and v.edited_ms > social_private.ms(since_at)
      and social_private.activity_pref(v.owner, 'decks')
    order by v.edited_ms desc
    limit lim
  ),
  games as (
    select 'pod_game'::text, g.recorded_by, g.created_at, null::text, g.id::text, null::text, null::text, g.pod_id,
           jsonb_build_object('format', nullif(g.format, ''), 'players', jsonb_array_length(g.players),
             'winner', (select p ->> 'name' from jsonb_array_elements(g.players) p where p ->> 'result' = 'WIN' limit 1)) as detail
    from public.pod_games g
    join public.pod_members m on m.pod_id = g.pod_id and m.user_id = uid
    where g.recorded_by <> uid and g.created_at < before_at and g.created_at > since_at
      and not social_private.blocked_either(uid, g.recorded_by)
    order by g.created_at desc
    limit lim
  ),
  sellers as (
    select a.user_id, a.selling_at
    from public.activity_prefs a
    join friends f on f.user_id = a.user_id
    where a.selling and a.selling_at < before_at and a.selling_at > since_at
    order by a.selling_at desc
    limit lim
  ),
  selling as (
    select 'selling'::text, s.user_id, s.selling_at, null::text, null::text, null::text, t.cover, null::uuid,
           jsonb_build_object('count', t.n, 'wanted_count', coalesce(cardinality(t.wanted), 0), 'wanted', to_jsonb(coalesce(t.wanted[1:6], '{}'::text[])))
    from sellers s
    cross join lateral (
      select sum(social_private.for_sale_count(e))::int as n,
             array_agg(distinct e ->> 'name') filter (where lower(e ->> 'name') = any(wants)) as wanted,
             (array_agg(e ->> 'imageUrl') filter (where e ->> 'imageUrl' is not null))[1] as cover
      from public.library_items li,
      lateral jsonb_array_elements(case when jsonb_typeof(li.data -> 'entries') = 'array' then li.data -> 'entries' else '[]'::jsonb end) e
      where li.user_id = s.user_id and li.kind = 'collection' and not li.deleted
        and coalesce(li.data ->> 'type', 'OWNED') <> 'WISHLIST'
        and social_private.for_sale_count(e) > 0
    ) t
    where coalesce(t.n, 0) > 0
  ),
  league_ended as (
    select 'league'::text, null::uuid, s.ended_at, null::text, s.id::text, s.name, null::text, s.pod_id,
           jsonb_build_object('season_id', s.id, 'ended', true, 'champion', s.champion,
             'champion_ids', (select coalesce(jsonb_agg(x ->> 'userId'), '[]'::jsonb)
                              from jsonb_array_elements(case when jsonb_typeof(s.standings) = 'array' then s.standings else '[]'::jsonb end) x
                              where x ->> 'rank' = '1' and coalesce(x ->> 'userId', '') <> ''),
             'quiet', social_private.league_quiet(s.pod_id, uid))
    from public.pod_seasons s
    join public.pod_members m on m.pod_id = s.pod_id and m.user_id = uid
    where s.ended_at is not null and s.ended_at < before_at and s.ended_at > since_at
    order by s.ended_at desc
    limit lim
  ),
  league_running as (
    select 'league'::text, null::uuid, g.at, null::text, s.id::text, s.name, null::text, s.pod_id,
           jsonb_build_object('season_id', s.id, 'ended', false,
             'starts_on', to_char(s.starts_on, 'YYYY-MM-DD'), 'ends_on', to_char(s.ends_on, 'YYYY-MM-DD'),
             'max_nights', s.max_nights, 'quiet', social_private.league_quiet(s.pod_id, uid))
    from public.pod_seasons s
    join public.pod_members m on m.pod_id = s.pod_id and m.user_id = uid
    cross join lateral (
      select max(pg.created_at) as at from public.pod_games pg
      where pg.pod_id = s.pod_id and pg.played_at >= (s.starts_on - 1)::timestamptz and pg.created_at < before_at
    ) g
    where s.ended_at is null and g.at is not null and g.at > since_at
    order by g.at desc
    limit lim
  ),
  on_mine as (
    select 'comment'::text, c.author, c.created_at, 'deck'::text, c.deck_id, li.data ->> 'name', li.data -> 'commander' ->> 'imageUrl', null::uuid,
           jsonb_build_object('item_owner', c.owner, 'comment_id', c.id, 'body', left(c.body, 140), 'card_name', c.card_name,
             'reply', c.parent is not null, 'on_mine', true)
    from public.deck_comments c
    join public.library_items li on li.user_id = c.owner and li.kind = 'deck' and li.id = c.deck_id and not li.deleted
    where c.owner = uid and c.author <> uid and not c.hidden and c.created_at < before_at and c.created_at > since_at
      and not social_private.blocked_either(uid, c.author)
    order by c.created_at desc
    limit lim
  ),
  replies as (
    select 'comment'::text, c.author, c.created_at, 'deck'::text, c.deck_id, li.data ->> 'name', li.data -> 'commander' ->> 'imageUrl', null::uuid,
           jsonb_build_object('item_owner', c.owner, 'comment_id', c.id, 'body', left(c.body, 140), 'card_name', c.card_name,
             'reply', true, 'on_mine', false)
    from public.deck_comments p
    join public.deck_comments c on c.parent = p.id
    join public.library_items li on li.user_id = c.owner and li.kind = 'deck' and li.id = c.deck_id and not li.deleted
    where p.author = uid and c.owner <> uid and c.author <> uid and not c.hidden
      and c.created_at < before_at and c.created_at > since_at
      and not social_private.blocked_either(uid, c.author)
      and social_private.can_view_item(c.owner, 'deck', c.deck_id, uid)
    order by c.created_at desc
    limit lim
  ),
  merged as (
    select * from shared
    union all select * from for_trade
    union all select * from decks
    union all select * from games
    union all select * from selling
    union all select * from league_ended
    union all select * from league_running
    union all select * from on_mine
    union all select * from replies
    order by at desc
    limit lim
  )
  select coalesce(jsonb_agg(jsonb_strip_nulls(jsonb_build_object(
      'kind', x.kind,
      'actor', case when x.actor is not null then social_private.profile_json(x.actor) end,
      'at', social_private.ms(x.at),
      'item_kind', x.item_kind,
      'item_id', x.item_id,
      'name', x.name,
      'cover', x.cover,
      'pod_id', x.pod_id,
      'pod_name', (select pd.name from public.pods pd where pd.id = x.pod_id)
    ) || coalesce(x.detail, '{}'::jsonb)) order by x.at desc), '[]'::jsonb)
  into result
  from merged x
  where x.actor is null or exists (select 1 from public.profiles pr where pr.user_id = x.actor);
  return result;
end;
$$;

-- ============================================================================================
-- API: comments on shared decks
-- ============================================================================================

-- The comments on [p_owner]'s deck [p_deck], oldest first (up to 1,000), for the owner and anyone it's
-- shared with: {is_owner, can_comment, comments: [{id, parent, author, body, card_name, card_image,
-- hidden, created_at, mine}]}. Hidden comments only reach the owner and their author; nobody's
-- comments reach someone they're blocked with (either way), or stay once the owner blocked them.
-- Null when the caller can't see the deck.
create or replace function public.deck_comments(p_owner uuid, p_deck text)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me();
begin
  if p_owner is null or not social_private.can_read_deck(p_owner, p_deck, uid) then
    return null;
  end if;
  return jsonb_build_object(
    'is_owner', p_owner = uid,
    'can_comment', social_private.can_comment_deck(p_owner, p_deck, uid),
    'comments', coalesce((
      with shown as (
        select c.* from public.deck_comments c
        where c.owner = p_owner and c.deck_id = p_deck
          and (not c.hidden or uid = p_owner or c.author = uid)
          and (c.author = uid or not social_private.blocked_either(uid, c.author))
          and (c.author = p_owner or not social_private.blocked_either(p_owner, c.author))
          and exists (select 1 from public.profiles pr where pr.user_id = c.author)
        order by c.created_at
        limit 1000
      )
      select jsonb_agg(social_private.comment_json(s, uid) order by s.created_at)
      from shown s
      where s.parent is null or s.parent in (select t.id from shown t)
    ), '[]'::jsonb));
end;
$$;

-- Comments on [p_owner]'s deck [p_deck] — or replies to [p_parent], a top-level comment on it — about
-- the card [p_card_name] when given. Notifies the owner, and a reply's comment's author. Answers the
-- comment.
create or replace function public.post_deck_comment(
  p_owner uuid, p_deck text, p_body text, p_parent uuid default null, p_card_name text default null, p_card_image text default null)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me_with_profile();
  txt text := btrim(coalesce(p_body, ''));
  card text := nullif(btrim(coalesce(p_card_name, '')), '');
  par public.deck_comments;
  c public.deck_comments;
  deck_name text;
  who text;
begin
  if char_length(txt) = 0 then
    raise exception 'empty_comment' using errcode = 'P0001';
  end if;
  if char_length(txt) > 1000 then
    raise exception 'comment_too_long' using errcode = 'P0001';
  end if;
  if char_length(coalesce(card, '')) > 200 then
    raise exception 'bad_comment_card' using errcode = 'P0001';
  end if;
  if p_owner is null or not social_private.can_comment_deck(p_owner, p_deck, uid) then
    raise exception 'cant_comment' using errcode = 'P0001';
  end if;
  if p_parent is not null then
    select * into par from public.deck_comments
    where deck_comments.id = p_parent and deck_comments.owner = p_owner and deck_comments.deck_id = p_deck and deck_comments.parent is null;
    if not found or (par.hidden and uid <> p_owner and par.author <> uid)
       or (par.author <> uid and social_private.blocked_either(uid, par.author)) then
      raise exception 'bad_parent' using errcode = 'P0001';
    end if;
  end if;
  if (select count(*) from public.deck_comments where author = uid and created_at > now() - interval '1 minute') >= 10
     or (select count(*) from public.deck_comments where author = uid and created_at > now() - interval '1 day') >= 200 then
    raise exception 'comment_slow_down' using errcode = 'P0001';
  end if;
  if (select count(*) from public.deck_comments where owner = p_owner and deck_id = p_deck) >= 1000 then
    raise exception 'too_many_comments' using errcode = 'P0001';
  end if;
  insert into public.deck_comments (owner, deck_id, author, parent, body, card_name, card_image)
  values (p_owner, p_deck, uid, p_parent, txt, card, case when card is not null then nullif(left(btrim(coalesce(p_card_image, '')), 500), '') end)
  returning * into c;

  select li.data ->> 'name' into deck_name from public.library_items li where li.user_id = p_owner and li.kind = 'deck' and li.id = p_deck;
  who := social_private.display_name(uid);
  if p_owner <> uid then
    perform social_private.notify(
      p_owner, 'friends',
      who || case when p_parent is null then ' commented on ' else ' replied on ' end || coalesce(deck_name, 'your deck'),
      case when card is not null then 'On ' || card || ': ' else '' end || left(txt, 140),
      'friends', 'deck-comment-' || p_deck);
  end if;
  if p_parent is not null and par.author <> uid and par.author <> p_owner
     and social_private.can_read_deck(p_owner, p_deck, par.author) then
    perform social_private.notify(
      par.author, 'friends',
      who || ' replied to your comment on ' || coalesce(deck_name, 'a deck'),
      left(txt, 140),
      'friends', 'deck-comment-' || p_deck);
  end if;
  return social_private.comment_json(c, uid);
end;
$$;

-- Deletes a comment (and its replies): its author, or the deck's owner.
create or replace function public.delete_deck_comment(p_comment uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me();
begin
  delete from public.deck_comments where id = p_comment and (author = uid or owner = uid);
  if not found then
    raise exception 'not_your_comment' using errcode = 'P0001';
  end if;
end;
$$;

-- The deck's owner hides a comment from everyone but its author, or shows it again.
create or replace function public.hide_deck_comment(p_comment uuid, p_hidden boolean)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me();
begin
  update public.deck_comments set hidden = coalesce(p_hidden, true) where id = p_comment and owner = uid;
  if not found then
    raise exception 'not_your_comment' using errcode = 'P0001';
  end if;
end;
$$;

-- How many comments each of the caller's decks has: {deck id: count}.
create or replace function public.my_deck_comment_counts()
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce(jsonb_object_agg(x.deck_id, x.n), '{}'::jsonb)
  from (
    select c.deck_id, count(*) as n from public.deck_comments c
    where c.owner = auth.uid() and not social_private.blocked_either(c.owner, c.author)
    group by c.deck_id
  ) x;
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
    'activity_comments_version()',
    'activity_prefs()', 'set_activity_prefs(boolean, boolean, boolean, boolean)', 'selling_list(uuid)',
    'activity_feed(bigint, integer)', 'friends_activity(bigint, integer)',
    'deck_comments(uuid, text)', 'post_deck_comment(uuid, text, text, uuid, text, text)',
    'delete_deck_comment(uuid)', 'hide_deck_comment(uuid, boolean)', 'my_deck_comment_counts()'
  ] loop
    execute format('revoke execute on function public.%s from public, anon', f);
    execute format('grant execute on function public.%s to authenticated', f);
  end loop;
end;
$$;
