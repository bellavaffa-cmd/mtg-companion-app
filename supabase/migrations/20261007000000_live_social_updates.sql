-- Live social updates: when a trade, friend request or friendship, loan, game night answer or invite,
-- block or household membership changes, both people involved (and the actor's own other devices)
-- hear about it straight away on their private "dm:<user id>" channel — the one direct messages and
-- pod chat already use (20261006020000_social_more.sql, 20261006070000_game_nights_chat.sql) — as
-- event 'social' with a small payload {what, id}:
--   what: 'trade' | 'friends' | 'loan' | 'night' | 'household'
--   id:   the trade, loan, night or household id; for 'friends' the other person's user id.
-- The apps reload just that area. Nothing private travels: the payload only says what to reload.
--
-- Done with AFTER triggers on the tables rather than by editing the functions that change them: the
-- functions (respond_trade, respond_friend, block_user, rsvp_game_night, respond_household…) stay
-- exactly as they are, and every path that changes a row — including ones added later and cascades —
-- pings. realtime.send writes to realtime.messages, which is broadcast after the transaction commits,
-- so an app that reloads on the ping reads the committed change. A ping never fails the change.
--
-- No existing function is replaced and nothing is dropped except "drop trigger if exists" for the
-- triggers made here (so this file can run again).

-- Tells [who]'s devices that [what] changed. A nicety: never fails the caller.
create or replace function social_private.ping(who uuid, what text, payload jsonb)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  if who is null then
    return;
  end if;
  perform realtime.send(
    jsonb_build_object('what', what) || coalesce(payload, '{}'::jsonb),
    'social', 'dm:' || who::text, true);
exception when others then
  raise warning 'social ping not sent: %', sqlerrm;
end;
$$;

-- Trades: the sender and the recipient.
create or replace function social_private.on_trade_ping()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  t public.trades := case when tg_op = 'DELETE' then old else new end;
begin
  perform social_private.ping(t.from_user, 'trade', jsonb_build_object('id', t.id));
  perform social_private.ping(t.to_user, 'trade', jsonb_build_object('id', t.id));
  return null;
exception when others then
  raise warning 'social ping not sent: %', sqlerrm;
  return null;
end;
$$;

drop trigger if exists trades_ping on public.trades;
create trigger trades_ping
  after insert or update or delete on public.trades
  for each row execute function social_private.on_trade_ping();

-- Friend requests and friendships (asked, accepted, declined, cancelled, removed, blocked): both people.
create or replace function social_private.on_friendship_ping()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  f public.friendships := case when tg_op = 'DELETE' then old else new end;
begin
  perform social_private.ping(f.requester, 'friends', jsonb_build_object('id', f.addressee));
  perform social_private.ping(f.addressee, 'friends', jsonb_build_object('id', f.requester));
  return null;
exception when others then
  raise warning 'social ping not sent: %', sqlerrm;
  return null;
end;
$$;

drop trigger if exists friendships_ping on public.friendships;
create trigger friendships_ping
  after insert or update or delete on public.friendships
  for each row execute function social_private.on_friendship_ping();

-- Blocking and unblocking: only the blocker's own devices (the blocked person learns nothing new;
-- a friendship the block ended pings them through friendships_ping like any removal).
create or replace function social_private.on_block_ping()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  b public.blocks := case when tg_op = 'DELETE' then old else new end;
begin
  perform social_private.ping(b.blocker, 'friends', jsonb_build_object('id', b.blocked));
  return null;
exception when others then
  raise warning 'social ping not sent: %', sqlerrm;
  return null;
end;
$$;

drop trigger if exists blocks_ping on public.blocks;
create trigger blocks_ping
  after insert or delete on public.blocks
  for each row execute function social_private.on_block_ping();

-- Loans: the lender and the borrower, when something they'd see changed.
create or replace function social_private.on_loan_ping()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  l public.loans := case when tg_op = 'DELETE' then old else new end;
begin
  -- The lending app sends its open loans again now and then; an update that changes nothing the
  -- other side shows (only updated_at or reminded_at) isn't worth a reload.
  if tg_op = 'UPDATE' and (old.status, old.cards, old.back_by, old.game_night, old.note, old.returned_at)
       is not distinct from (new.status, new.cards, new.back_by, new.game_night, new.note, new.returned_at) then
    return null;
  end if;
  perform social_private.ping(l.lender, 'loan', jsonb_build_object('id', l.id));
  perform social_private.ping(l.borrower, 'loan', jsonb_build_object('id', l.id));
  return null;
exception when others then
  raise warning 'social ping not sent: %', sqlerrm;
  return null;
end;
$$;

drop trigger if exists loans_ping on public.loans;
create trigger loans_ping
  after insert or update or delete on public.loans
  for each row execute function social_private.on_loan_ping();

-- Game night answers and guest invites: everyone invited to the night, the organiser and the person
-- the row is about (a guest who was just taken off is no longer among the invitees).
create or replace function social_private.on_night_ping()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  night uuid := case when tg_op = 'DELETE' then old.night_id else new.night_id end;
  person uuid := case when tg_op = 'DELETE' then old.user_id else new.user_id end;
  who uuid;
begin
  for who in
    select i from social_private.night_invitees(night) i
    union select n.organiser from public.game_nights n where n.id = night
    union select person
  loop
    perform social_private.ping(who, 'night', jsonb_build_object('id', night));
  end loop;
  return null;
exception when others then
  raise warning 'social ping not sent: %', sqlerrm;
  return null;
end;
$$;

drop trigger if exists game_night_rsvps_ping on public.game_night_rsvps;
create trigger game_night_rsvps_ping
  after insert or update or delete on public.game_night_rsvps
  for each row execute function social_private.on_night_ping();

drop trigger if exists game_night_guests_ping on public.game_night_guests;
create trigger game_night_guests_ping
  after insert or delete on public.game_night_guests
  for each row execute function social_private.on_night_ping();

-- Household invites and memberships: everyone in or invited to the household, and the person the row
-- is about (someone who left or whose invite was withdrawn is no longer listed).
create or replace function social_private.on_household_ping()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  home uuid := case when tg_op = 'DELETE' then old.household_id else new.household_id end;
  person uuid := case when tg_op = 'DELETE' then old.user_id else new.user_id end;
  who uuid;
begin
  for who in
    select m.user_id from public.household_members m where m.household_id = home
    union select person
  loop
    perform social_private.ping(who, 'household', jsonb_build_object('id', home));
  end loop;
  return null;
exception when others then
  raise warning 'social ping not sent: %', sqlerrm;
  return null;
end;
$$;

drop trigger if exists household_members_ping on public.household_members;
create trigger household_members_ping
  after insert or update or delete on public.household_members
  for each row execute function social_private.on_household_ping();

revoke execute on all functions in schema social_private from public, anon, authenticated;
