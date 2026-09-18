// Firebase Cloud Messaging (HTTP v1) with nothing but the Web Crypto API: a service account's key
// signs a short JWT, Google trades it for an access token, and that sends the messages.

import { b64uEncode, type SendResult } from './webpush.ts'

export interface ServiceAccount {
  project_id: string
  client_email: string
  private_key: string
}

const enc = new TextEncoder()

/** An RS256-signed JWT of [claims], with the service account's PEM private key. */
export async function signJwtRS256(claims: Record<string, unknown>, privateKeyPem: string): Promise<string> {
  const der = Uint8Array.from(atob(privateKeyPem.replace(/-----[^-]+-----/g, '').replace(/\s+/g, '')), (c) => c.charCodeAt(0))
  const key = await crypto.subtle.importKey('pkcs8', der, { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['sign'])
  const unsigned = `${b64uEncode(enc.encode(JSON.stringify({ alg: 'RS256', typ: 'JWT' })))}.${b64uEncode(enc.encode(JSON.stringify(claims)))}`
  const signature = new Uint8Array(await crypto.subtle.sign('RSASSA-PKCS1-v1_5', key, enc.encode(unsigned)))
  return `${unsigned}.${b64uEncode(signature)}`
}

let cached: { token: string; expires: number; account: string } | null = null

/** An OAuth access token for sending messages, reused while it's good. */
export async function accessToken(account: ServiceAccount, now = Date.now()): Promise<string> {
  if (cached && cached.account === account.client_email && cached.expires > now + 60_000) return cached.token
  const iat = Math.floor(now / 1000)
  const assertion = await signJwtRS256(
    {
      iss: account.client_email,
      scope: 'https://www.googleapis.com/auth/firebase.messaging',
      aud: 'https://oauth2.googleapis.com/token',
      iat,
      exp: iat + 3600,
    },
    account.private_key,
  )
  const res = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion }),
  })
  if (!res.ok) throw new Error(`Google refused the service account (HTTP ${res.status}): ${await res.text()}`)
  const json = (await res.json()) as { access_token: string; expires_in: number }
  cached = { token: json.access_token, expires: now + json.expires_in * 1000, account: account.client_email }
  return json.access_token
}

/**
 * Sends a data message to one Android device. The app builds the notification itself from [data]
 * (title, body, open, tag), so it looks the same whether the app is open or not.
 */
export async function sendFcm(account: ServiceAccount, token: string, data: Record<string, string>): Promise<SendResult> {
  const res = await fetch(`https://fcm.googleapis.com/v1/projects/${account.project_id}/messages:send`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${await accessToken(account)}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ message: { token, data, android: { priority: 'HIGH', ttl: '86400s' } } }),
  })
  const text = await res.text()
  // UNREGISTERED (app uninstalled / token rotated) and a token Google doesn't recognise both mean: forget it.
  const gone = res.status === 404 || (res.status === 400 && /UNREGISTERED|registration token is not a valid/i.test(text))
  return { ok: res.ok, gone, status: res.status }
}
