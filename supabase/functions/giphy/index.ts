// giphy: GIF search for picking a profile picture (or a life counter background) inside the apps.
// The Giphy API key stays here, as the GIPHY_API_KEY secret, never in the apps.
//
//   GET /giphy?q=dragon&offset=0   search (G/PG/PG-13), 24 at a time
//   GET /giphy?offset=0            trending, when there's nothing to search for
//
// Signed-in users only (the apps send the user's access token). Answers are kept for a while and
// shared between users: the key's free tier allows 100 calls an hour, and most people look at the
// same trending GIFs and search for the same few things.

// manabind.com is the web app's home; the github.io address it moved from still redirects there.
const ALLOWED_ORIGINS = [/^https:\/\/(www\.)?manabind\.com$/, /^https:\/\/bellavaffa-cmd\.github\.io$/, /^http:\/\/localhost(:\d+)?$/, /^http:\/\/127\.0\.0\.1(:\d+)?$/]
const PAGE = 24
const TTL_MS = 30 * 60 * 1000
const MAX_CACHED = 300

function corsHeaders(req: Request): Record<string, string> {
  const origin = req.headers.get('Origin') ?? ''
  const headers: Record<string, string> = {
    'Access-Control-Allow-Methods': 'GET, OPTIONS',
    'Access-Control-Allow-Headers': 'authorization, apikey, content-type, x-client-info',
    'Access-Control-Max-Age': '86400',
    Vary: 'Origin',
  }
  if (ALLOWED_ORIGINS.some((re) => re.test(origin))) headers['Access-Control-Allow-Origin'] = origin
  return headers
}

const json = (req: Request, status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { ...corsHeaders(req), 'Content-Type': 'application/json' } })

/** Whether the bearer token is a signed-in user's (the gateway has already checked its signature). */
function signedIn(req: Request): boolean {
  const token = (req.headers.get('Authorization') ?? '').replace(/^Bearer\s+/i, '')
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))
    return payload.role === 'authenticated' && typeof payload.sub === 'string'
  } catch {
    return false
  }
}

interface Gif {
  id: string
  title: string
  /** A small moving preview for the grid (about 100 px wide). */
  preview: string
  width: number
  height: number
}

interface Page {
  gifs: Gif[]
  /** Where the next page starts, or null at the end. */
  next: number | null
}

const cache = new Map<string, { at: number; page: Page }>()

// deno-lint-ignore no-explicit-any
function slim(item: any): Gif | null {
  const small = item?.images?.fixed_width_small ?? item?.images?.fixed_width
  const url = small?.webp || small?.url
  if (!item?.id || !url) return null
  return { id: String(item.id), title: String(item.title ?? ''), preview: url, width: Number(small.width) || 100, height: Number(small.height) || 100 }
}

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response(null, { status: 204, headers: corsHeaders(req) })
  if (req.method !== 'GET') return json(req, 405, { error: 'GET only' })
  if (!signedIn(req)) return json(req, 401, { error: 'Sign in first.' })
  const key = Deno.env.get('GIPHY_API_KEY')
  if (!key) return json(req, 503, { error: 'GIF search isn’t set up.' })

  const params = new URL(req.url).searchParams
  const q = (params.get('q') ?? '').trim().toLowerCase().slice(0, 50)
  const offset = Math.max(0, Math.min(4999 - PAGE, Number(params.get('offset')) || 0))
  const cacheKey = `${q}|${offset}`
  const hit = cache.get(cacheKey)
  if (hit && Date.now() - hit.at < TTL_MS) return json(req, 200, hit.page)

  const upstream = new URL(q ? 'https://api.giphy.com/v1/gifs/search' : 'https://api.giphy.com/v1/gifs/trending')
  upstream.searchParams.set('api_key', key)
  if (q) upstream.searchParams.set('q', q)
  upstream.searchParams.set('limit', String(PAGE))
  upstream.searchParams.set('offset', String(offset))
  upstream.searchParams.set('rating', 'pg-13')
  upstream.searchParams.set('bundle', 'messaging_non_clips')

  let res: Response
  try {
    res = await fetch(upstream)
  } catch {
    return json(req, 502, { error: "Couldn't reach Giphy — try again." })
  }
  if (res.status === 429) return json(req, 429, { error: 'GIF search is busy right now — try again in a few minutes, or paste a Giphy link.' })
  if (!res.ok) return json(req, 502, { error: `Giphy answered HTTP ${res.status}.` })
  const body = await res.json()
  const gifs = (Array.isArray(body?.data) ? body.data : []).map(slim).filter((g: Gif | null): g is Gif => g !== null)
  const total = Number(body?.pagination?.total_count ?? 0)
  const next = offset + PAGE < Math.min(total, 4999) && gifs.length > 0 ? offset + PAGE : null
  const page: Page = { gifs, next }

  if (cache.size >= MAX_CACHED) cache.delete(cache.keys().next().value!)
  cache.set(cacheKey, { at: Date.now(), page })
  return json(req, 200, page)
})
