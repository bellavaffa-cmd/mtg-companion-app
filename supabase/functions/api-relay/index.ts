// api-relay: fetches, on the web app's behalf, the few MTG sources that don't allow browser
// (cross-origin) requests. Scryfall, EDHREC and MTGJSON all send `Access-Control-Allow-Origin: *`
// and are called directly; Commander Spellbook's API and the news RSS feeds are not, so they come
// through here.
//
// Deliberately not an open proxy: every route maps to one fixed upstream URL, and only the query
// parameters / body that route needs are passed on.
//
//   GET  /api-relay/news                        merged headlines from the RSS feeds, newest first
//   GET  /api-relay/combos/variants?q=&limit=   Commander Spellbook combo search (held for a day)
//
// Both combo lookups are held in the combo_cache table, since each request may run in a fresh isolate.
//   POST /api-relay/combos/find-my-combos       combos a decklist contains or is one card short of (held for a day)
//
// Deployed with JWT verification on, so callers send the project's anon key as the bearer token.

// manabind.com is the web app's home; the github.io address it moved from still redirects there.
const ALLOWED_ORIGINS = [/^https:\/\/(www\.)?manabind\.com$/, /^https:\/\/bellavaffa-cmd\.github\.io$/, /^http:\/\/localhost(:\d+)?$/, /^http:\/\/127\.0\.0\.1(:\d+)?$/]

const SPELLBOOK = 'https://backend.commanderspellbook.com'
const NEWS_FEEDS = [
  { url: 'https://mtgazone.com/news/feed/', source: 'MTG Arena Zone' },
  { url: 'https://articles.starcitygames.com/feed/', source: 'Star City Games' },
]
const USER_AGENT = 'MtgCompanionRelay/1.0 (+https://github.com/bellavaffa-cmd/mtg-companion-app)'
const MAX_BODY_BYTES = 256 * 1024
const NEWS_TTL_MS = 15 * 60 * 1000
// A card's combos change when cards are printed or banned, so a day-old answer is a good answer.
const COMBOS_TTL_MS = 24 * 60 * 60 * 1000
// Card lookups held in this isolate. Each request may get a fresh one, so the table below is what
// actually remembers a card; this only saves a repeat inside one warm isolate.
const COMBOS_KEEP = 500
// Where a card lookup is remembered across requests (see migrations/..._combo_cache.sql).
const COMBOS_TABLE = 'combo_cache'
// One write in this many sweeps out what's a day old, so the table stays small without a cron job.
const COMBOS_SWEEP_ODDS = 20

/** An environment variable, under Deno where this runs and under Node where the test drives it. */
function env(name: string): string | undefined {
  const holder = globalThis as { Deno?: { env: { get: (k: string) => string | undefined } }; process?: { env: Record<string, string | undefined> } }
  return holder.Deno?.env.get(name) ?? holder.process?.env[name]
}

/**
 * The project's own REST API and a key that may use it. Edge functions are handed these; the secret
 * key is this function's own (never a caller's), and it only ever touches [COMBOS_TABLE].
 */
function db(): { url: string; key: string } | null {
  const url = env('SUPABASE_URL')
  const key = env('SUPABASE_SERVICE_ROLE_KEY')
    ?? (() => { try { return JSON.parse(env('SUPABASE_SECRET_KEYS') ?? '{}').default as string | undefined } catch { return undefined } })()
  return url && key ? { url, key } : null
}

function corsHeaders(req: Request): Record<string, string> {
  const origin = req.headers.get('Origin') ?? ''
  const headers: Record<string, string> = {
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    'Access-Control-Allow-Headers': 'authorization, apikey, content-type, x-client-info',
    'Access-Control-Max-Age': '86400',
    Vary: 'Origin',
  }
  if (ALLOWED_ORIGINS.some((re) => re.test(origin))) headers['Access-Control-Allow-Origin'] = origin
  return headers
}

function json(req: Request, status: number, body: unknown, extra: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders(req), 'Content-Type': 'application/json', ...extra },
  })
}

// ---- News ----

interface NewsItem { title: string; link: string; source: string; publishedAt: number | null }

let newsCache: { at: number; items: NewsItem[] } | null = null

