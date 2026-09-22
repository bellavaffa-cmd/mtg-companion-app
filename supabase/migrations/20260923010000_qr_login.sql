-- Signing in on the web by scanning a code with the phone.
--
-- The browser asks for a sign-in request and shows its code as a QR. The phone, already signed in,
-- scans it and approves; the qr-login Edge Function then mints a one-time sign-in token for the
-- phone's OWN account (it can't approve anyone else's) and leaves it here. The browser, and only the
-- browser that asked — it holds a secret the code never carries — collects that token once and
-- exchanges it for a session.
--
-- A request is good for two minutes, is used once, and is thrown away after. Row level security is
-- on with no policies: nothing reads this table directly, only the functions below (which run as
-- their owner) and the Edge Function (service role).

create table if not exists public.qr_login (
  -- What the QR carries: 128 bits of randomness, and all the phone needs to approve.
  code text primary key,
  -- The asking browser proves it's the same one by the secret behind this digest.
  secret_hash text not null,
  -- What the phone shows the user: "Chrome on Windows", as the browser described itself.
  browser text,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null,
  -- Set when the phone approves: whose account, and the one-time token for the browser.
  approved_by uuid references auth.users (id) on delete cascade,
  token_hash text,
  claimed_at timestamptz
);

alter table public.qr_login enable row level security;

create index if not exists qr_login_expires_at_idx on public.qr_login (expires_at);

/** How long a code is good for, and how many may be waiting at once. */
create or replace function public.qr_login_sweep() returns void language sql security definer set search_path = public as $$
  delete from public.qr_login where expires_at < now() - interval '5 minutes';
$$;

/**
 * A new sign-in request for a browser: its code (to show as a QR) and when it runs out. [p_secret_hash]
 * is the digest of a secret only that browser knows, which it hands back to collect the token.
 */
create or replace function public.start_qr_login(p_secret_hash text, p_browser text default null)
returns table (code text, expires_at timestamptz)
language plpgsql security definer set search_path = public as $$
declare
  v_code text;
  v_waiting int;
begin
  if p_secret_hash is null or length(p_secret_hash) < 32 then
    raise exception 'bad_request';
  end if;
  perform public.qr_login_sweep();
  -- Nothing here is worth much, but a runaway shouldn't be able to fill the table either.
  -- Alias the table: plain expires_at would mean this function's own answer column.
  select count(*) into v_waiting from public.qr_login q where q.expires_at > now();
  if v_waiting > 500 then
    raise exception 'too_many_requests';
  end if;
  -- 128 bits of randomness, as two uuids with their dashes taken out.
  v_code := replace(gen_random_uuid()::text, '-', '');
  insert into public.qr_login (code, secret_hash, browser, expires_at)
  values (v_code, p_secret_hash, left(coalesce(p_browser, ''), 80), now() + interval '2 minutes');
  return query select v_code, now() + interval '2 minutes';
end;
$$;

/**
 * What a signed-in phone is being asked to approve: which browser, and how long it has left. Null
 * when the code means nothing (mistyped, already used, or run out).
 */
create or replace function public.qr_login_request(p_code text)
returns table (browser text, expires_at timestamptz)
language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then
    raise exception 'not_signed_in';
  end if;
  return query
    select q.browser, q.expires_at from public.qr_login q
    where q.code = p_code and q.expires_at > now() and q.approved_by is null;
end;
$$;

/**
 * The browser collecting what it asked for: the one-time sign-in token, once the phone has approved
 * and only for the browser holding [p_secret]. Null while nobody has approved yet. The token is
 * handed over once — a second call gets nothing.
 */
create or replace function public.claim_qr_login(p_code text, p_secret text)
returns text
language plpgsql security definer set search_path = public as $$
declare
  v_token text;
begin
  -- Read it under a lock, then clear it: two browsers racing, only one gets the token.
  select q.token_hash into v_token
  from public.qr_login q
  where q.code = p_code
    and q.expires_at > now()
    and q.claimed_at is null
    and q.token_hash is not null
    and q.secret_hash = encode(sha256(convert_to(p_secret, 'UTF8')), 'hex')
  for update;
  if v_token is null then
    return null;
  end if;
  update public.qr_login set token_hash = null, claimed_at = now() where code = p_code;
  return v_token;
end;
$$;

-- The browser asks for a request and collects it without an account; the phone has to be signed in.
grant execute on function public.start_qr_login(text, text) to anon, authenticated;
grant execute on function public.claim_qr_login(text, text) to anon, authenticated;
grant execute on function public.qr_login_request(text) to authenticated;
revoke execute on function public.qr_login_sweep() from anon, authenticated;
