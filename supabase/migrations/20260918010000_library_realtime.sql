-- Live updates: the apps subscribe to changes on their own account's library_items rows (Supabase
-- Realtime, postgres_changes filtered by user_id), and sync as soon as another device saves a deck
-- or binder instead of waiting for their next check.
--
-- Realtime applies the table's row-level security to each subscriber, using the subscriber's own
-- sign-in: a device only ever hears about its own account's rows.
--
-- Safe to run more than once.

do $$
begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'library_items'
  ) then
    alter publication supabase_realtime add table public.library_items;
  end if;
end;
$$;
