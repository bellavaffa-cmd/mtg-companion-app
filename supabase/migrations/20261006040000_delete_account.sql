-- Delete my account: the caller's own account and everything the server holds about them.
--
-- Google Play requires an app with accounts to let people delete them from inside the app and from
-- the web (manabind.com/delete-account). Both apps call public.delete_my_account() and then sign
-- out locally. An app talking to a server without this migration gets PGRST202 (function not found)
-- and says deletion isn't available yet, rather than pretending it worked.
--
-- What goes, for the caller only (auth.uid()):
--   library_items        their decks and binders (synced copies)
--   push_tokens, notification_prefs
--   profiles             and with it, by its foreign keys, everything social: friendships, pods
--                        they own and their pod memberships, pod games they recorded, shares, link
--                        shares, matches they hosted and seats they took, trades either side,
--                        loans either side, blocks, reports either side, conversations and
--                        messages, trade ratings, social activity
--   tester_reports       reports sent from the Manabind Tester app
--   qr_login             web sign-ins they approved
--   avatars bucket       their profile pictures, where the server allows it (see below)
--   auth.users           the account itself (email, password hash, sessions, refresh tokens)
--
-- Every table above already cascades from auth.users, so the last delete alone would do; the
-- explicit deletes say what goes and still work if a cascade is ever changed. Tables a project may
-- not have yet (a migration not run) are skipped by name, and so is any table added later by its
-- own migration as long as it cascades from auth.users or profiles — e.g. usage counts.
--
-- Other people's copies are not touched: a card a friend received in a trade and applied to their
-- own binder is in their library, not this account's.
--
-- Storage: Supabase may refuse a direct SQL delete on storage.objects (only the Storage API may
-- remove files). The apps therefore delete the caller's avatars through the Storage API first; the
-- delete here is a backstop and never fails the whole call. A file left behind sits under
-- avatars/<deleted user id>/ with an unguessable name and nothing pointing at it; the owner can
-- clear such folders from the dashboard.
--
-- NOT applied automatically. The owner runs it by hand in the Supabase SQL editor (or
-- `npx supabase db push`) after reviewing it. It contains DELETE statements by design.

create or replace function public.delete_my_account()
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := auth.uid();
  t record;
begin
  if uid is null then
    raise exception 'not_signed_in' using errcode = 'P0001';
  end if;

  -- (table, column holding the caller's id), most dependent first.
  for t in
    select * from (values
      ('library_items', 'user_id'),
      ('push_tokens', 'user_id'),
      ('notification_prefs', 'user_id'),
      ('tester_reports', 'user_id'),
      ('qr_login', 'approved_by'),
      ('social_events', 'actor'),
      ('trade_ratings', 'rater'),
      ('trade_ratings', 'rated'),
      ('messages', 'sender'),
      ('conversations', 'user_a'),
      ('conversations', 'user_b'),
      ('reports', 'reporter'),
      ('reports', 'reported'),
      ('blocks', 'blocker'),
      ('blocks', 'blocked'),
      ('loans', 'lender'),
      ('loans', 'borrower'),
      ('trades', 'from_user'),
      ('trades', 'to_user'),
      ('match_players', 'user_id'),
      ('matches', 'host'),
      ('library_share_all', 'owner'),
      ('library_share_all', 'viewer'),
      ('library_shares', 'owner'),
      ('pod_games', 'recorded_by'),
      ('pod_members', 'user_id'),
      ('pods', 'owner'),
      ('friendships', 'requester'),
      ('friendships', 'addressee'),
      ('profiles', 'user_id')
    ) as v(tbl, col)
  loop
    if to_regclass('public.' || t.tbl) is not null then
      execute format('delete from public.%I where %I = $1', t.tbl, t.col) using uid;
    end if;
  end loop;

  -- Backstop for profile pictures (see the note above): never fails the deletion.
  begin
    delete from storage.objects
    where bucket_id = 'avatars' and (storage.foldername(name))[1] = uid::text;
  exception when others then
    raise warning 'avatars not removed for %: %', uid, sqlerrm;
  end;

  -- The account itself; anything not listed above that references it goes by cascade.
  delete from auth.users where id = uid;
end;
$$;

revoke execute on function public.delete_my_account() from public, anon;
grant execute on function public.delete_my_account() to authenticated;
