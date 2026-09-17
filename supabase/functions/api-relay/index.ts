// api-relay: fetches, on the web app's behalf, the few MTG sources that don't allow browser
// (cross-origin) requests. Scryfall, EDHREC and MTGJSON all send `Access-Control-Allow-Origin: *`
// and are called directly; Commander Spellbook's API and the news RSS feeds are not, so they come
// through here.
//
// Deliberately not an open proxy: every route maps to one fixed upstream URL, and only the query
// parameters / body that route needs are passed on.
//
//   GET  /api-relay/news                        merged headlines from the RSS feeds, newest first
//   GET  /api-relay/combos/variants?q=&limit=   Commander Spellbook combo search
//   POST /api-relay/combos/find-my-combos       combos a decklist contains or is one card short of
//
// Deployed with JWT verification on, so callers send the project's anon key as the bearer token.

const ALLOWED_ORIGINS = [/^https:\/\/bellavaffa-cmd\.github\.io$/, /^http:\/\/localhost(:\d+)?$/, /^http:\/\/127\.0\.0\.1(:\d+)?$/]

const SPELLBOOK = 'https://backend.commanderspellbook.com'
const NEWS_FEEDS = [
  { url: 'https://mtgazone.com/news/feed/', source: 'MTG Arena Zone' },
  { url: 'https://articles.starcitygames.com/feed/', source: 'Star City Games' },
]
const USER_AGENT = 'MtgCompanionRelay/1.0 (+https://github.com/bellavaffa-cmd/mtg-companion-app)'
const MAX_BODY_BYTES = 256 * 1024
const NEWS_TTL_MS = 15 * 60 * 1000

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
  const upstream = `${SPELLBOOK}/variants?${new URLSearchParams({ q, limit: String(limit) })}`
  return relaySpellbook(req, upstream, { method: 'GET' })
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
  const body = JSON.stringify({ commanders: cards(parsed.commanders), main: cards(parsed.main) })
  return relaySpellbook(req, `${SPELLBOOK}/find-my-combos`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body,
  })
}

Deno.serve(async (req) => {
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
})
