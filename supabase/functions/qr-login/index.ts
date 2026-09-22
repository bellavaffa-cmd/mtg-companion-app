// qr-login: a signed-in phone approving a sign-in on the web (see supabase/migrations/…_qr_login.sql).
//
//   POST /qr-login   { "code": "<the code in the QR>" }   with the approving user's access token
//
// The browser showed a code and is waiting. This mints a one-time sign-in token for the caller's OWN
// account — whoever's access token comes with the request, never anyone else's — and leaves it on the
// request for that browser to collect once. The token is a magic-link hash made through the admin
// API, which does not send an email.
//
// Answers { "ok": true } when the browser can now sign in, or 400 with a reason: "expired" (nothing
// waiting under that code, or it ran out), "done" (someone already approved it).

const ALLOWED_ORIGINS = [/^https:\/\/(www\.)?manabind\.com$/, /^https:\/\/bellavaffa-cmd\.github\.io$/, /^http:\/\/localhost(:\d+)?$/, /^http:\/\/127\.0\.0\.1(:\d+)?$/]

function corsHeaders(req: Request): Record<string, string> {
  const origin = req.headers.get('Origin') ?? ''
  const headers: Record<string, string> = {
    'Access-Control-Allow-Methods': 'POST, OPTIONS',
    'Access-Control-Allow-Headers': 'authorization, apikey, content-type, x-client-info',
    'Access-Control-Max-Age': '86400',
    Vary: 'Origin',
  }
  if (ALLOWED_ORIGINS.some((re) => re.test(origin))) headers['Access-Control-Allow-Origin'] = origin
  return headers
}

const json = (req: Request, status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { ...corsHeaders(req), 'Content-Type': 'application/json' } })

const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!
const SERVICE_ROLE = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!

/** The account the request came from, by its own access token. */
async function caller(req: Request): Promise<{ id: string; email: string } | null> {
  const auth = req.headers.get('Authorization') ?? ''
  if (!auth.toLowerCase().startsWith('bearer ')) return null
  const res = await fetch(`${SUPABASE_URL}/auth/v1/user`, { headers: { Authorization: auth, apikey: SERVICE_ROLE } })
  if (!res.ok) return null
  const user = await res.json()
  return user?.id && user?.email ? { id: user.id, email: user.email } : null
}

/** Reads and writes the waiting request; the table is closed to everyone but this key. */
async function rest(path: string, init: RequestInit = {}) {
  return fetch(`${SUPABASE_URL}/rest/v1/${path}`, {
    ...init,
    headers: {
      apikey: SERVICE_ROLE,
      Authorization: `Bearer ${SERVICE_ROLE}`,
      'Content-Type': 'application/json',
      Prefer: 'return=representation',
      ...(init.headers ?? {}),
    },
  })
}

Deno.serve(async (req: Request) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: corsHeaders(req) })
  if (req.method !== 'POST') return json(req, 405, { error: 'method' })

  const who = await caller(req)
  if (!who) return json(req, 401, { error: 'not_signed_in' })

  const code = await req.json().then((b) => (typeof b?.code === 'string' ? b.code.trim() : '')).catch(() => '')
  if (!/^[0-9a-f]{32}$/.test(code)) return json(req, 400, { error: 'expired' })

  const waiting = await rest(`qr_login?code=eq.${code}&select=code,expires_at,approved_by,claimed_at`).then((r) => r.json())
  const row = Array.isArray(waiting) ? waiting[0] : null
  if (!row || new Date(row.expires_at).getTime() < Date.now()) return json(req, 400, { error: 'expired' })
  if (row.approved_by || row.claimed_at) return json(req, 400, { error: 'done' })

  // A sign-in token for this account, made rather than emailed.
  const link = await fetch(`${SUPABASE_URL}/auth/v1/admin/generate_link`, {
    method: 'POST',
    headers: { apikey: SERVICE_ROLE, Authorization: `Bearer ${SERVICE_ROLE}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ type: 'magiclink', email: who.email }),
  })
  if (!link.ok) return json(req, 500, { error: 'no_token' })
  const made = await link.json()
  const tokenHash: string | undefined = made?.hashed_token ?? made?.properties?.hashed_token
  if (!tokenHash) return json(req, 500, { error: 'no_token' })

  // Only if nobody got there first (approved_by is still empty).
  const saved = await rest(`qr_login?code=eq.${code}&approved_by=is.null&select=code`, {
    method: 'PATCH',
    body: JSON.stringify({ approved_by: who.id, token_hash: tokenHash }),
  }).then((r) => r.json())
  if (!Array.isArray(saved) || saved.length === 0) return json(req, 400, { error: 'done' })

  return json(req, 200, { ok: true })
})
