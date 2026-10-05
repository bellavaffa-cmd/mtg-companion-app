-- Manabind — anonymous feature-usage counts, for the owner to see which screens and features get used.
--
-- Both apps count screens opened ("screen_<name>") and a short fixed list of actions (deck_created,
-- card_scanned…) per day on the device, and send each finished day here at most once a day. A row
-- holds a day, an anonymous random install id (the app makes a new one every 90 days), the platform,
-- the app version, the event's name and how many times it happened. No account, card names, deck
-- contents, free text or IP address is stored. Settings › Privacy in either app turns it off.
--
-- Rows are written only through record_usage below, by anyone with the app's key (signed in or
-- not: the account isn't part of it); the table itself is closed to the API and read from the
-- dashboard or the CLI (supabase/queries/usage_report.sql). Sending the same day again adds to it.
--
-- Safe to re-run: every statement is idempotent.

create table if not exists public.usage_counts (
  day date not null,
  install_id text not null check (install_id ~ '^[0-9a-f]{32}$'),
  platform text not null check (platform in ('web', 'android')),
  -- The version that last sent this row ("3.5.17", "3.5.17-tester120"); empty when unknown.
  app_version text not null default '' check (char_length(app_version) <= 40),
  event text not null check (event ~ '^[a-z_]{1,40}$'),
  count integer not null check (count between 1 and 1000000),
  primary key (day, install_id, platform, event)
);
create index if not exists usage_counts_event_idx on public.usage_counts (event, day);
alter table public.usage_counts enable row level security;
revoke all on public.usage_counts from anon, authenticated;

-- One install's counts for one day: {"screen_decks": 4, "deck_created": 1}. Checked whole before
-- anything is written, so a bad call stores nothing.
create or replace function public.record_usage(
  p_install text, p_platform text, p_version text, p_day date, p_counts jsonb)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  k text;
  v jsonb;
  n integer := 0;
  v_version text := coalesce(p_version, '');
begin
  if p_install is null or p_install !~ '^[0-9a-f]{32}$' then
    raise exception 'bad_install' using errcode = 'P0001';
  end if;
  if p_platform is null or p_platform not in ('web', 'android') then
    raise exception 'bad_platform' using errcode = 'P0001';
  end if;
  if v_version !~ '^[0-9A-Za-z._+-]{0,40}$' then
    raise exception 'bad_version' using errcode = 'P0001';
  end if;
  -- The last 8 days (a phone keeps a day a week), and a day ahead for time zones.
  if p_day is null or p_day < current_date - 8 or p_day > current_date + 1 then
    raise exception 'bad_day' using errcode = 'P0001';
  end if;
  if p_counts is null or jsonb_typeof(p_counts) <> 'object' then
    raise exception 'bad_counts' using errcode = 'P0001';
  end if;
  for k, v in select * from jsonb_each(p_counts) loop
    n := n + 1;
    if n > 100 or k !~ '^[a-z_]{1,40}$' or jsonb_typeof(v) <> 'number' then
      raise exception 'bad_counts' using errcode = 'P0001';
    end if;
    -- A whole number from 1 to 10000.
    if (v #>> '{}') !~ '^[0-9]{1,5}$' or (v #>> '{}')::integer not between 1 and 10000 then
      raise exception 'bad_counts' using errcode = 'P0001';
    end if;
  end loop;
  if n = 0 then
    raise exception 'bad_counts' using errcode = 'P0001';
  end if;

  insert into public.usage_counts as u (day, install_id, platform, app_version, event, count)
  select p_day, p_install, p_platform, v_version, e.key, (e.value #>> '{}')::integer
  from jsonb_each(p_counts) as e
  on conflict (day, install_id, platform, event) do update
    set count = least(u.count + excluded.count, 1000000),
        app_version = excluded.app_version;
end;
$$;

revoke execute on function public.record_usage(text, text, text, date, jsonb) from public;
grant execute on function public.record_usage(text, text, text, date, jsonb) to anon, authenticated;
