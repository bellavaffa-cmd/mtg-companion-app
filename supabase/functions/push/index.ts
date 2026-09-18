// Sends MTG Companion's phone notifications (friend requests, trades). Called only by the database
// triggers in supabase/migrations/20260919010000_push_notifications.sql, which hand over the message
// and the recipient's devices; the shared secret proves the call came from them. This function
// never reads the database — it reports dead tokens back through prune_push_tokens().
//
// Secrets (supabase secrets set …):
//   PUSH_HOOK_SECRET          the same value as the push_hook_secret Vault secret
//   FIREBASE_SERVICE_ACCOUNT  the Firebase service account JSON (Android); without it Android is skipped
//   VAPID_PUBLIC_KEY          base64url, uncompressed P-256 (the web app subscribes with it)
//   VAPID_PRIVATE_JWK         the matching private key as a JWK (JSON)
//   VAPID_SUBJECT             optional; defaults to the web app's address
// Deploy with --no-verify-jwt: the caller is the database, not a signed-in user.

import { sendFcm, type ServiceAccount } from './fcm.ts'
import { sendWebPush, type SendResult, type Vapid, type WebSubscription } from './webpush.ts'

interface Device {
  platform: 'fcm' | 'web'
  token: string
  subscription: WebSubscription | null
}

interface Job {
  tokens: Device[]
  title: string
  body: string
  open: string
  tag: string
}

const json = (status: number, body: unknown) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

function config() {
  const account = Deno.env.get('FIREBASE_SERVICE_ACCOUNT')
  const publicKey = Deno.env.get('VAPID_PUBLIC_KEY')
  const privateJwk = Deno.env.get('VAPID_PRIVATE_JWK')
  return {
    firebase: account ? (JSON.parse(account) as ServiceAccount) : null,
    vapid: publicKey && privateJwk
      ? { publicKey, privateJwk: JSON.parse(privateJwk), subject: Deno.env.get('VAPID_SUBJECT') ?? 'https://bellavaffa-cmd.github.io/mtg-companion-web/' } as Vapid
      : null,
  }
}

Deno.serve(async (req) => {
  const secret = Deno.env.get('PUSH_HOOK_SECRET')
  if (req.method !== 'POST') return json(405, { error: 'POST only' })
  if (!secret || req.headers.get('x-push-secret') !== secret) return json(401, { error: 'not allowed' })

  let job: Job
  try {
    job = await req.json()
  } catch {
    return json(400, { error: 'bad body' })
  }
  const { firebase, vapid } = config()
  const data = { title: job.title, body: job.body, open: job.open, tag: job.tag }
  const payload = JSON.stringify(data)

  const results = await Promise.all(job.tokens.map(async (device): Promise<[Device, SendResult | null]> => {
    try {
      if (device.platform === 'fcm' && firebase) return [device, await sendFcm(firebase, device.token, data)]
      if (device.platform === 'web' && vapid && device.subscription) return [device, await sendWebPush(device.subscription, payload, vapid)]
      return [device, null]
    } catch (e) {
      console.error(`push to a ${device.platform} device failed:`, e)
      return [device, { ok: false, gone: false, status: 0 }]
    }
  }))

  const dead = results.filter(([, r]) => r?.gone).map(([d]) => d.token)
  if (dead.length > 0) {
    await fetch(`${Deno.env.get('SUPABASE_URL')}/rest/v1/rpc/prune_push_tokens`, {
      method: 'POST',
      headers: { apikey: Deno.env.get('SUPABASE_ANON_KEY') ?? '', 'Content-Type': 'application/json' },
      body: JSON.stringify({ p_secret: secret, p_tokens: dead }),
    }).then((r) => r.body?.cancel()).catch((e) => console.error('pruning failed:', e))
  }
  return json(200, {
    sent: results.filter(([, r]) => r?.ok).length,
    skipped: results.filter(([, r]) => r === null).length,
    failed: results.filter(([, r]) => r && !r.ok).map(([d, r]) => `${d.platform}:${r!.status}`),
    pruned: dead.length,
  })
})
