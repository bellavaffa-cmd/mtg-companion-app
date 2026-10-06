-- Manabind — sharing storage at home: a household of 2–6 friends who keep cards in the same places.
--
-- NOT applied automatically. The owner reviews it and runs it by hand (Supabase SQL editor, or
-- `npx supabase db push`). Safe to run more than once: tables and indexes are "if not exists",
-- functions "create or replace".
--
-- The apps check for it with household_version() and say "Household sharing isn't available yet"
-- until then (web: src/social/household.ts; Android: data/social/HouseholdApi.kt).
--
-- What's shared, and what isn't
-- -----------------------------
-- Each person's cards stay in their own library (library_items, synced as before — nothing here
-- writes to it or widens its policies). A household shares *read access to what is in a few storage
-- places*: for each shared place, the copies (card name, printing, image, copies, foil) that the
-- people who put cards there keep in it. Nothing else of anyone's library is ever returned: no
-- binders, decks, wishlists, notes, conditions, or places that aren't shared.
--
-- A storage place is the person's own (the Unsorted pile's "storagePlaces", by id). Sharing one puts
-- its id, name and kind in household_places. Someone else in the household who keeps their cards
-- there too ("Keep my cards here too") gets a place with the same id in their own storage — their app
-- adds it like any new place — and a household_place_users row says their copies there may be seen.
-- That row is what opens a person's copies, and only that person can add it: nobody can expose
-- someone else's cards by sharing a place id they've seen (a place id alone opens only the copies of
-- the people who opted in to it).
--
-- Why derive the copies here rather than have each app publish a snapshot: the libraries are already
-- on the server (synced whole as JSON), so the read function reads them as they are — always as
-- current as the last sync, nothing extra for the apps to send or forget to send, and nothing stale
-- left behind when someone stops sharing (their rows go, and with them all access). The cost is
-- reading up to six people's binders' JSON on each read, which is small (a household is 2–6 people,
-- and only places shared in it are looked at).
--
-- Borrowing from the shelf: a person can record that they took someone's copies (from a deck's pull
-- list, "ask Alex"). That's an ordinary loan (20261006010000_loans.sql: public.loans, lender = the
-- owner of the cards, borrower = the caller, client_id "hh-…"), so it shows under the borrower's
-- Loans → Borrowed with no new screen, and the owner gets a notification. Either side can say the
-- cards are back. The owner's own library isn't changed: it's still read-only across accounts.
--
-- Rules every function checks: the caller is signed in with a profile; a household is reached only by
-- its members (invited people see only its name and who asked); invites go to friends of the inviter
-- and never across a block with anyone already in it; 6 people at most (invites included); 5
-- households per person; 30 shared places per household.

-- ============================================================================================
-- Tables (row-level security on, no policies: reached only through the functions below)
-- ============================================================================================

create table if not exists public.households (
  id uuid primary key default gen_random_uuid(),
  name text not null check (char_length(btrim(name)) between 1 and 40),
  created_by uuid references public.profiles (user_id) on delete set null,
  created_at timestamptz not null default now()
);
alter table public.households enable row level security;
revoke all on public.households from anon, authenticated;

-- status 'invited' until they say yes; 'member' after.
create table if not exists public.household_members (
  household_id uuid not null references public.households (id) on delete cascade,
  user_id uuid not null references public.profiles (user_id) on delete cascade,
  status text not null default 'invited' check (status in ('invited', 'member')),
  invited_by uuid references public.profiles (user_id) on delete set null,
  created_at timestamptz not null default now(),
  joined_at timestamptz,
  primary key (household_id, user_id)
);
create index if not exists household_members_user_idx on public.household_members (user_id);
alter table public.household_members enable row level security;
revoke all on public.household_members from anon, authenticated;

