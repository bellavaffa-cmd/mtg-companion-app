-- MTG Companion — per-item library sync (decks + binders), one row per item.
--
-- Run once in the Supabase dashboard: SQL Editor → New query → paste this whole file → Run.
-- Safe to re-run: every statement is idempotent.
--
-- Each deck or binder is stored whole as JSON in `data`, so fields a newer app version adds survive
-- older clients. The apps merge two devices' edits to the same deck card by card before pushing the
-- result (see ItemMerge.kt / mergeItems.ts); the server just keeps the newer write, so a device that
-- hasn't merged yet can't clobber one that has. `edited_ms` is the wall-clock time of the
-- edit on the device that made it, and push_library_items() only overwrites a row with a newer edit.
-- Deleting an item leaves a tombstone (deleted = true, data = null) so other devices delete it too.

create table if not exists public.library_items (
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  kind text not null check (kind in ('deck', 'collection')),
  id text not null,
  data jsonb,
  edited_ms bigint not null default 0,
  deleted boolean not null default false,
  -- Set by the server on every write; devices pull everything newer than the last value they saw.
  server_updated_at timestamptz not null default clock_timestamp(),
  primary key (user_id, kind, id)
);

create index if not exists library_items_pull_idx on public.library_items (user_id, server_updated_at);

-- Row-level security: every row is private to the account that owns it.
alter table public.library_items enable row level security;

drop policy if exists "library_items_select_own" on public.library_items;
create policy "library_items_select_own" on public.library_items
  for select to authenticated using ((select auth.uid()) = user_id);

drop policy if exists "library_items_insert_own" on public.library_items;
create policy "library_items_insert_own" on public.library_items
  for insert to authenticated with check ((select auth.uid()) = user_id);

drop policy if exists "library_items_update_own" on public.library_items;
create policy "library_items_update_own" on public.library_items
  for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

drop policy if exists "library_items_delete_own" on public.library_items;
create policy "library_items_delete_own" on public.library_items
  for delete to authenticated using ((select auth.uid()) = user_id);

revoke all on public.library_items from anon;
grant select, insert, update, delete on public.library_items to authenticated;

-- Stamp server time on every insert/update, so the pull cursor never misses a write.
create or replace function public.library_items_touch()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.server_updated_at := clock_timestamp();
  return new;
end;
$$;

drop trigger if exists library_items_touch on public.library_items;
create trigger library_items_touch
  before insert or update on public.library_items
  for each row execute function public.library_items_touch();

-- Push a batch of local changes: [{kind, id, data, edited_ms, deleted}, ...].
-- Runs as the caller (security invoker), so row-level security still applies.
-- A change older than what the server already has is skipped; the device picks up the newer
-- version on its next pull. Returns how many rows were written.
create or replace function public.push_library_items(items jsonb)
returns integer
language plpgsql
security invoker
set search_path = ''
as $$
declare
  item jsonb;
  is_deleted boolean;
  written integer := 0;
  n integer;
begin
  if auth.uid() is null then
    raise exception 'not signed in';
  end if;
  for item in select value from jsonb_array_elements(items) loop
    is_deleted := coalesce((item ->> 'deleted')::boolean, false);
    insert into public.library_items as li (user_id, kind, id, data, edited_ms, deleted)
    values (
      auth.uid(),
      item ->> 'kind',
      item ->> 'id',
      case when is_deleted then null else item -> 'data' end,
      coalesce((item ->> 'edited_ms')::bigint, 0),
      is_deleted
    )
    on conflict (user_id, kind, id) do update
      set data = excluded.data,
          edited_ms = excluded.edited_ms,
          deleted = excluded.deleted
      where li.edited_ms <= excluded.edited_ms;
    get diagnostics n = row_count;
    written := written + n;
  end loop;
  return written;
end;
$$;

revoke execute on function public.push_library_items(jsonb) from public, anon;
grant execute on function public.push_library_items(jsonb) to authenticated;
