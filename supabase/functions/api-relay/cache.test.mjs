// What the relay holds on to for card combo lookups: a card is asked of Commander Spellbook once a
// day per instance, callers arriving together share the one request, an error is never held, and a
// full cache drops the card nobody's asking for.
//
// Run it with Node 22 or newer (it reads the TypeScript directly):
//   node supabase/functions/api-relay/cache.test.mjs
import assert from 'node:assert/strict'

let upstreamCalls = 0
let nextStatus = 200
let slowMs = 0
globalThis.fetch = async (url) => {
  upstreamCalls++
  if (slowMs) await new Promise((r) => setTimeout(r, slowMs))
  const q = new URL(url).searchParams.get('q')
  return new Response(JSON.stringify({ results: [{ id: `combo-for-${q}` }] }), {
    status: nextStatus,
    headers: { 'Content-Type': 'application/json' },
  })
}

const { handle, comboCache, COMBOS_KEEP } = await import('./index.ts')

const ask = async (card, limit = 6) => {
  const url = `https://relay.test/api-relay/combos/variants?q=${encodeURIComponent(`card:"${card}"`)}&limit=${limit}`
  const res = await handle(new Request(url, { headers: { Origin: 'https://bellavaffa-cmd.github.io' } }))
  return { status: res.status, cache: res.headers.get('X-Relay-Cache'), body: await res.text(), cors: res.headers.get('Access-Control-Allow-Origin') }
}

// A first look goes to Spellbook; a second is answered from here.
const first = await ask('Sol Ring')
assert.equal(first.cache, 'miss')
assert.equal(upstreamCalls, 1)
const second = await ask('Sol Ring')
assert.equal(second.cache, 'hit')
assert.equal(upstreamCalls, 1)
assert.equal(second.body, first.body)
assert.equal(second.cors, 'https://bellavaffa-cmd.github.io')

// A different card, and the same card asked with a different limit, are different questions.
await ask('Cultivate')
assert.equal(upstreamCalls, 2)
await ask('Sol Ring', 30)
assert.equal(upstreamCalls, 3)

// Ten people opening the same card at once make one request upstream.
slowMs = 30
const together = await Promise.all(Array.from({ length: 10 }, () => ask('Rhystic Study')))
slowMs = 0
assert.equal(upstreamCalls, 4)
assert.equal(together.filter((r) => r.cache === 'miss').length, 10)

// Spellbook failing is not an answer: it isn't held, and the next look tries again.
nextStatus = 503
const failed = await ask('Mox Opal')
assert.equal(failed.status, 502)
assert.equal(upstreamCalls, 5)
nextStatus = 200
assert.equal((await ask('Mox Opal')).cache, 'miss')
assert.equal(upstreamCalls, 6)

// Full: the card nobody's asking for goes, while one kept warm stays.
for (let i = 0; i < COMBOS_KEEP + 50; i++) await ask(`filler ${i}`)
assert.ok(comboCache.size <= COMBOS_KEEP, `size ${comboCache.size}`)
assert.equal((await ask(`filler ${COMBOS_KEEP + 49}`)).cache, 'hit')
assert.equal((await ask('filler 0')).cache, 'miss')

// An unknown route is still an unknown route.
assert.equal((await handle(new Request('https://relay.test/api-relay/nope'))).status, 404)

console.log('api-relay cache: all checks passed')