-- A storage place shared in a household: [place_id] is the sharer's StoragePlace id; [name] and
-- [kind] as they were when last shared (the sharer's app sends them again when they change).
create table if not exists public.household_places (
  household_id uuid not null references public.households (id) on delete cascade,
  place_id text not null check (char_length(place_id) between 1 and 64),
  shared_by uuid not null references public.profiles (user_id) on delete cascade,
  name text not null check (char_length(btrim(name)) between 1 and 60),
  kind text not null default 'OTHER' check (kind in ('BOX', 'BINDER', 'DECK_BOX', 'SHELF', 'OTHER')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (household_id, place_id)
);
alter table public.household_places enable row level security;
revoke all on public.household_places from anon, authenticated;

-- Whose copies in a shared place the household may see: the sharer, and each person who keeps
-- their cards there too. Only ever added by that person.
create table if not exists public.household_place_users (
  household_id uuid not null,
  place_id text not null,
  user_id uuid not null references public.profiles (user_id) on delete cascade,
  added_at timestamptz not null default now(),
  primary key (household_id, place_id, user_id),
  foreign key (household_id, place_id) references public.household_places (household_id, place_id) on delete cascade
);
create index if not exists household_place_users_user_idx on public.household_place_users (user_id);
alter table public.household_place_users enable row level security;
revoke all on public.household_place_users from anon, authenticated;

-- ============================================================================================
-- Helpers (private)
-- ============================================================================================

-- Whether [who] is a member (not just invited) of [hh].
create or replace function social_private.is_household_member(hh uuid, who uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.household_members m
    where m.household_id = hh and m.user_id = who and m.status = 'member'
  );
$$;

-- The caller, who must be a member of [hh]; 'not_in_household' otherwise.
create or replace function social_private.household_me(hh uuid)
returns uuid
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me_with_profile();
begin
  if hh is null or not social_private.is_household_member(hh, uid) then
    raise exception 'not_in_household' using errcode = 'P0001';
  end if;
  return uid;
end;
$$;

-- A whole number from a JSON value, 0 when it isn't one; at most 9999.
create or replace function social_private.json_count(v jsonb)
returns integer
language sql
immutable
set search_path = ''
as $$
  select case when jsonb_typeof(v) = 'number' then floor(greatest(least((v #>> '{}')::numeric, 9999), 0))::int else 0 end;
$$;

-- [who]'s copies in the places of [hh] they opted in to (household_place_users), from their owned
-- binders and Unsorted pile as last synced: one row per place, printing and foil. Wishlists and
-- deleted binders are left out, and so is every entry field but the card's name, printing and image.
-- Nothing is returned for someone who isn't a member any more, or who is blocked either way with
-- [viewer].
create or replace function social_private.household_copies(hh uuid, who uuid, viewer uuid)
returns table (place_id text, scryfall_id text, name text, image_url text, qty integer, foil boolean)
language sql
stable
security definer
set search_path = ''
as $$
  with mine as (
    select u.place_id
    from public.household_place_users u
    where u.household_id = hh and u.user_id = who
      and social_private.is_household_member(hh, who)
      and (who = viewer or not social_private.blocked_either(who, viewer))
  ),
  lines as (
    select pl ->> 'placeId' as place_id,
           left(coalesce(e ->> 'scryfallId', ''), 64) as scryfall_id,
           left(btrim(coalesce(e ->> 'name', '')), 150) as name,
           left(nullif(e ->> 'imageUrl', ''), 500) as image_url,
           social_private.json_count(pl -> 'qty') as qty,
           coalesce(pl -> 'foil' = 'true'::jsonb, false) as foil
    from public.library_items li
    cross join lateral jsonb_array_elements(case when jsonb_typeof(li.data -> 'entries') = 'array' then li.data -> 'entries' else '[]'::jsonb end) e
    cross join lateral jsonb_array_elements(case when jsonb_typeof(e -> 'places') = 'array' then e -> 'places' else '[]'::jsonb end) pl
    where li.user_id = who and li.kind = 'collection' and not li.deleted and li.data is not null
      and coalesce(li.data ->> 'type', 'OWNED') <> 'WISHLIST'
      and jsonb_typeof(pl) = 'object'
      and (pl ->> 'placeId') in (select m.place_id from mine m)
  )
  select l.place_id, l.scryfall_id, min(l.name), min(l.image_url), least(sum(l.qty), 9999)::int, l.foil
  from lines l
  where l.qty > 0 and l.name <> '' and l.scryfall_id <> ''
  group by l.place_id, l.scryfall_id, l.foil
  limit 5000;
$$;

-- A household as its members see it: name, people (members and invited), places and whose copies
-- are in each.
create or replace function social_private.household_json(hh uuid)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select jsonb_build_object(
    'id', h.id,
    'name', h.name,
    'createdAt', social_private.ms(h.created_at),
    'members', coalesce((
      select jsonb_agg(jsonb_build_object(
        'profile', social_private.profile_json(m.user_id),
        'status', m.status) order by m.status desc, m.created_at)
      from public.household_members m where m.household_id = h.id
    ), '[]'::jsonb),
    'places', coalesce((
      select jsonb_agg(jsonb_build_object(
        'placeId', p.place_id,
        'name', p.name,
        'kind', p.kind,
        'sharedBy', p.shared_by,
        'users', coalesce((
          select jsonb_agg(u.user_id order by u.added_at)
          from public.household_place_users u
          where u.household_id = p.household_id and u.place_id = p.place_id
        ), '[]'::jsonb)) order by p.created_at)
      from public.household_places p where p.household_id = h.id
    ), '[]'::jsonb))
  from public.households h where h.id = hh;
$$;

-- Takes [who] out of [hh]: their places and their opt-ins go (so nobody sees their copies any more),
-- and the household itself goes once nobody is left in it.
create or replace function social_private.drop_from_household(hh uuid, who uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  delete from public.household_places where household_id = hh and shared_by = who;
  delete from public.household_place_users where household_id = hh and user_id = who;
  delete from public.household_members where household_id = hh and user_id = who;
  if not exists (select 1 from public.household_members where household_id = hh and status = 'member') then
    delete from public.households where id = hh;
  end if;
end;
$$;

-- ============================================================================================
-- API
-- ============================================================================================

-- Is this migration there?
create or replace function public.household_version()
returns integer
language sql
immutable
set search_path = ''
as $$
  select 1;
$$;

-- The caller's households, and the invitations waiting for an answer.
create or replace function public.my_households()
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
    'households', coalesce((
      select jsonb_agg(social_private.household_json(m.household_id) order by m.joined_at)
      from public.household_members m
      where m.user_id = uid and m.status = 'member'
    ), '[]'::jsonb),
    'invites', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', h.id,
        'name', h.name,
        'invitedBy', social_private.profile_json(m.invited_by),
        'members', (select count(*) from public.household_members x where x.household_id = h.id and x.status = 'member'),
        'at', social_private.ms(m.created_at)) order by m.created_at desc)
      from public.household_members m
      join public.households h on h.id = m.household_id
      where m.user_id = uid and m.status = 'invited'
        and (m.invited_by is null or not social_private.blocked_either(uid, m.invited_by))
    ), '[]'::jsonb));
end;
$$;

-- Starts a household with the caller in it. Answers its id.
create or replace function public.create_household(p_name text)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me_with_profile();
  clean text := btrim(coalesce(p_name, ''));
  hh uuid;
begin
  if char_length(clean) not between 1 and 40 then
    raise exception 'bad_name' using errcode = 'P0001';
  end if;
  if (select count(*) from public.household_members where user_id = uid) >= 5 then
    raise exception 'too_many_households' using errcode = 'P0001';
  end if;
  insert into public.households (name, created_by) values (clean, uid) returning id into hh;
  insert into public.household_members (household_id, user_id, status, invited_by, joined_at)
  values (hh, uid, 'member', uid, now());
  return hh;
end;
$$;

-- A member renames the household.
create or replace function public.rename_household(p_household uuid, p_name text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.household_me(p_household);
  clean text := btrim(coalesce(p_name, ''));
begin
  if char_length(clean) not between 1 and 40 then
    raise exception 'bad_name' using errcode = 'P0001';
  end if;
  update public.households set name = clean where id = p_household;
end;
$$;

-- A member invites one of their friends. Answers 'invited', or 'already' when they're in it or asked.
create or replace function public.invite_to_household(p_household uuid, p_friend uuid)
returns text
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.household_me(p_household);
  hname text;
begin
  if p_friend is null or p_friend = uid then
    raise exception 'self' using errcode = 'P0001';
  end if;
  if not social_private.are_friends(uid, p_friend) or social_private.blocked_either(uid, p_friend) then
    raise exception 'not_a_friend' using errcode = 'P0001';
  end if;
  if exists (select 1 from public.household_members where household_id = p_household and user_id = p_friend) then
    return 'already';
  end if;
  if exists (
    select 1 from public.household_members m
    where m.household_id = p_household and social_private.blocked_either(m.user_id, p_friend)
  ) then
    raise exception 'blocked' using errcode = 'P0001';
  end if;
  if (select count(*) from public.household_members where household_id = p_household) >= 6 then
    raise exception 'household_full' using errcode = 'P0001';
  end if;
  if (select count(*) from public.household_members where user_id = p_friend) >= 5 then
    raise exception 'too_many_households' using errcode = 'P0001';
  end if;
  insert into public.household_members (household_id, user_id, status, invited_by)
  values (p_household, p_friend, 'invited', uid);
  select name into hname from public.households where id = p_household;
  perform social_private.notify(
    p_friend, 'friends',
    social_private.display_name(uid) || ' asked you to share storage at home',
    hname || ' · each of you still owns your own cards',
    'friends', 'household-' || p_household
  );
  return 'invited';
end;
$$;

-- The invited person says yes or no. Saying yes to an invitation that's gone answers 'not_invited'.
create or replace function public.respond_household(p_household uuid, p_accept boolean)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me_with_profile();
  inviter uuid;
begin
  select invited_by into inviter from public.household_members
  where household_id = p_household and user_id = uid and status = 'invited';
  if not found then
    raise exception 'not_invited' using errcode = 'P0001';
  end if;
  if coalesce(p_accept, false) then
    update public.household_members set status = 'member', joined_at = now()
    where household_id = p_household and user_id = uid;
    if inviter is not null then
      perform social_private.notify(
        inviter, 'friends',
        social_private.display_name(uid) || ' is sharing storage with you',
        coalesce((select name from public.households where id = p_household), 'Shared shelf'),
        'friends', 'household-' || p_household
      );
    end if;
  else
    delete from public.household_members where household_id = p_household and user_id = uid;
  end if;
end;
$$;

-- Stop sharing: the caller leaves. Their shared places and their copies stop being seen at once.
create or replace function public.leave_household(p_household uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me();
begin
  if not exists (select 1 from public.household_members where household_id = p_household and user_id = uid) then
    return;
  end if;
  perform social_private.drop_from_household(p_household, uid);
end;
$$;

-- A member takes back an invitation not answered yet.
create or replace function public.cancel_household_invite(p_household uuid, p_user uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.household_me(p_household);
begin
  delete from public.household_members
  where household_id = p_household and user_id = p_user and status = 'invited';
end;
$$;

-- A member shares one of their own storage places, or sends its name and kind again. The caller's
-- copies in it can be seen from then on. 'not_yours' when someone else shared that place.
create or replace function public.share_household_place(p_household uuid, p_place_id text, p_name text, p_kind text default 'OTHER')
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.household_me(p_household);
  clean text := btrim(coalesce(p_name, ''));
  pid text := btrim(coalesce(p_place_id, ''));
  pkind text := case when p_kind in ('BOX', 'BINDER', 'DECK_BOX', 'SHELF', 'OTHER') then p_kind else 'OTHER' end;
  owner uuid;
begin
  if char_length(pid) not between 1 and 64 then
    raise exception 'no_such_place' using errcode = 'P0001';
  end if;
  if char_length(clean) not between 1 and 60 then
    raise exception 'bad_name' using errcode = 'P0001';
  end if;
  select shared_by into owner from public.household_places where household_id = p_household and place_id = pid for update;
  if found and owner <> uid then
    raise exception 'not_yours' using errcode = 'P0001';
  end if;
  if not found and (select count(*) from public.household_places where household_id = p_household) >= 30 then
    raise exception 'too_many_places' using errcode = 'P0001';
  end if;
  insert into public.household_places (household_id, place_id, shared_by, name, kind)
  values (p_household, pid, uid, clean, pkind)
  on conflict (household_id, place_id) do update set name = excluded.name, kind = excluded.kind, updated_at = now();
  insert into public.household_place_users (household_id, place_id, user_id)
  values (p_household, pid, uid)
  on conflict do nothing;
end;
$$;

-- "Keep my cards here too": the caller's copies in a place someone shared can be seen as well (the
-- caller's app has added a place with the same id to their own storage).
create or replace function public.join_household_place(p_household uuid, p_place_id text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.household_me(p_household);
begin
  if not exists (select 1 from public.household_places where household_id = p_household and place_id = p_place_id) then
    raise exception 'no_such_place' using errcode = 'P0001';
  end if;
  insert into public.household_place_users (household_id, place_id, user_id)
  values (p_household, p_place_id, uid)
  on conflict do nothing;
end;
$$;

-- Unshares a place: the sharer takes it out of the household (nobody's copies there are seen any
-- more); anyone else only stops their own copies there being seen.
create or replace function public.unshare_household_place(p_household uuid, p_place_id text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.household_me(p_household);
begin
  if exists (select 1 from public.household_places where household_id = p_household and place_id = p_place_id and shared_by = uid) then
    delete from public.household_places where household_id = p_household and place_id = p_place_id;
  else
    delete from public.household_place_users where household_id = p_household and place_id = p_place_id and user_id = uid;
  end if;
end;
$$;

-- What the other people in [p_household] keep in its shared places, and the cards borrowed from the
-- shelf that aren't back yet:
--   {"copies": [{"userId", "placeId", "scryfallId", "name", "imageUrl", "qty", "foil"}],
--    "loans":  [{"id", "clientId", "lender", "borrower", "cards": [{"name", "qty", "printingId"}], "note", "lentAt"}]}
-- The caller's own copies aren't in it: their app has them already, newer than the last sync.
create or replace function public.household_cards(p_household uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.household_me(p_household);
begin
  return jsonb_build_object(
    'copies', coalesce((
      select jsonb_agg(jsonb_build_object(
        'userId', m.user_id, 'placeId', c.place_id, 'scryfallId', c.scryfall_id, 'name', c.name,
        'imageUrl', c.image_url, 'qty', c.qty, 'foil', c.foil))
      from public.household_members m
      cross join lateral social_private.household_copies(p_household, m.user_id, uid) c
      where m.household_id = p_household and m.status = 'member' and m.user_id <> uid
    ), '[]'::jsonb),
    'loans', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', l.id, 'clientId', l.client_id, 'lender', l.lender, 'borrower', l.borrower,
        'cards', l.cards, 'note', l.note, 'lentAt', social_private.ms(l.lent_at)) order by l.lent_at desc)
      from public.loans l
      where l.status = 'open' and l.client_id like 'hh-%'
        and social_private.is_household_member(p_household, l.lender)
        and social_private.is_household_member(p_household, l.borrower)
        and (l.lender = uid or l.borrower = uid or not (social_private.blocked_either(uid, l.lender) or social_private.blocked_either(uid, l.borrower)))
    ), '[]'::jsonb));
end;
$$;

-- The caller took some of [p_from]'s copies off the shared shelf (a deck's pull list, "ask Alex"):
-- recorded as a loan from them, so it shows under the caller's Loans → Borrowed, and [p_from] is
-- told. Every card must be one [p_from] keeps in a place shared in the household, and no more copies
-- than they keep there. [p_client_id] ("hh-" and the app's own id) makes a retry store one loan.
-- Answers the loan's id.
create or replace function public.household_borrow(p_household uuid, p_from uuid, p_client_id text, p_cards jsonb, p_note text default null)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.household_me(p_household);
  cleaned jsonb;
  c jsonb;
  loan uuid;
  is_new boolean;
  clean_note text := nullif(left(btrim(coalesce(p_note, '')), 200), '');
begin
  if p_from is null or p_from = uid then
    raise exception 'self' using errcode = 'P0001';
  end if;
  if not social_private.is_household_member(p_household, p_from) or social_private.blocked_either(uid, p_from) then
    raise exception 'not_in_household' using errcode = 'P0001';
  end if;
  if coalesce(p_client_id, '') !~ '^hh-[A-Za-z0-9_-]{1,61}$' then
    raise exception 'bad_card' using errcode = 'P0001';
  end if;
  cleaned := social_private.clean_loan_cards(p_cards);
  for c in select * from jsonb_array_elements(cleaned) loop
    if (c ->> 'qty')::int > coalesce((
      select sum(h.qty) from social_private.household_copies(p_household, p_from, uid) h
      where lower(h.name) = lower(c ->> 'name')
    ), 0) then
      raise exception 'not_on_shelf' using errcode = 'P0001';
    end if;
  end loop;
  insert into public.loans as l (lender, borrower, client_id, cards, note)
  values (p_from, uid, p_client_id, cleaned, clean_note)
  on conflict (lender, client_id) do update
    set cards = excluded.cards, note = excluded.note, updated_at = now()
    where l.borrower = uid and l.status = 'open'
  returning l.id, (l.xmax = 0) into loan, is_new;
  if loan is null then
    raise exception 'bad_card' using errcode = 'P0001';
  end if;
  if is_new then
    perform social_private.notify(
      p_from, 'friends',
      social_private.display_name(uid) || ' borrowed ' || social_private.loan_cards_phrase(cleaned) || ' from the shelf',
      coalesce(clean_note, 'They show under Loans until they''re back.'),
      'friends', 'loan-' || loan
    );
  end if;
  return loan;
end;
$$;

-- The cards borrowed from the shelf are back: either side can say so.
create or replace function public.household_loan_returned(p_loan uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me();
begin
  update public.loans
  set status = 'returned', returned_at = now(), updated_at = now()
  where id = p_loan and status = 'open' and client_id like 'hh-%' and (lender = uid or borrower = uid);
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
    'household_version()', 'my_households()', 'create_household(text)', 'rename_household(uuid, text)',
    'invite_to_household(uuid, uuid)', 'respond_household(uuid, boolean)', 'leave_household(uuid)',
    'cancel_household_invite(uuid, uuid)', 'share_household_place(uuid, text, text, text)',
    'join_household_place(uuid, text)', 'unshare_household_place(uuid, text)',
    'household_cards(uuid)', 'household_borrow(uuid, uuid, text, jsonb, text)', 'household_loan_returned(uuid)'
  ] loop
    execute format('revoke execute on function public.%s from public, anon', f);
    execute format('grant execute on function public.%s to authenticated', f);
  end loop;
end;
$$;

-- ============================================================================================
-- Checks. These were run against a local PostgreSQL 16 with every migration applied in order (auth,
-- vault and pg_net stubbed), as role authenticated, switching auth.uid() between A and B who are
-- friends and C who is A's friend but not in the household. A's library had copies in 'red', in an
-- unshared place and on a wishlist; only the 'red' copies from owned binders came back. To repeat on
-- a branch database, switch users with `set request.jwt.claims`:
--
--   A: select create_household('Shared shelf');                         -- → id H
--   A: select share_household_place(H, 'red', 'Red box', 'BOX');
--   B: select household_cards(H);                                       -- not_in_household
--   A: select invite_to_household(H, B);                                -- 'invited'
--   A: select invite_to_household(H, B);                                -- 'already'
--   A: select invite_to_household(H, <not a friend>);                   -- not_a_friend
--   B: select my_households();                                          -- invites: [H], households: []
--   B: select household_cards(H);                                       -- not_in_household (invited only)
--   B: select respond_household(H, true);
--   B: select household_cards(H);       -- A's copies in 'red' only; none of A's other places or binders
--   B: select share_household_place(H, 'red', 'Mine', 'BOX');           -- not_yours
--   B: select join_household_place(H, 'red');   -- A now also sees B's copies with placeId 'red'
--   A: select household_cards(H);                                       -- B's copies in 'red'
--   B: select household_borrow(H, A, 'hh-1', '[{"name":"Sol Ring","qty":1}]');  -- loan id; A notified
--   B: select my_borrowed_loans();                                      -- has it (Loans → Borrowed)
--   B: select household_borrow(H, A, 'hh-2', '[{"name":"Not There","qty":1}]'); -- not_on_shelf
--   A: select unshare_household_place(H, 'red');
--   B: select household_cards(H);                                       -- copies: []
--   C: select household_cards(H);                                       -- not_in_household
--   A: select leave_household(H);   B: select leave_household(H);       -- household gone
-- ============================================================================================
