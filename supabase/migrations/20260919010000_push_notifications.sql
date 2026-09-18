-- MTG Companion — phone notifications for friend requests and trades.
--
-- Safe to run more than once.
--
-- Each device that allows notifications registers a token: an FCM registration token (Android) or
-- a Web Push subscription (browsers). When a friend request or a trade arrives or is answered, a
-- trigger hands the message and the recipient's tokens to the `push` Edge Function
-- (supabase/functions/push), which sends them. The function never reads the database; it only
-- reports tokens that no longer work, through prune_push_tokens().
--
-- Two Vault secrets connect the two, and notifications stay off until both exist:
--   push_function_url  — https://<project>.supabase.co/functions/v1/push
--   push_hook_secret   — shared with the function (its PUSH_HOOK_SECRET), so only these triggers can call it.

create extension if not exists pg_net with schema extensions;

create table if not exists public.push_tokens (
  -- The FCM registration token, or the Web Push endpoint URL.
  token text primary key check (char_length(token) <= 4096),
  user_id uuid not null references auth.users (id) on delete cascade,
  platform text not null check (platform in ('fcm', 'web')),
  -- Web Push: {endpoint, keys: {p256dh, auth}}.
  subscription jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists push_tokens_user_idx on public.push_tokens (user_id);
alter table public.push_tokens enable row level security;
revoke all on public.push_tokens from anon, authenticated;

create table if not exists public.notification_prefs (
  user_id uuid primary key references auth.users (id) on delete cascade,
  friends boolean not null default true,
  trades boolean not null default true,
  updated_at timestamptz not null default now()
);
alter table public.notification_prefs enable row level security;
revoke all on public.notification_prefs from anon, authenticated;

-- ============================================================================================
-- API
-- ============================================================================================

-- This device wants notifications for the caller. A token that belonged to another account (the
-- same phone, signed in as someone else) moves over. Each account keeps its 20 newest devices.
create or replace function public.register_push_token(p_platform text, p_token text, p_subscription jsonb default null)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me();
begin
  if p_platform not in ('fcm', 'web') or coalesce(p_token, '') = '' or char_length(p_token) > 4096 then
    raise exception 'bad_token' using errcode = 'P0001';
  end if;
  if p_platform = 'web' and (p_subscription is null or p_subscription -> 'keys' ->> 'p256dh' is null or p_subscription -> 'keys' ->> 'auth' is null) then
    raise exception 'bad_token' using errcode = 'P0001';
  end if;
  insert into public.push_tokens as t (token, user_id, platform, subscription)
  values (p_token, uid, p_platform, case when p_platform = 'web' then p_subscription end)
  on conflict (token) do update
    set user_id = excluded.user_id, platform = excluded.platform, subscription = excluded.subscription, updated_at = now();
  delete from public.push_tokens
  where user_id = uid and token not in (
    select token from public.push_tokens where user_id = uid order by updated_at desc limit 20
  );
end;
$$;

create or replace function public.unregister_push_token(p_token text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  delete from public.push_tokens where token = p_token and user_id = social_private.me();
end;
$$;

-- {friends, trades}: which kinds of notification the caller gets (both on until changed).
create or replace function public.notification_prefs()
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select jsonb_build_object('friends', coalesce(p.friends, true), 'trades', coalesce(p.trades, true))
  from (select auth.uid() as uid) me
  left join public.notification_prefs p on p.user_id = me.uid;
$$;

create or replace function public.set_notification_prefs(p_friends boolean, p_trades boolean)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := social_private.me();
begin
  insert into public.notification_prefs as n (user_id, friends, trades)
  values (uid, coalesce(p_friends, true), coalesce(p_trades, true))
  on conflict (user_id) do update set friends = excluded.friends, trades = excluded.trades, updated_at = now();
  return public.notification_prefs();
end;
$$;

-- The push function reports tokens the push services said are gone (app uninstalled, permission
-- withdrawn). Callable without an account, but only with the shared secret.
create or replace function public.prune_push_tokens(p_secret text, p_tokens text[])
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  expected text;
  n integer;
begin
  select decrypted_secret into expected from vault.decrypted_secrets where name = 'push_hook_secret';
  if expected is null or p_secret is distinct from expected then
    raise exception 'not_allowed' using errcode = 'P0001';
  end if;
  delete from public.push_tokens where token = any(coalesce(p_tokens, '{}'));
  get diagnostics n = row_count;
  return n;
end;
$$;

revoke execute on function public.register_push_token(text, text, jsonb) from public, anon;
revoke execute on function public.unregister_push_token(text) from public, anon;
revoke execute on function public.notification_prefs() from public, anon;
revoke execute on function public.set_notification_prefs(boolean, boolean) from public, anon;
grant execute on function public.register_push_token(text, text, jsonb) to authenticated;
grant execute on function public.unregister_push_token(text) to authenticated;
grant execute on function public.notification_prefs() to authenticated;
grant execute on function public.set_notification_prefs(boolean, boolean) to authenticated;
revoke execute on function public.prune_push_tokens(text, text[]) from public;
grant execute on function public.prune_push_tokens(text, text[]) to anon, authenticated;

-- ============================================================================================
-- Sending
-- ============================================================================================

-- Sends [p_title]/[p_body] to every device of [p_user] — unless they turned [p_kind] off, have no
-- devices, or notifications aren't set up. [p_open] is the screen a tap opens ('friends' or
-- 'trades'); [p_tag] makes a newer notification about the same thing replace the older one.
-- Never fails the change that triggered it: a problem here only costs the notification.
create or replace function social_private.notify(p_user uuid, p_kind text, p_title text, p_body text, p_open text, p_tag text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  url text;
  secret text;
  tokens jsonb;
begin
  if not coalesce((select case when p_kind = 'friends' then n.friends else n.trades end
                   from public.notification_prefs n where n.user_id = p_user), true) then
    return;
  end if;
  select coalesce(jsonb_agg(jsonb_build_object('platform', t.platform, 'token', t.token, 'subscription', t.subscription)), '[]')
  into tokens
  from public.push_tokens t where t.user_id = p_user;
  if jsonb_array_length(tokens) = 0 then
    return;
  end if;
  select decrypted_secret into url from vault.decrypted_secrets where name = 'push_function_url';
  select decrypted_secret into secret from vault.decrypted_secrets where name = 'push_hook_secret';
  if url is null or secret is null then
    return;
  end if;
  perform net.http_post(
    url := url,
    body := jsonb_build_object('tokens', tokens, 'title', p_title, 'body', p_body, 'open', p_open, 'tag', p_tag),
    headers := jsonb_build_object('Content-Type', 'application/json', 'x-push-secret', secret),
    timeout_milliseconds := 10000
  );
exception when others then
  raise warning 'notification not sent: %', sqlerrm;
end;
$$;

create or replace function social_private.display_name(uid uuid)
returns text
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce((select p.display_name from public.profiles p where p.user_id = uid), 'Someone');
$$;

create or replace function social_private.cards_phrase(cards jsonb)
returns text
language sql
immutable
set search_path = ''
as $$
  select case n when 1 then '1 card' else n || ' cards' end
  from (select coalesce(sum((c ->> 'quantity')::int), 0)::int as n from jsonb_array_elements(coalesce(cards, '[]')) c) s;
$$;

create or replace function social_private.on_friendship_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if tg_op = 'INSERT' and new.status = 'pending' then
    perform social_private.notify(
      new.addressee, 'friends',
      social_private.display_name(new.requester) || ' wants to be friends',
      'Tap to answer.',
      'friends', 'friend-' || new.requester
    );
  elsif tg_op = 'UPDATE' and old.status = 'pending' and new.status = 'accepted' then
    perform social_private.notify(
      new.requester, 'friends',
      social_private.display_name(new.addressee) || ' accepted your friend request',
      'You can now see what each of you shares.',
      'friends', 'friend-' || new.addressee
    );
  end if;
  return null;
end;
$$;

drop trigger if exists friendships_notify on public.friendships;
create trigger friendships_notify
  after insert or update of status on public.friendships
  for each row execute function social_private.on_friendship_change();

create or replace function social_private.on_trade_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  what text;
begin
  if tg_op = 'INSERT' then
    what := concat_ws(' · ',
      case when jsonb_array_length(new.want) > 0 then 'Asks for ' || social_private.cards_phrase(new.want) end,
      case when jsonb_array_length(new.give) > 0 then 'offers ' || social_private.cards_phrase(new.give) end);
    perform social_private.notify(
      new.to_user, 'trades',
      social_private.display_name(new.from_user) || case when new.reply_to is null then ' sent you a trade' else ' made a counter-offer' end,
      coalesce(nullif(new.message, ''), what),
      'trades', 'trade-' || new.id
    );
  elsif old.status = 'open' and new.status in ('accepted', 'declined') then
    perform social_private.notify(
      new.from_user, 'trades',
      social_private.display_name(new.to_user) || case when new.status = 'accepted' then ' accepted your trade' else ' declined your trade' end,
      coalesce(nullif(new.reply, ''), case when new.status = 'accepted' then 'Swap the cards, then update your binders.' else 'Maybe another time.' end),
      'trades', 'trade-' || new.id
    );
  end if;
  return null;
end;
$$;

drop trigger if exists trades_notify on public.trades;
create trigger trades_notify
  after insert or update of status on public.trades
  for each row execute function social_private.on_trade_change();

revoke execute on all functions in schema social_private from public, anon, authenticated;