function decodeEntities(s: string): string {
  return s
    .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, '$1')
    .replace(/&#(\d+);/g, (_, n) => String.fromCodePoint(Number(n)))
    .replace(/&#x([0-9a-f]+);/gi, (_, n) => String.fromCodePoint(parseInt(n, 16)))
    .replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&')
    .trim()
}

function tag(item: string, name: string): string | null {
  const m = item.match(new RegExp(`<${name}(?:\\s[^>]*)?>([\\s\\S]*?)</${name}>`, 'i'))
  return m ? decodeEntities(m[1]) : null
}

async function fetchFeed(url: string, source: string, limit: number): Promise<NewsItem[]> {
  const res = await fetch(url, { headers: { 'User-Agent': USER_AGENT, Accept: 'application/rss+xml, application/xml' } })
  if (!res.ok) return []
  const xml = await res.text()
  const items: NewsItem[] = []
  for (const m of xml.matchAll(/<item\b[\s\S]*?<\/item>/gi)) {
    const title = tag(m[0], 'title')
    const link = tag(m[0], 'link')
    if (!title || !link || !/^https?:\/\//.test(link)) continue
    const date = tag(m[0], 'pubDate')
    const at = date ? Date.parse(date) : NaN
    items.push({ title, link, source, publishedAt: Number.isNaN(at) ? null : at })
    if (items.length >= limit) break
  }
  return items
}

async function news(req: Request): Promise<Response> {
  if (!newsCache || Date.now() - newsCache.at > NEWS_TTL_MS) {
    const results = await Promise.all(NEWS_FEEDS.map((f) => fetchFeed(f.url, f.source, 8).catch(() => [])))
    const items = results.flat().sort((a, b) => (b.publishedAt ?? 0) - (a.publishedAt ?? 0))
    // Don't cache a total failure; the next request tries again.
    if (items.length > 0) newsCache = { at: Date.now(), items }
    else return json(req, 502, { error: 'News feeds are unavailable right now.' })
  }
  return json(req, 200, { items: newsCache.items }, { 'Cache-Control': 'public, max-age=900' })
}

// ---- Commander Spellbook ----

/**
 * What card lookups came back with, in this isolate. The table behind it is what holds an answer
 * between requests; this saves a second trip to the database when an isolate is reused.
 */
const comboCache = new Map<string, { at: number; body: string }>()
/** Lookups under way, so ten people opening the same card make one request upstream. */
const comboInFlight = new Map<string, Promise<string>>()

/** Drops what's a day old, then the least recently used, down to COMBOS_KEEP. */
function sweepCombos(now: number) {
  for (const [key, entry] of comboCache) if (now - entry.at > COMBOS_TTL_MS) comboCache.delete(key)
  // A Map keeps insertion order and a hit is re-inserted, so the oldest key is the coldest.
  while (comboCache.size > COMBOS_KEEP) comboCache.delete(comboCache.keys().next().value as string)
}

/** What the table holds for [key], if it was fetched within the day. Never throws: this is a shortcut. */
async function heldInDb(key: string): Promise<string | null> {
  const conn = db()
  if (!conn) return null
  try {
    const since = new Date(Date.now() - COMBOS_TTL_MS).toISOString()
    const query = `${COMBOS_TABLE}?select=body&key=eq.${encodeURIComponent(key)}&fetched_at=gte.${encodeURIComponent(since)}&limit=1`
    const res = await fetch(`${conn.url}/rest/v1/${query}`, {
      headers: { apikey: conn.key, Authorization: `Bearer ${conn.key}`, Accept: 'application/json' },
    })
    if (!res.ok) return null
    const rows = await res.json() as { body?: string }[]
    return rows[0]?.body ?? null
  } catch {
    return null
  }
}

/** Holds [body] for [key], and now and then drops what's a day old. Never throws. */
async function holdInDb(key: string, body: string): Promise<void> {
  const conn = db()
  if (!conn) return
  try {
    await fetch(`${conn.url}/rest/v1/${COMBOS_TABLE}`, {
      method: 'POST',
      headers: {
        apikey: conn.key,
        Authorization: `Bearer ${conn.key}`,
        'Content-Type': 'application/json',
        Prefer: 'resolution=merge-duplicates,return=minimal',
      },
      body: JSON.stringify({ key, body, fetched_at: new Date().toISOString() }),
    })
    if (Math.random() < 1 / COMBOS_SWEEP_ODDS) {
      const stale = new Date(Date.now() - COMBOS_TTL_MS).toISOString()
      await fetch(`${conn.url}/rest/v1/${COMBOS_TABLE}?fetched_at=lt.${encodeURIComponent(stale)}`, {
        method: 'DELETE',
        headers: { apikey: conn.key, Authorization: `Bearer ${conn.key}`, Prefer: 'return=minimal' },
      })
    }
  } catch {
    // The answer still goes back to the caller; it just isn't held.
  }
}

async function relaySpellbook(req: Request, upstream: string, init: RequestInit): Promise<Response> {
  const res = await fetch(upstream, {
    ...init,
    headers: { 'User-Agent': USER_AGENT, Accept: 'application/json', ...(init.headers ?? {}) },
  })
  const body = await res.text()
  return new Response(body, {
    status: res.status,
    headers: { ...corsHeaders(req), 'Content-Type': res.headers.get('Content-Type') ?? 'application/json' },
  })
}

async function comboVariants(req: Request, url: URL): Promise<Response> {
  const q = url.searchParams.get('q') ?? ''
  if (!q.trim() || q.length > 500) return json(req, 400, { error: 'Missing or oversized q.' })
  const limit = Math.min(100, Math.max(1, Number(url.searchParams.get('limit') ?? '30') || 30))
  // Kept as text in Postgres, which has no room for a NUL. The limit is digits, so putting it
  // first keeps one lookup from reading as another.
  const key = `${limit}:${q}`
  const now = Date.now()

  const held = comboCache.get(key)
  if (held && now - held.at <= COMBOS_TTL_MS) {
    // Re-inserting marks it as the most recently used, so a popular card outlives a one-off.
    comboCache.delete(key)
    comboCache.set(key, held)
    return combosResponse(req, held.body, 'hit')
  }

  const fromDb = await heldInDb(key)
  if (fromDb !== null) {
    comboCache.set(key, { at: now, body: fromDb })
    sweepCombos(now)
    return combosResponse(req, fromDb, 'db')
  }

  const upstream = `${SPELLBOOK}/variants?${new URLSearchParams({ q, limit: String(limit) })}`
  let request = comboInFlight.get(key)
  if (!request) {
    request = fetchCombos(upstream, key, now).finally(() => comboInFlight.delete(key))
    comboInFlight.set(key, request)
  }
  try {
    return combosResponse(req, await request, 'miss')
  } catch {
    // Spellbook refused or is down: say so the way the other routes do, and hold nothing.
    return json(req, 502, { error: 'Commander Spellbook is unavailable right now.' })
  }
}

/** Asks Spellbook and holds the answer. Only a 200 is held — an error is not an answer. */
async function fetchCombos(upstream: string, key: string, now: number): Promise<string> {
  const res = await fetch(upstream, { headers: { 'User-Agent': USER_AGENT, Accept: 'application/json' } })
  const body = await res.text()
  if (!res.ok) throw new Error(`Spellbook ${res.status}`)
  comboCache.set(key, { at: now, body })
  sweepCombos(now)
  await holdInDb(key, body)
  return body
}

/** [cache]: "hit" from this isolate, "db" from the table, "miss" from Spellbook just now. */
function combosResponse(req: Request, body: string, cache: 'hit' | 'db' | 'miss'): Response {
  return new Response(body, {
    status: 200,
    headers: {
      ...corsHeaders(req),
      'Content-Type': 'application/json',
      'Cache-Control': `public, max-age=${Math.floor(COMBOS_TTL_MS / 1000)}`,
      'X-Relay-Cache': cache,
      // Whether the relay can reach its own store — without it, every first look goes upstream.
      'X-Relay-Store': db() ? 'on' : 'off',
      'Access-Control-Expose-Headers': 'X-Relay-Cache, X-Relay-Store',
    },
  })
}

async function findMyCombos(req: Request): Promise<Response> {
  const text = await req.text()
  if (text.length > MAX_BODY_BYTES) return json(req, 413, { error: 'Decklist too large.' })
  let parsed: { commanders?: unknown; main?: unknown }
  try {
    parsed = JSON.parse(text)
  } catch {
    return json(req, 400, { error: 'Body must be JSON.' })
  }
  const cards = (list: unknown) =>
    (Array.isArray(list) ? list : [])
      .filter((c): c is { card: string; quantity?: number } => typeof c?.card === 'string' && c.card.length <= 200)
      .slice(0, 250)
      .map((c) => ({ card: c.card, quantity: Math.max(1, Math.min(99, Number(c.quantity) || 1)) }))
  const commanders = cards(parsed.commanders)
  const main = cards(parsed.main)
  const body = JSON.stringify({ commanders, main })
  // Held under the decklist itself, so anyone opening the same deck — the owner on another device,
  // or a friend it's shared with — is answered without asking Spellbook again.
  const key = await deckKey(commanders, main)
  const now = Date.now()

  const held = comboCache.get(key)
  if (held && now - held.at <= COMBOS_TTL_MS) return combosResponse(req, held.body, 'hit')
  const fromDb = await heldInDb(key)
  if (fromDb !== null) {
    comboCache.set(key, { at: now, body: fromDb })
    sweepCombos(now)
    return combosResponse(req, fromDb, 'db')
  }

  let request = comboInFlight.get(key)
  if (!request) {
    request = fetchDeckCombos(body, key, now).finally(() => comboInFlight.delete(key))
    comboInFlight.set(key, request)
  }
  try {
    return combosResponse(req, await request, 'miss')
  } catch {
    return json(req, 502, { error: 'Commander Spellbook is unavailable right now.' })
  }
}

/** Asks Spellbook about a decklist and holds the answer. Only a 200 is held. */
async function fetchDeckCombos(body: string, key: string, now: number): Promise<string> {
  const res = await fetch(`${SPELLBOOK}/find-my-combos`, {
    method: 'POST',
    headers: { 'User-Agent': USER_AGENT, Accept: 'application/json', 'Content-Type': 'application/json' },
    body,
  })
  const answer = await res.text()
  if (!res.ok) throw new Error(`Spellbook ${res.status}`)
  comboCache.set(key, { at: now, body: answer })
  sweepCombos(now)
  await holdInDb(key, answer)
  return answer
}

/**
 * A decklist's key: the cards it holds, hashed so a 250-card list is a short row key. Order and
 * repeats don't matter, so the same deck on two devices is the same question.
 */
async function deckKey(commanders: { card: string }[], main: { card: string }[]): Promise<string> {
  const names = (list: { card: string }[]) => [...new Set(list.map((c) => c.card.trim().toLowerCase()))].sort().join('|')
  const text = `${names(commanders)}#${names(main)}`
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text))
  return `deck:${[...new Uint8Array(digest)].slice(0, 16).map((b) => b.toString(16).padStart(2, '0')).join('')}`
}

/** The routes, as one handler — exported so cache.test.mjs can drive it without a server. */
export async function handle(req: Request): Promise<Response> {
  if (req.method === 'OPTIONS') return new Response(null, { status: 204, headers: corsHeaders(req) })
  const url = new URL(req.url)
  // The path arrives as /api-relay/<route> (or /functions/v1/api-relay/<route> locally).
  const route = url.pathname.replace(/^.*?\/api-relay/, '') || '/'
  try {
    if (req.method === 'GET' && route === '/news') return await news(req)
    if (req.method === 'GET' && route === '/combos/variants') return await comboVariants(req, url)
    if (req.method === 'POST' && route === '/combos/find-my-combos') return await findMyCombos(req)
    return json(req, 404, { error: 'Unknown route.' })
  } catch (e) {
    return json(req, 502, { error: `Upstream request failed: ${e instanceof Error ? e.message : String(e)}` })
  }
}

// Served under Deno; importing this file elsewhere (the cache test) just gets the handler.
const deno = (globalThis as { Deno?: { serve: (handler: (req: Request) => Promise<Response>) => void } }).Deno
if (deno) deno.serve(handle)

export { comboCache, COMBOS_KEEP }
