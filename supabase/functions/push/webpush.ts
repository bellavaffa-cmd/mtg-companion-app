// Web Push with nothing but the Web Crypto API: RFC 8291 message encryption (aes128gcm) and RFC 8292
// VAPID. Runs in the Edge Function (Deno) and in the tests (Node).

const enc = new TextEncoder()

export function b64uEncode(bytes: Uint8Array): string {
  let s = ''
  for (const b of bytes) s += String.fromCharCode(b)
  return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

export function b64uDecode(text: string): Uint8Array {
  const s = atob(text.replace(/-/g, '+').replace(/_/g, '/') + '==='.slice((text.length + 3) % 4))
  return Uint8Array.from(s, (c) => c.charCodeAt(0))
}

export function concat(...parts: Uint8Array[]): Uint8Array {
  const out = new Uint8Array(parts.reduce((n, p) => n + p.length, 0))
  let at = 0
  for (const p of parts) {
    out.set(p, at)
    at += p.length
  }
  return out
}

async function hmac(key: Uint8Array, data: Uint8Array): Promise<Uint8Array> {
  const k = await crypto.subtle.importKey('raw', key, { name: 'HMAC', hash: 'SHA-256' }, false, ['sign'])
  return new Uint8Array(await crypto.subtle.sign('HMAC', k, data))
}

/** A P-256 public key's JWK coordinates, from its uncompressed form (0x04 || x || y). */
function jwkOf(publicKey: Uint8Array, d?: Uint8Array): JsonWebKey {
  return {
    kty: 'EC',
    crv: 'P-256',
    x: b64uEncode(publicKey.slice(1, 33)),
    y: b64uEncode(publicKey.slice(33, 65)),
    ...(d ? { d: b64uEncode(d) } : {}),
    ext: true,
  }
}

/** The sender's one-off key pair; given in the tests (RFC 8291's example), random otherwise. */
export interface SenderKeys {
  privateKey: Uint8Array
  publicKey: Uint8Array
}

/** A Web Push record size: one record holds any notification this app sends. */
const RECORD_SIZE = 4096

/**
 * Encrypts [plaintext] for a browser subscription (its p256dh key and auth secret), per RFC 8291:
 * the body to POST, header included.
 */
export async function encryptPayload(
  uaPublic: Uint8Array,
  authSecret: Uint8Array,
  plaintext: Uint8Array,
  salt: Uint8Array = crypto.getRandomValues(new Uint8Array(16)),
  sender?: SenderKeys,
): Promise<Uint8Array> {
  let asPrivate: CryptoKey
  let asPublic: Uint8Array
  if (sender) {
    asPrivate = await crypto.subtle.importKey('jwk', jwkOf(sender.publicKey, sender.privateKey), { name: 'ECDH', namedCurve: 'P-256' }, false, ['deriveBits'])
    asPublic = sender.publicKey
  } else {
    const pair = (await crypto.subtle.generateKey({ name: 'ECDH', namedCurve: 'P-256' }, true, ['deriveBits'])) as CryptoKeyPair
    asPrivate = pair.privateKey
    asPublic = new Uint8Array(await crypto.subtle.exportKey('raw', pair.publicKey))
  }
  const uaKey = await crypto.subtle.importKey('raw', uaPublic, { name: 'ECDH', namedCurve: 'P-256' }, false, [])
  const ecdhSecret = new Uint8Array(await crypto.subtle.deriveBits({ name: 'ECDH', public: uaKey }, asPrivate, 256))

  const prkKey = await hmac(authSecret, ecdhSecret)
  const keyInfo = concat(enc.encode('WebPush: info\0'), uaPublic, asPublic)
  const ikm = await hmac(prkKey, concat(keyInfo, new Uint8Array([1])))
  const prk = await hmac(salt, ikm)
  const cek = (await hmac(prk, enc.encode('Content-Encoding: aes128gcm\0\x01'))).slice(0, 16)
  const nonce = (await hmac(prk, enc.encode('Content-Encoding: nonce\0\x01'))).slice(0, 12)

  // One record, so it is also the last: the plaintext ends with the 0x02 delimiter.
  const padded = concat(plaintext, new Uint8Array([2]))
  const key = await crypto.subtle.importKey('raw', cek, 'AES-GCM', false, ['encrypt'])
  const ciphertext = new Uint8Array(await crypto.subtle.encrypt({ name: 'AES-GCM', iv: nonce, tagLength: 128 }, key, padded))

  const header = new Uint8Array(21 + asPublic.length)
  header.set(salt, 0)
  new DataView(header.buffer).setUint32(16, RECORD_SIZE)
  header[20] = asPublic.length
  header.set(asPublic, 21)
  return concat(header, ciphertext)
}

export interface Vapid {
  /** The public key, uncompressed and base64url (what browsers subscribe with). */
  publicKey: string
  /** The private key as a JWK. */
  privateJwk: JsonWebKey
  /** Who runs this sender: a mailto: or https: URL. */
  subject: string
}

/** The Authorization header value for a push to [endpoint] (RFC 8292), good for 12 hours. */
export async function vapidAuthorization(endpoint: string, vapid: Vapid, now = Date.now()): Promise<string> {
  const header = b64uEncode(enc.encode(JSON.stringify({ typ: 'JWT', alg: 'ES256' })))
  const claims = b64uEncode(enc.encode(JSON.stringify({ aud: new URL(endpoint).origin, exp: Math.floor(now / 1000) + 12 * 3600, sub: vapid.subject })))
  const key = await crypto.subtle.importKey('jwk', vapid.privateJwk, { name: 'ECDSA', namedCurve: 'P-256' }, false, ['sign'])
  // Web Crypto's ECDSA signature is already the raw r || s that JWS wants.
  const signature = new Uint8Array(await crypto.subtle.sign({ name: 'ECDSA', hash: 'SHA-256' }, key, enc.encode(`${header}.${claims}`)))
  return `vapid t=${header}.${claims}.${b64uEncode(signature)}, k=${vapid.publicKey}`
}

export interface WebSubscription {
  endpoint: string
  keys: { p256dh: string; auth: string }
}

export interface SendResult {
  ok: boolean
  /** The subscription or token no longer works and should be forgotten. */
  gone: boolean
  status: number
}

/** Sends [payload] (a short JSON string) to one browser subscription. */
export async function sendWebPush(subscription: WebSubscription, payload: string, vapid: Vapid, ttlSeconds = 86400): Promise<SendResult> {
  const body = await encryptPayload(b64uDecode(subscription.keys.p256dh), b64uDecode(subscription.keys.auth), enc.encode(payload))
  const res = await fetch(subscription.endpoint, {
    method: 'POST',
    headers: {
      Authorization: await vapidAuthorization(subscription.endpoint, vapid),
      'Content-Encoding': 'aes128gcm',
      'Content-Type': 'application/octet-stream',
      TTL: String(ttlSeconds),
      Urgency: 'high',
    },
    body,
  })
  await res.body?.cancel()
  return { ok: res.ok, gone: res.status === 404 || res.status === 410, status: res.status }
}
