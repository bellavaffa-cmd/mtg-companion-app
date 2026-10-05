-- Manabind — loans to friends: the friend sees what they've borrowed, and can be reminded.
--
-- A loan itself lives in the lender's library (the Unsorted pile's "loans", synced through
-- library_items like everything else; see the apps' Loans.kt / loans.ts). When the borrower is a
-- friend, the lender's app also sends a summary here — who, which cards (name, copies, printing),
-- when they're due back and a note — so the friend's app can show "Borrowed" and get a notification
-- ("Borrowed from Sam: 3 cards"). The apps work without any of this: if these functions fail (offline,
-- or this migration not applied yet), the loan is still kept and the friend just doesn't see it.
--
-- Rows are reached only through the functions below; the table itself is closed to the API.
-- Sending is idempotent per (lender, client_id) — the app's own loan id — so a retry, or the app
-- sending every open loan again when the Loans page opens, stores one loan.
--
-- Reminders go out through the push notifications set up in 20260919010000_push_notifications.sql
-- (social_private.notify), as the "friends" kind; at most one per loan every 12 hours.
--
-- Safe to re-run: every statement is idempotent.

create table if not exists public.loans (
  id uuid primary key default gen_random_uuid(),
  lender uuid not null references public.profiles (user_id) on delete cascade,
  borrower uuid not null references public.profiles (user_id) on delete cascade,
  -- The lending app's own id for the loan.
  client_id text not null check (char_length(client_id) between 1 and 64),
  -- [{"name": text, "qty": int, "printingId": text|null}], 1 to 200 cards: the copies still out.
  cards jsonb not null check (jsonb_typeof(cards) = 'array' and pg_column_size(cards) <= 32768),
  back_by date,
  -- Due back at the next game night (back_by is then null).
  game_night boolean not null default false,
  note text check (note is null or char_length(note) <= 200),
  status text not null default 'open' check (status in ('open', 'returned')),
  lent_at timestamptz not null default now(),
  returned_at timestamptz,
  reminded_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (lender, client_id)
);
create index if not exists loans_borrower_idx on public.loans (borrower, status);
alter table public.loans enable row level security;
revoke all on public.loans from anon, authenticated;

-- Checks a loan's cards and returns them cleaned: names trimmed, copies 1–999, unknown keys dropped.
create or replace function social_private.clean_loan_cards(p_cards jsonb)
returns jsonb
language plpgsql
immutable
set search_path = ''
as $$
declare
  c jsonb;
  cleaned jsonb := '[]'::jsonb;
  cname text;
  cqty integer;
begin
  if p_cards is null or jsonb_typeof(p_cards) <> 'array' or jsonb_array_length(p_cards) not between 1 and 200 then
    raise exception 'bad_card' using errcode = 'P0001';
  end if;
  for c in select * from jsonb_array_elements(p_cards) loop
    cname := btrim(coalesce(c ->> 'name', ''));
    if char_length(cname) not between 1 and 150 or coalesce(c ->> 'qty', '') !~ '^[0-9]{1,3}$' then
      raise exception 'bad_card' using errcode = 'P0001';
    end if;
    cqty := (c ->> 'qty')::integer;
    if cqty < 1 then
      raise exception 'bad_card' using errcode = 'P0001';
    end if;
    cleaned := cleaned || jsonb_build_array(jsonb_build_object(
      'name', cname,
      'qty', cqty,
      'printingId', nullif(left(btrim(coalesce(c ->> 'printingId', '')), 64), '')));
  end loop;
  return cleaned;
end;
$$;

-- "3 cards", "1 card", from a cleaned list.
create or replace function social_private.loan_cards_phrase(p_cards jsonb)
returns text
language sql
immutable
set search_path = ''
as $$
  select case n when 1 then '1 card' else n || ' cards' end
  from (select coalesce(sum((c ->> 'qty')::int), 0)::int as n from jsonb_array_elements(coalesce(p_cards, '[]')) c) s;
$$;

-- The lender sends a loan to a friend, or sends it again with the cards still out. A new loan tells
-- the friend. Answers the loan's id.
create or replace function public.upsert_loan(
  p_client_id text, p_borrower uuid, p_cards jsonb, p_back_by date default null,
  p_game_night boolean default false, p_note text default null, p_lent_at timestamptz default null)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me_with_profile();
  cleaned jsonb;
  loan uuid;
  is_new boolean;
  clean_note text := nullif(left(btrim(coalesce(p_note, '')), 200), '');
