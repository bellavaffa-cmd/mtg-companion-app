-- Phones as remotes for the life counter, and "who has it?" for a deck's missing cards.
--
-- A match's live channel is the private Realtime topic "match:<id>". Only the host and the
-- players sitting at the match may listen. Nobody broadcasts on it directly (no insert policy):
--   * the host publishes the game state through publish_match_state (event "state");
--   * a seated player sends requests for their own seat through send_match_action (event
--     "action"), which stamps the seat the server knows they sit in — a player can't pose as
--     another seat. The host's app applies them, or ignores them with remotes switched off.

-- Whether the caller may listen on [p_topic]: "match:<id>" of a live match they host or sit at.
create or replace function public.match_channel_member(p_topic text)
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  uid uuid := auth.uid();
  mid uuid;
begin
  if uid is null or p_topic is null or p_topic !~ '^match:[0-9a-f-]{36}$' then
    return false;
  end if;
  mid := substring(p_topic from 7)::uuid;
  return exists (
    select 1 from public.matches m
    where m.id = mid and m.ended_at is null and m.created_at > now() - interval '12 hours'
      and (m.host = uid or exists (select 1 from public.match_players mp where mp.match_id = m.id and mp.user_id = uid))
  );
end;
$$;

drop policy if exists "match members listen on the match channel" on realtime.messages;
create policy "match members listen on the match channel" on realtime.messages
  for select to authenticated
  using (realtime.messages.extension = 'broadcast' and public.match_channel_member(realtime.topic()));

-- The host shares the game (life totals, counters, whose turn…) with the players' remotes.
create or replace function public.publish_match_state(p_match uuid, p_state jsonb)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me();
begin
  if not exists (select 1 from public.matches m where m.id = p_match and m.host = uid and m.ended_at is null) then
    raise exception 'not_host' using errcode = 'P0001';
  end if;
  if p_state is null or length(p_state::text) > 60000 then
    raise exception 'bad_state' using errcode = 'P0001';
  end if;
  perform realtime.send(jsonb_build_object('state', p_state), 'state', 'match:' || p_match, true);
end;
$$;

-- A seated player asks the host to change their seat ([p_action], e.g. {"type":"life","delta":-1}).
-- The seat attached is the one the server knows they sit in.
create or replace function public.send_match_action(p_match uuid, p_action jsonb)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me();
  my_seat integer;
begin
  select mp.seat into my_seat
  from public.match_players mp
  join public.matches m on m.id = mp.match_id
  where mp.match_id = p_match and mp.user_id = uid and m.ended_at is null and m.created_at > now() - interval '12 hours';
  if my_seat is null then
    raise exception 'not_seated' using errcode = 'P0001';
  end if;
  if p_action is null or jsonb_typeof(p_action) <> 'object' or length(p_action::text) > 4000 then
    raise exception 'bad_action' using errcode = 'P0001';
  end if;
  perform realtime.send(
    jsonb_build_object('seat', my_seat, 'user_id', uid, 'action', p_action, 'at', (extract(epoch from clock_timestamp()) * 1000)::bigint),
    'action', 'match:' || p_match, true
  );
end;
$$;

-- "Who has it?": copies of the cards named [p_names] (exact names, any case) in the binders
-- friends share with the caller — not their wishlists or decks. At most 300 rows, shaped like
-- search_shared_cards.
create or replace function public.who_has_cards(p_names text[])
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me();
  wanted text[];
  result jsonb;
begin
  select coalesce(array_agg(distinct lower(btrim(n))), '{}') into wanted
  from unnest(coalesce(p_names, '{}')) as n
  where char_length(btrim(n)) between 1 and 200;
  if cardinality(wanted) = 0 then
    return '[]'::jsonb;
  end if;
  if cardinality(wanted) > 250 then
    raise exception 'too_many_cards' using errcode = 'P0001';
  end if;
  select coalesce(jsonb_agg(h.row order by h.row ->> 'name', h.row ->> 'item_name'), '[]') into result
  from (
    select jsonb_build_object(
      'owner', v.owner, 'kind', v.kind, 'item_id', v.id, 'item_name', v.data ->> 'name',
      'scryfall_id', e ->> 'scryfallId', 'name', e ->> 'name', 'image_url', e ->> 'imageUrl',
      'quantity', coalesce((e ->> 'quantity')::int, 0), 'foil_quantity', coalesce((e ->> 'foilQuantity')::int, 0)
    ) as row
    from social_private.visible_items(uid) v,
    lateral jsonb_array_elements(coalesce(v.data -> 'entries', '[]')) e
    where v.kind = 'collection' and coalesce(v.data ->> 'type', 'OWNED') <> 'WISHLIST'
      and lower(e ->> 'name') = any(wanted)
    limit 300
  ) h;
  return result;
end;
$$;

revoke all on function public.match_channel_member(text) from public, anon;
revoke all on function public.publish_match_state(uuid, jsonb) from public, anon;
revoke all on function public.send_match_action(uuid, jsonb) from public, anon;
revoke all on function public.who_has_cards(text[]) from public, anon;
grant execute on function public.match_channel_member(text) to authenticated;
grant execute on function public.publish_match_state(uuid, jsonb) to authenticated;
grant execute on function public.send_match_action(uuid, jsonb) to authenticated;
grant execute on function public.who_has_cards(text[]) to authenticated;
revoke execute on all functions in schema social_private from public, anon, authenticated;
