-- Manabind — trade nights: trading at a game night.
--
-- NOT applied automatically. The owner reviews it and runs it by hand (Supabase SQL editor, or
-- `npx supabase db push`). Needs the migrations before it (social, push notifications, social_more for
-- blocking, game nights and chat, live social updates for social_private.ping). Safe to run more than
-- once: tables and indexes are "if not exists", functions "create or replace". Nothing at the top
-- level deletes rows, drops anything or replaces a function that already exists: every function here
-- is new. The only DELETE is inside stop_trade_night_list (the caller taking their own list down).
--
-- The apps check for this migration with trade_nights_version() and leave the Trades section off a
-- game night until then (Android: data/social/TradeNightsApi.kt; web: src/social/tradeNightsApi.ts).
--
-- As everywhere else, the tables have row-level security on and no policies: the functions below
-- run as their owner and check the caller (auth.uid()) themselves.
--
-- What it is
--   On a game night, each invitee who answered Going can put up a list for the night: the cards
--   they're bringing to trade (picked from their binders or their event bag, snapshotted by the app)
--   and the cards they want (wishlist, cards their decks are missing, collection goals). Nothing is
--   shared until they choose to. Only the other invitees who are Going to that same night see it,
--   and only until 12 hours after the night starts (when a night counts as over everywhere else);
--   nobody blocked either way sees it; someone who changes their answer away from Going stops seeing
--   the others' lists and stops being seen. The apps match wants against the lists and suggest fair
--   trades themselves.
--
--   A trade proposed from the night goes into public.trades like any other (so the inbox, accepting,
--   notifications, "Update my binders" and mark_trade_applied all work unchanged) and is tied to the
--   night in trade_night_trades, which is what the night's "Trade table" lists. Two people at the same
--   night may trade without being friends, but only when both are Going, neither has blocked the
--   other, and the person asked has put up a list for the night (or they're friends) — so nobody gets
--   trade requests from strangers who never opted in.
--
-- Live updates: putting up, changing or taking down a list pings every other Going invitee (not
-- blocked either way) and the caller's own devices on "dm:<user id>" with social_private.ping —
-- event 'social', {what: 'night', id: <night id>, trades: true} — the same channel and payload shape
-- as 20261007000000_live_social_updates.sql, so the apps already reload the night. A trade tied to a
-- night pings both people through the existing trades_ping trigger.
--
-- Deleting an account: both tables cascade from public.profiles / public.game_nights /
-- public.trades, so delete_my_account removes the user's lists and links with their profile.

-- ============================================================================================
-- Tables
-- ============================================================================================

-- One list per person per night: what they bring to trade and what they want.
create table if not exists public.trade_night_lists (
  night_id uuid not null references public.game_nights (id) on delete cascade,
  user_id uuid not null references public.profiles (user_id) on delete cascade,
  -- What the cards were picked from, for the owner's own screen: [{"kind": "binder", "id", "name"} | {"kind": "bag", "name"}].
  sources jsonb not null default '[]' check (jsonb_typeof(sources) = 'array' and pg_column_size(sources) <= 16384),
  -- [{scryfallId, name, imageUrl, setCode, collectorNumber, foil, quantity, collectionId, condition, spare}]
  -- (spare: no deck of theirs needs it, or it's marked for trade).
  cards jsonb not null default '[]' check (jsonb_typeof(cards) = 'array' and pg_column_size(cards) <= 524288),
  -- [{name, weight}] — weight 3: wishlist, 2: a deck is missing it, 1: a collection goal is missing it.
  wants jsonb not null default '[]' check (jsonb_typeof(wants) = 'array' and pg_column_size(wants) <= 131072),
  updated_at timestamptz not null default now(),
  primary key (night_id, user_id)
);
create index if not exists trade_night_lists_user_idx on public.trade_night_lists (user_id);
alter table public.trade_night_lists enable row level security;
revoke all on public.trade_night_lists from anon, authenticated;

-- A trade proposed at a night (the night's Trade table).
create table if not exists public.trade_night_trades (
  trade_id uuid primary key references public.trades (id) on delete cascade,
  night_id uuid not null references public.game_nights (id) on delete cascade,
  created_at timestamptz not null default now()
);
create index if not exists trade_night_trades_night_idx on public.trade_night_trades (night_id);
alter table public.trade_night_trades enable row level security;
revoke all on public.trade_night_trades from anon, authenticated;

-- ============================================================================================
-- Helpers (private)
-- ============================================================================================

-- [who] is invited to [p_night] and answered Going.
create or replace function social_private.is_going(p_night uuid, who uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select who is not null
    and exists (select 1 from public.game_night_rsvps r where r.night_id = p_night and r.user_id = who and r.answer = 'going')
    and social_private.is_invited(p_night, who);
$$;

-- Lists are open (readable by the others, changeable) until the night is called off or is over.
create or replace function social_private.trade_night_open(n public.game_nights)
returns boolean
language sql
stable
set search_path = ''
as $$
  select n.id is not null and n.cancelled_at is null and n.starts_at > now() - interval '12 hours';
$$;

-- The cards someone brings, cleaned: up to 500 lines, each with a scryfallId, a name and 1–99 copies.
create or replace function social_private.clean_night_cards(p_cards jsonb)
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
  if p_cards is null or jsonb_typeof(p_cards) <> 'array' then
    return '[]';
  end if;
  if jsonb_array_length(p_cards) > 500 then
    raise exception 'too_many_night_cards' using errcode = 'P0001';
  end if;
  for c in select value from jsonb_array_elements(p_cards) loop
    if jsonb_typeof(c) <> 'object' or jsonb_typeof(c -> 'quantity') is distinct from 'number'
       or coalesce(c ->> 'scryfallId', '') = '' or btrim(coalesce(c ->> 'name', '')) = '' then
      raise exception 'bad_card' using errcode = 'P0001';
    end if;
    qty := floor((c ->> 'quantity')::numeric)::int;
    if qty < 1 or qty > 99 then
      raise exception 'bad_card' using errcode = 'P0001';
    end if;
    out := out || jsonb_build_array(jsonb_strip_nulls(jsonb_build_object(
      'scryfallId', left(c ->> 'scryfallId', 64),
      'name', left(btrim(c ->> 'name'), 200),
      'imageUrl', left(c ->> 'imageUrl', 500),
      'setCode', left(c ->> 'setCode', 12),
      'collectorNumber', left(c ->> 'collectorNumber', 12),
      'foil', coalesce(c -> 'foil' = 'true'::jsonb, false),
      'quantity', qty,
      'collectionId', left(c ->> 'collectionId', 64),
      'condition', case when c ->> 'condition' in ('NM', 'LP', 'MP', 'HP', 'DMG') then c ->> 'condition' end,
      'spare', coalesce(c -> 'spare' = 'true'::jsonb, false)
    )));
  end loop;
  return out;
end;
$$;

-- The cards someone wants, cleaned: up to 500 names (each once, its highest weight), weight 1–3.
create or replace function social_private.clean_night_wants(p_wants jsonb)
returns jsonb
language plpgsql
immutable
set search_path = ''
as $$
declare
  w jsonb;
  nm text;
  wt integer;
  seen jsonb := '{}';
begin
  if p_wants is null or jsonb_typeof(p_wants) <> 'array' then
    return '[]';
  end if;
  if jsonb_array_length(p_wants) > 500 then
    raise exception 'too_many_night_cards' using errcode = 'P0001';
  end if;
  for w in select value from jsonb_array_elements(p_wants) loop
    nm := left(btrim(coalesce(w ->> 'name', '')), 200);
    if jsonb_typeof(w) <> 'object' or nm = '' or jsonb_typeof(w -> 'weight') is distinct from 'number' then
      raise exception 'bad_card' using errcode = 'P0001';
    end if;
    wt := greatest(1, least(3, floor((w ->> 'weight')::numeric)::int));
    if not (seen ? lower(nm)) or (seen -> lower(nm) ->> 'weight')::int < wt then
      seen := seen || jsonb_build_object(lower(nm), jsonb_build_object('name', coalesce(seen -> lower(nm) ->> 'name', nm), 'weight', wt));
    end if;
  end loop;
  return coalesce((select jsonb_agg(v order by (v ->> 'weight')::int desc, lower(v ->> 'name')) from jsonb_each(seen) as e(k, v)), '[]');
end;
$$;

-- The sources the user picked, cleaned: up to 50 of {"kind": "binder", "id", "name"} | {"kind": "bag", "name"}.
create or replace function social_private.clean_night_sources(p_sources jsonb)
returns jsonb
language plpgsql
immutable
set search_path = ''
as $$
declare
  s jsonb;
  out jsonb := '[]';
begin
  if p_sources is null or jsonb_typeof(p_sources) <> 'array' then
    return '[]';
  end if;
  if jsonb_array_length(p_sources) > 50 then
    raise exception 'bad_sources' using errcode = 'P0001';
  end if;
  for s in select value from jsonb_array_elements(p_sources) loop
    if jsonb_typeof(s) <> 'object' or coalesce(s ->> 'kind', '') not in ('binder', 'bag') then
      raise exception 'bad_sources' using errcode = 'P0001';
    end if;
    out := out || jsonb_build_array(jsonb_strip_nulls(jsonb_build_object(
      'kind', s ->> 'kind', 'id', left(s ->> 'id', 64), 'name', left(btrim(coalesce(s ->> 'name', '')), 80))));
  end loop;
  return out;
end;
$$;

-- Tells the Going invitees of [p_night] (but anyone blocked either way by [p_from]) and [p_from]'s own
-- devices that the night's trade lists changed. A nicety: social_private.ping never fails the caller.
create or replace function social_private.trade_night_ping(p_night uuid, p_from uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  who uuid;
begin
  for who in
    select r.user_id from public.game_night_rsvps r
    where r.night_id = p_night and r.answer = 'going'
    union select p_from
  loop
    if who = p_from or not social_private.blocked_either(p_from, who) then
      perform social_private.ping(who, 'night', jsonb_build_object('id', p_night, 'trades', true));
    end if;
  end loop;
end;
$$;

-- A list as the apps read it.
create or replace function social_private.trade_night_list_json(l public.trade_night_lists)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select jsonb_build_object(
    'user', social_private.profile_json(l.user_id),
    'sources', l.sources,
    'cards', l.cards,
    'wants', l.wants,
    'updatedAt', social_private.ms(l.updated_at));
$$;

-- ============================================================================================
-- API: is this migration there?
-- ============================================================================================

create or replace function public.trade_nights_version()
returns integer
language sql
immutable
set search_path = ''
as $$
  select 1;
$$;

-- ============================================================================================
-- API: a night's trades
-- ============================================================================================

-- The trade side of [p_night] for the caller, or null when they aren't invited:
--   {nightId, going, open, mine, others, trades}
--   going:  the caller answered Going.
--   open:   lists can be put up and are shown (not called off, not over).
--   mine:   the caller's own list ({user, sources, cards, wants, updatedAt}) or null.
--   others: the lists of the other invitees who are Going — only when the caller is Going and the
--           night is open; nobody blocked either way.
--   trades: the trades tied to this night that the caller is in (the shape social_overview uses),
--           newest first; none with someone blocked either way.
create or replace function public.trade_night(p_night uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me();
  n public.game_nights;
  going boolean;
  open boolean;
begin
  select * into n from public.game_nights where id = p_night;
  if n.id is null or not social_private.is_invited(n.id, uid) then
    return null;
  end if;
  going := social_private.is_going(n.id, uid);
  open := social_private.trade_night_open(n);
  return jsonb_build_object(
    'nightId', n.id,
    'going', going,
    'open', open,
    'mine', (select social_private.trade_night_list_json(l) from public.trade_night_lists l where l.night_id = n.id and l.user_id = uid),
    'others', case when going and open then coalesce((
        select jsonb_agg(social_private.trade_night_list_json(l) order by pr.display_name)
        from public.trade_night_lists l
        join public.profiles pr on pr.user_id = l.user_id
        where l.night_id = n.id and l.user_id <> uid
          and social_private.is_going(n.id, l.user_id)
          and not social_private.blocked_either(uid, l.user_id)
      ), '[]'::jsonb) else '[]'::jsonb end,
    'trades', coalesce((
        select jsonb_agg(jsonb_build_object(
            'id', t.id, 'from_user', t.from_user, 'to_user', t.to_user, 'want', t.want, 'give', t.give,
            'message', t.message, 'reply', t.reply, 'status', t.status, 'reply_to', t.reply_to,
            'from_applied', t.from_applied, 'to_applied', t.to_applied, 'created_at', t.created_at, 'updated_at', t.updated_at
          ) order by t.updated_at desc)
        from public.trade_night_trades x
        join public.trades t on t.id = x.trade_id
        where x.night_id = n.id and (t.from_user = uid or t.to_user = uid)
          and not social_private.blocked_either(t.from_user, t.to_user)
      ), '[]'::jsonb)
  );
end;
$$;

-- Puts up (or replaces) the caller's list for [p_night]: what they bring ([p_cards], up to 500
-- lines), what they want ([p_wants], up to 500 names) and what the cards came from ([p_sources]).
-- Only for an invitee who answered Going, while the night is open. Answers trade_night(p_night).
create or replace function public.set_trade_night_list(p_night uuid, p_sources jsonb, p_cards jsonb, p_wants jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me_with_profile();
  n public.game_nights;
  v_sources jsonb := social_private.clean_night_sources(p_sources);
  v_cards jsonb := social_private.clean_night_cards(p_cards);
  v_wants jsonb := social_private.clean_night_wants(p_wants);
begin
  select * into n from public.game_nights where id = p_night;
  if n.id is null or not social_private.is_invited(n.id, uid) then
    raise exception 'no_such_night' using errcode = 'P0001';
  end if;
  if n.cancelled_at is not null then
    raise exception 'night_cancelled' using errcode = 'P0001';
  end if;
  if not social_private.trade_night_open(n) then
    raise exception 'night_over' using errcode = 'P0001';
  end if;
  if not social_private.is_going(n.id, uid) then
    raise exception 'not_going' using errcode = 'P0001';
  end if;
  insert into public.trade_night_lists as l (night_id, user_id, sources, cards, wants, updated_at)
  values (n.id, uid, v_sources, v_cards, v_wants, now())
  on conflict (night_id, user_id) do update
    set sources = excluded.sources, cards = excluded.cards, wants = excluded.wants, updated_at = now();
  perform social_private.trade_night_ping(n.id, uid);
  return public.trade_night(n.id);
end;
$$;

-- Takes the caller's list for [p_night] down (any time — even after the night, or when no longer
-- invited). Nothing happens when there's none.
create or replace function public.stop_trade_night_list(p_night uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me();
begin
  delete from public.trade_night_lists where night_id = p_night and user_id = uid;
  if found then
    perform social_private.trade_night_ping(p_night, uid);
  end if;
end;
$$;

-- Proposes a trade to [p_to] at [p_night]: [p_want] out of their cards, [p_give] out of the
-- caller's, as propose_trade takes them (social_private.clean_trade_cards: up to 100 lines). Both
-- must be Going, the night open, neither blocked by the other, and [p_to] must have put up a list
-- for the night or be a friend. The trade is an ordinary one (inbox, notifications, accepting and
-- updating binders as usual), tied to the night. Answers its id.
create or replace function public.propose_night_trade(p_night uuid, p_to uuid, p_want jsonb, p_give jsonb, p_message text default null)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me_with_profile();
  want jsonb := social_private.clean_trade_cards(p_want);
  give jsonb := social_private.clean_trade_cards(p_give);
  n public.game_nights;
  new_id uuid;
begin
  select * into n from public.game_nights where id = p_night;
  if n.id is null or not social_private.is_invited(n.id, uid) then
    raise exception 'no_such_night' using errcode = 'P0001';
  end if;
  if n.cancelled_at is not null then
    raise exception 'night_cancelled' using errcode = 'P0001';
  end if;
  if not social_private.trade_night_open(n) then
    raise exception 'night_over' using errcode = 'P0001';
  end if;
  if not social_private.is_going(n.id, uid) then
    raise exception 'not_going' using errcode = 'P0001';
  end if;
  if p_to is null or p_to = uid or not social_private.is_going(n.id, p_to)
     or social_private.blocked_either(uid, p_to)
     or not (social_private.are_friends(uid, p_to)
             or exists (select 1 from public.trade_night_lists l where l.night_id = n.id and l.user_id = p_to)) then
    raise exception 'cant_trade_here' using errcode = 'P0001';
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
  insert into public.trades (from_user, to_user, want, give, message)
  values (uid, p_to, want, give, nullif(btrim(p_message), ''))
  returning trades.id into new_id;
  insert into public.trade_night_trades (trade_id, night_id) values (new_id, n.id);
  return new_id;
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
    'trade_nights_version()',
    'trade_night(uuid)',
    'set_trade_night_list(uuid, jsonb, jsonb, jsonb)',
    'stop_trade_night_list(uuid)',
    'propose_night_trade(uuid, uuid, jsonb, jsonb, text)'
  ] loop
    execute format('revoke execute on function public.%s from public, anon', f);
    execute format('grant execute on function public.%s to authenticated', f);
  end loop;
end;
$$;

-- ============================================================================================
-- What to check after applying (two test accounts A and B in the same pod, a third C; run as each
-- with `set local role authenticated; set local request.jwt.claims = '{"sub": "<user id>"}';`
-- inside a transaction, or try it in the apps)
-- ============================================================================================
--
--  1. select public.trade_nights_version();                                   → 1
--  2. A plans a night (A is Going); B hasn't answered.
--     As B: select public.set_trade_night_list('<night>', '[]', '[{"scryfallId":"x","name":"Sol Ring","quantity":1}]', '[]');
--                                                                              → error not_going
--  3. As B: rsvp_game_night('<night>', 'going'); set_trade_night_list(... same ...) → {going: true, open: true, mine: {...}, others: [...]}
--  4. As A (Going) with no list yet: select public.trade_night('<night>');      → others has B's list (cards, wants), mine null
--  5. As C (not invited): select public.trade_night('<night>');                 → null; set_trade_night_list → no_such_night
--  6. As A: rsvp 'maybe'; trade_night('<night>')                               → going false, others []
--     As B: trade_night('<night>')                                             → A's list (if any) left out of others
--  7. A blocks B (block_user): each one's trade_night → the other's list left out; propose_night_trade → cant_trade_here
--  8. A and B Going, B has a list, not friends: as A
--     select public.propose_night_trade('<night>', '<B>', '[{"scryfallId":"x","name":"Sol Ring","quantity":1}]', '[]');
--                                                                              → a trade id; B gets the trade notification;
--     select * from public.trade_night_trades;                                  → (trade id, night)
--     B's social_overview().trades has it; respond_trade(id, 'accept') as B works; mark_trade_applied as each answers true once.
--  9. As A: propose_night_trade to someone Going without a list who isn't a friend → cant_trade_here
-- 10. As B: select public.stop_trade_night_list('<night>');                   → A's trade_night no longer shows B's list
-- 11. Night called off (cancel_game_night) or starts_at more than 12 hours ago: set_trade_night_list → night_cancelled / night_over;
--     trade_night → open false, others [] (trades still listed for the Trade table).
-- 12. Live: with the apps open on the night as A, B putting up or changing a list makes A's screen reload
--     (a 'social' broadcast {what: 'night', id, trades: true} on dm:<A>).
-- 13. Nothing else changed: \df public.propose_trade, mark_trade_applied, rsvp_game_night etc. are as before
--     (this file only creates new functions), and anon can't execute any of the new ones:
--     select has_function_privilege('anon', 'public.trade_night(uuid)', 'execute');  → false
