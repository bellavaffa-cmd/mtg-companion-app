-- A push may not empty a whole kind at once.
--
-- Deleting an item nulls its data (…_library_sync_cas.sql), so a deletion is not recoverable: there
-- is no copy of the card list left anywhere on the server. That makes one kind of client bug
-- unusually expensive — a device whose local library has gone missing (storage cleared, a backup
-- half restored, a browser's data wiped) reports every item it no longer holds as deleted, and the
-- account is emptied. The apps now tell a deletion they were told about from a library that simply
-- isn't there (SyncCore.notePending / cloudSync.ts), but an older app, or one that hasn't updated
-- yet, still has the old behaviour. This is the backstop for those.
--
-- The rule: if a push would delete every deck, or every binder, the account still has — two or more
-- of them — those deletions are skipped. Deleting by hand pushes each item as it goes, so a whole
-- kind going in one push is not something a person did through the app. v2 leaves the skipped ones
-- out of what it answers, which is what the apps already read as "read those rows back", so the
-- device fills up again instead.
--
-- Deleting your only deck still works: one is not a wipe, and losing one item to a bad sync is not
-- the same as losing a collection built over months.

create or replace function public.wholesale_delete_kinds(items jsonb)
returns text[]
language sql
security invoker
set search_path = ''
as $$
  with live as (
    select li.kind, li.id from public.library_items li
    where li.user_id = auth.uid() and not li.deleted
  ),
  gone as (
    select i ->> 'kind' as kind, i ->> 'id' as id
    from jsonb_array_elements(items) as i
    where coalesce((i ->> 'deleted')::boolean, false)
  )
  select coalesce(array_agg(k.kind), '{}')
  from (
    select l.kind, count(*) as live, count(g.id) as going
    from live l left join gone g on g.kind = l.kind and g.id = l.id
    group by l.kind
  ) k
  where k.live >= 2 and k.going = k.live;
$$;

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
  wiping text[];
begin
  if auth.uid() is null then
    raise exception 'not signed in';
  end if;
  wiping := public.wholesale_delete_kinds(items);
  for item in select value from jsonb_array_elements(items) loop
    is_deleted := coalesce((item ->> 'deleted')::boolean, false);
    -- The whole of a kind going at once is a library that's been lost, not a decision.
    continue when is_deleted and (item ->> 'kind') = any(wiping);
    insert into public.library_items as li (user_id, kind, id, data, edited_ms, deleted, base_edited_ms)
    values (
      auth.uid(),
      item ->> 'kind',
      item ->> 'id',
      case when is_deleted then null else item -> 'data' end,
      coalesce((item ->> 'edited_ms')::bigint, 0),
      is_deleted,
      null
    )
    on conflict (user_id, kind, id) do update
      set data = excluded.data,
          edited_ms = excluded.edited_ms,
          deleted = excluded.deleted,
          base_edited_ms = null
      where li.edited_ms <= excluded.edited_ms;
    get diagnostics n = row_count;
    written := written + n;
  end loop;
  return written;
end;
$$;

create or replace function public.push_library_items_v2(items jsonb)
returns jsonb
language plpgsql
security invoker
set search_path = ''
as $$
declare
  item jsonb;
  is_deleted boolean;
  base bigint;
  written jsonb := '[]'::jsonb;
  n integer;
  wiping text[];
begin
  if auth.uid() is null then
    raise exception 'not signed in';
  end if;
  wiping := public.wholesale_delete_kinds(items);
  for item in select value from jsonb_array_elements(items) loop
    is_deleted := coalesce((item ->> 'deleted')::boolean, false);
    -- Left out of what we answer as well as skipped, so the device reads those rows back and has
    -- its decks again rather than quietly agreeing they're gone.
    continue when is_deleted and (item ->> 'kind') = any(wiping);
    base := (item ->> 'base_edited_ms')::bigint;
    insert into public.library_items as li (user_id, kind, id, data, edited_ms, deleted, base_edited_ms)
    values (
      auth.uid(),
      item ->> 'kind',
      item ->> 'id',
      case when is_deleted then null else item -> 'data' end,
      coalesce((item ->> 'edited_ms')::bigint, 0),
      is_deleted,
      base
    )
    on conflict (user_id, kind, id) do update
      set data = excluded.data,
          edited_ms = excluded.edited_ms,
          deleted = excluded.deleted,
          base_edited_ms = excluded.base_edited_ms
      where li.edited_ms = base;
    get diagnostics n = row_count;
    if n > 0 then
      written := written || jsonb_build_array(jsonb_build_object('kind', item ->> 'kind', 'id', item ->> 'id'));
    end if;
  end loop;
  return written;
end;
$$;

revoke execute on function public.wholesale_delete_kinds(jsonb) from anon;
grant execute on function public.wholesale_delete_kinds(jsonb) to authenticated;
