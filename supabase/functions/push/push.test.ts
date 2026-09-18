// node --test supabase/functions/push/push.test.ts
// The crypto the push function relies on, checked against the standards' own examples.

import { test } from 'node:test'
import assert from 'node:assert/strict'
import { b64uDecode, b64uEncode, concat, encryptPayload, vapidAuthorization } from './webpush.ts'
import { signJwtRS256 } from './fcm.ts'

test('Web Push encryption matches RFC 8291 Appendix A exactly', async () => {
  const body = await encryptPayload(
    b64uDecode('BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4'),
    b64uDecode('BTBZMqHH6r4Tts7J_aSIgg'),
    b64uDecode('V2hlbiBJIGdyb3cgdXAsIEkgd2FudCB0byBiZSBhIHdhdGVybWVsb24'),
    b64uDecode('DGv6ra1nlYgDCS1FRnbzlw'),
    {
      privateKey: b64uDecode('yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw'),
      publicKey: b64uDecode('BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8'),
    },
  )
  const expected = concat(
    b64uDecode('DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8'),
    b64uDecode('8pfeW0KbunFT06SuDKoJH9Ql87S1QUrdirN6GcG7sFz1y1sqLgVi1VhjVkHsUoEsbI_0LpXMuGvnzQ'),
  )
  assert.equal(b64uEncode(body), b64uEncode(expected))
})

test('a random-key message has the aes128gcm header layout', async () => {
  const ua = (await crypto.subtle.generateKey({ name: 'ECDH', namedCurve: 'P-256' }, true, ['deriveBits'])) as CryptoKeyPair
  const uaPublic = new Uint8Array(await crypto.subtle.exportKey('raw', ua.publicKey))
  const body = await encryptPayload(uaPublic, crypto.getRandomValues(new Uint8Array(16)), new TextEncoder().encode('{"title":"hi"}'))
  assert.equal(new DataView(body.buffer).getUint32(16), 4096)
  assert.equal(body[20], 65)
  assert.equal(body[21], 4)
  assert.equal(body.length, 86 + 14 + 1 + 16)
})

test('the VAPID token is signed by the VAPID key and names the push service', async () => {
  const pair = (await crypto.subtle.generateKey({ name: 'ECDSA', namedCurve: 'P-256' }, true, ['sign', 'verify'])) as CryptoKeyPair
  const publicKey = b64uEncode(new Uint8Array(await crypto.subtle.exportKey('raw', pair.publicKey)))
  const header = await vapidAuthorization(
    'https://fcm.googleapis.com/fcm/send/abc:def',
    { publicKey, privateJwk: await crypto.subtle.exportKey('jwk', pair.privateKey), subject: 'https://example.com/' },
    1_000_000_000_000,
  )
  const [, jwt, k] = header.match(/^vapid t=([^,]+), k=(.+)$/)!
  assert.equal(k, publicKey)
  const [h, c, s] = jwt.split('.')
  const claims = JSON.parse(new TextDecoder().decode(b64uDecode(c)))
  assert.deepEqual(claims, { aud: 'https://fcm.googleapis.com', exp: 1_000_000_000 + 12 * 3600, sub: 'https://example.com/' })
  const ok = await crypto.subtle.verify({ name: 'ECDSA', hash: 'SHA-256' }, pair.publicKey, b64uDecode(s), new TextEncoder().encode(`${h}.${c}`))
  assert.ok(ok)
})

test('the Firebase service-account JWT is RS256-signed with its PEM key', async () => {
  const pair = (await crypto.subtle.generateKey(
    { name: 'RSASSA-PKCS1-v1_5', modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' },
    true, ['sign', 'verify'],
  )) as CryptoKeyPair
  const der = new Uint8Array(await crypto.subtle.exportKey('pkcs8', pair.privateKey))
  const pem = `-----BEGIN PRIVATE KEY-----\n${btoa(String.fromCharCode(...der)).replace(/(.{64})/g, '$1\n')}\n-----END PRIVATE KEY-----\n`
  const jwt = await signJwtRS256({ iss: 'x@y.iam.gserviceaccount.com', aud: 'https://oauth2.googleapis.com/token' }, pem)
  const [h, c, s] = jwt.split('.')
  assert.deepEqual(JSON.parse(new TextDecoder().decode(b64uDecode(h))), { alg: 'RS256', typ: 'JWT' })
  assert.ok(await crypto.subtle.verify('RSASSA-PKCS1-v1_5', pair.publicKey, b64uDecode(s), new TextEncoder().encode(`${h}.${c}`)))
})
