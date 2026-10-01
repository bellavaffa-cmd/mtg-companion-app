-- Manabind — reports from the tester app (Manabind Tester, the beta build).
--
-- A tester reports a problem, an idea, how a feature went, a scan that came out wrong, or a crash,
-- and it lands here with what the app was doing at the time, for the developer to read. The released
-- app never writes to this table.
--
-- Rows come only from a signed-in account and go one way: an account can add its own reports and
-- can't read, change or remove any — its own included. They're read from the dashboard or the CLI.
--
-- Safe to re-run: every statement is idempotent.

create table if not exists public.tester_reports (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  created_at timestamptz not null default clock_timestamp(),
  -- When the phone wrote it, which is earlier than created_at for one written offline or by a crash.
  reported_at_ms bigint not null default 0,
  kind text not null check (kind in ('bug', 'idea', 'feedback', 'checklist', 'scan', 'crash')),
  note text not null default '' check (char_length(note) <= 4000),
  screen text not null default '' check (char_length(screen) <= 200),
  -- The tester build number (tester-N) and the app's version name.
  build integer not null default 0,
  app_version text not null default '' check (char_length(app_version) <= 60),
  device text not null default '' check (char_length(device) <= 200),
  -- Build, phone, sync state and whatever the kind adds (a crash's stack trace, a scan's reading).
  context jsonb not null default '{}'::jsonb check (pg_column_size(context) <= 65536),
  -- The last things the app did before the report, oldest first.
  log jsonb not null default '[]'::jsonb check (pg_column_size(log) <= 131072),
  -- A JPEG of the screen (or the scanned frame), base64. Kept small by the app; capped here.
  screenshot text check (screenshot is null or char_length(screenshot) <= 1500000),
  -- For the developer to mark what's been dealt with.
  status text not null default 'new' check (status in ('new', 'seen', 'fixed', 'wontfix'))
);

create index if not exists tester_reports_newest_idx on public.tester_reports (created_at desc);
create index if not exists tester_reports_status_idx on public.tester_reports (status, created_at desc);

-- Row-level security: an account may add its own reports, and that is all.
alter table public.tester_reports enable row level security;

drop policy if exists "tester_reports_insert_own" on public.tester_reports;
create policy "tester_reports_insert_own" on public.tester_reports
  for insert to authenticated with check ((select auth.uid()) = user_id and status = 'new');

revoke all on public.tester_reports from anon, authenticated;
grant insert on public.tester_reports to authenticated;
