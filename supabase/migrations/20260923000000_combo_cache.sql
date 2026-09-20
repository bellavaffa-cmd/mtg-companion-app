-- What Commander Spellbook said about a card, held for the relay (supabase/functions/api-relay).
--
-- The relay runs in a fresh isolate per request, so anything it holds in memory is gone by the next
-- call; this table is where a card lookup is actually remembered. It holds no one's data — just a
-- public answer about a card — and is read and written only by the relay, which uses the service
-- role. Row level security is on with no policies, so no client can read or write it.

create table if not exists public.combo_cache (
  -- The lookup as asked: Spellbook's query and the number of combos wanted.
  key text primary key,
  -- Spellbook's reply, as it came, to hand back unchanged.
  body text not null,
  fetched_at timestamptz not null default now()
);

alter table public.combo_cache enable row level security;

-- For dropping what's a day old.
create index if not exists combo_cache_fetched_at_idx on public.combo_cache (fetched_at);
