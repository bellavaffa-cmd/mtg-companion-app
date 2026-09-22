-- start_qr_login read "expires_at > now()" as its own answer column rather than the table's, so
-- every request came back "column reference expires_at is ambiguous" and no browser could ask for a
-- code. The same fix is in …_qr_login.sql for databases built from scratch; this repeats it for the
-- one that already has the first version.

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

grant execute on function public.start_qr_login(text, text) to anon, authenticated;