begin
  if p_borrower is null or p_borrower = uid then
    raise exception 'self' using errcode = 'P0001';
  end if;
  if not social_private.are_friends(uid, p_borrower) then
    raise exception 'not_a_friend' using errcode = 'P0001';
  end if;
  if coalesce(char_length(p_client_id), 0) not between 1 and 64 then
    raise exception 'bad_card' using errcode = 'P0001';
  end if;
  cleaned := social_private.clean_loan_cards(p_cards);
  if (select count(*) from public.loans where lender = uid and status = 'open') >= 500
      and not exists (select 1 from public.loans where lender = uid and client_id = p_client_id) then
    raise exception 'too_many_loans' using errcode = 'P0001';
  end if;
  insert into public.loans as l (lender, borrower, client_id, cards, back_by, game_night, note, lent_at)
  values (uid, p_borrower, p_client_id, cleaned, p_back_by, coalesce(p_game_night, false), clean_note,
          least(coalesce(p_lent_at, now()), now() + interval '1 day'))
  on conflict (lender, client_id) do update
    set borrower = excluded.borrower, cards = excluded.cards, back_by = excluded.back_by,
        game_night = excluded.game_night, note = excluded.note, status = 'open', returned_at = null,
        updated_at = now()
  returning l.id, (l.xmax = 0) into loan, is_new;
  if is_new then
    perform social_private.notify(
      p_borrower, 'friends',
      'Borrowed from ' || social_private.display_name(uid) || ': ' || social_private.loan_cards_phrase(cleaned),
      coalesce(clean_note,
        case when p_back_by is not null then 'Back by ' || to_char(p_back_by, 'FMDD Mon')
             when coalesce(p_game_night, false) then 'Back by next game night'
             else 'Tap to see them.' end),
      'friends', 'loan-' || loan
    );
  end if;
  return loan;
end;
$$;

-- The lender got everything back.
create or replace function public.mark_loan_returned(p_client_id text)
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
  where lender = uid and client_id = p_client_id and status = 'open';
end;
$$;

-- What the caller has borrowed and not given back yet, newest first.
create or replace function public.my_borrowed_loans()
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
    select jsonb_agg(jsonb_build_object(
      'id', l.id, 'clientId', l.client_id,
      'lender', social_private.profile_json(l.lender),
      'cards', l.cards,
      'backBy', to_char(l.back_by, 'YYYY-MM-DD'),
      'gameNight', l.game_night,
      'note', l.note,
      'lentAt', (extract(epoch from l.lent_at) * 1000)::bigint)
      order by l.lent_at desc)
    from (select * from public.loans where borrower = uid and status = 'open' order by lent_at desc limit 200) l
  ), '[]'::jsonb);
end;
$$;

-- The lender asks for the cards back: a notification to the friend. False when one went out for this
-- loan in the last 12 hours (nothing is sent), or the loan isn't open.
create or replace function public.remind_loan(p_client_id text)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me();
  l public.loans;
begin
  select * into l from public.loans where lender = uid and client_id = p_client_id and status = 'open' for update;
  if not found or (l.reminded_at is not null and l.reminded_at > now() - interval '12 hours') then
    return false;
  end if;
  update public.loans set reminded_at = now() where id = l.id;
  perform social_private.notify(
    l.borrower, 'friends',
    social_private.display_name(uid) || ' would like their cards back',
    social_private.loan_cards_phrase(l.cards)
      || case when l.back_by is not null then ' · back by ' || to_char(l.back_by, 'FMDD Mon')
              when l.game_night then ' · back by next game night' else '' end,
    'friends', 'loan-' || l.id
  );
  return true;
end;
$$;

revoke execute on function social_private.clean_loan_cards(jsonb) from public, anon, authenticated;
revoke execute on function social_private.loan_cards_phrase(jsonb) from public, anon, authenticated;
revoke execute on function public.upsert_loan(text, uuid, jsonb, date, boolean, text, timestamptz) from public, anon;
revoke execute on function public.mark_loan_returned(text) from public, anon;
revoke execute on function public.my_borrowed_loans() from public, anon;
revoke execute on function public.remind_loan(text) from public, anon;
grant execute on function public.upsert_loan(text, uuid, jsonb, date, boolean, text, timestamptz) to authenticated;
grant execute on function public.mark_loan_returned(text) to authenticated;
grant execute on function public.my_borrowed_loans() to authenticated;
grant execute on function public.remind_loan(text) to authenticated;
