-- Manabind — moving a trade's cards happens once per side.
--
-- "Update my binders" marks the caller's side of an accepted trade as applied, then moves the
-- cards. mark_trade_applied used to succeed every time, so a retry after a failed refresh, or a
-- second device still showing the button, moved the cards again. It now says whether this call
-- is the one that marked it: true means go ahead and move the cards, false means that side was
-- already done (or the trade isn't an accepted one of the caller's), so leave the binders alone.
--
-- The apps treat a null result (this function before the change) as "go ahead", so they work
-- against either version.
--
-- Safe to re-run: the function is dropped and made again, with its grants.

drop function if exists public.mark_trade_applied(uuid);

create function public.mark_trade_applied(p_trade uuid)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me();
  changed integer;
begin
  -- Only a side not yet applied is updated, and the row lock makes two calls at once take turns:
  -- the second finds its side already applied and updates nothing.
  update public.trades
  set from_applied = from_applied or from_user = uid,
      to_applied = to_applied or to_user = uid,
      updated_at = now()
  where id = p_trade and status = 'accepted'
    and ((from_user = uid and not from_applied) or (to_user = uid and not to_applied));
  get diagnostics changed = row_count;
  return changed > 0;
end;
$$;

revoke execute on function public.mark_trade_applied(uuid) from public, anon;
grant execute on function public.mark_trade_applied(uuid) to authenticated;
