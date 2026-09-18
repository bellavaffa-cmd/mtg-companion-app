-- Library sync, part 2: compare-and-swap pushes.
--
-- push_library_items() overwrites a row with any newer edit, judged by the devices' own clocks. Two
-- devices that merged from the same version could therefore overwrite each other's merge, and a
-- device whose push answer got lost couldn't tell whether its edit had landed.
--
-- push_library_items_v2() only overwrites a row that is still at the version the device merged from
-- (base_edited_ms, the edit time of that version; null = the device has never seen this item, so
-- only a new row is written). Anything else is skipped, and the device reads the row back and merges.
-- It answers with exactly which items it wrote. Each row also keeps base_edited_ms, so a device can
-- tell that another one built on its write.
--
-- Safe to run more than once. Apps from v1.85.x keep using push_library_items(), which now clears
-- base_edited_ms on every write, so a row they overwrite never claims to be built on an older one.

alter table public.library_items add column if not exists base_edited_ms bigint;

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
begin
  if auth.uid() is null then
    raise exception 'not signed in';
  end if;
  for item in select value from jsonb_array_elements(items) loop
    is_deleted := coalesce((item ->> 'deleted')::boolean, false);
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
      -- Only over the version this device merged from. A null base never matches an existing row.
      where li.edited_ms = base;
    get diagnostics n = row_count;
    if n > 0 then
      written := written || jsonb_build_array(jsonb_build_object('kind', item ->> 'kind', 'id', item ->> 'id'));
    end if;
  end loop;
  return written;
end;
$$;

revoke execute on function public.push_library_items_v2(jsonb) from public, anon;
grant execute on function public.push_library_items_v2(jsonb) to authenticated;

-- PostgREST caches the schema; tell it about the new column and function.
notify pgrst, 'reload schema';
