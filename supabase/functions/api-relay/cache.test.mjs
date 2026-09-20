// What the relay holds on to for card combo lookups: this isolate's own memory, the combo_cache
// table behind it (which is what survives a fresh isolate), one request upstream for callers
// arriving together, and errors never held.
//
// Run it with Node 22 or newer (it reads the TypeScript directly):
//   node supabase/functions/api-relay/cache.test.mjs
import assert from 'node:assert/strict'

process.env.SUPABASE_URL = 'https://project.test'
process.env.SUPABASE_SERVICE_ROLE_KEY = 'test-service-key'

// A stand-in for Commander Spellbook and for the project's REST API.
let upstreamCalls = 0
let nextStatus = 200
let slowMs = 0
let dbRows = new Map() // key -> { body, fetched_at }
let dbDown = false
let dbReads = 0
let dbWrites = 0
let dbDeletes = 0

globalThis.fetch = async (url, init = {}) => {
  const target = new URL(url)
  if (target.origin === 'https://project.test') {
    if (dbDown) throw new Error('database unreachable')
    const key = decodeURIComponent(target.searchParams.get('key')?.replace(/^eq\./, '') ?? '')
    if (init.method === 'POST') {
      dbWrites++
      const row = JSON.parse(init.body)
      dbRows.set(row.key, { body: row.body, fetched_at: row.fetched_at })
      return new Response(null, { status: 201 })
    }
    if (init.method === 'DELETE') {
      dbDeletes++
      return new Response(null, { status: 204 })
    }
    dbReads++
    const since = decodeURIComponent(target.searchParams.get('fetched_at')?.replace(/^gte\./, '') ?? '')
    const row = dbRows.get(key)
    const fresh = row && (!since || row.fetched_at >= since)
    return new Response(JSON.stringify(fresh ? [{ body: row.body }] : []), { status: 200, headers: { 'Content-Type': 'application/json' } })
  }
  upstreamCalls++
  if (slowMs) await new Promise((r) => setTimeout(r, slowMs))
  const q = target.searchParams.get('q')
  return new Response(JSON.stringify({ results: [{ id: `combo-for-${q}` }] }), {
    status: nextStatus,
    headers: { 'Content-Type': 'application/json' },
  })
}

const { handle, comboCache, COMBOS_KEEP } = await import('./index.ts')

/** A card looked up through the relay; [freshIsolate] throws away what this one held, as the platform does. */
const ask = async (card, { limit = 6, freshIsolate = false } = {}) => {
  if (freshIsolate) comboCache.clear()
  const url = `https://relay.test/api-relay/combos/variants?q=${encodeURIComponent(`card:"${card}"`)}&limit=${limit}`
  const res = await handle(new Request(url, { headers: { Origin: 'https://bellavaffa-cmd.github.io' } }))
  return { status: res.status, cache: res.headers.get('X-Relay-Cache'), body: await res.text(), cors: res.headers.get('Access-Control-Allow-Origin') }
}

// A first look goes to Spellbook and is written to the table.
const first = await ask('Sol Ring')
assert.equal(first.cache, 'miss')
assert.equal(upstreamCalls, 1)
assert.equal(dbWrites, 1)

// Postgres text has no room for a NUL, so a key holding one is written as nothing at all.
for (const key of dbRows.keys()) {
  assert.ok(!/[\u0000]/.test(key), `key must be plain text: ${JSON.stringify(key)}`)
  assert.ok(/^\d+:/.test(key), `key should start with the limit: ${JSON.stringify(key)}`)
}

// The same isolate answers from memory, without even reading the table.
const readsBefore = dbReads
const second = await ask('Sol Ring')
assert.equal(second.cache, 'hit')
assert.equal(upstreamCalls, 1)
assert.equal(dbReads, readsBefore)
assert.equal(second.body, first.body)
assert.equal(second.cors, 'https://bellavaffa-cmd.github.io')

// A fresh isolate — what the platform usually hands a request — is answered by the table.
const cold = await ask('Sol Ring', { freshIsolate: true })
assert.equal(cold.cache, 'db')
assert.equal(cold.body, first.body)
assert.equal(upstreamCalls, 1)

// A day-old row is not an answer: the card is asked of Spellbook again.
dbRows.set([...dbRows.keys()][0], { ...dbRows.get([...dbRows.keys()][0]), fetched_at: new Date(Date.now() - 25 * 60 * 60 * 1000).toISOString() })
const stale = await ask('Sol Ring', { freshIsolate: true })
assert.equal(stale.cache, 'miss')
assert.equal(upstreamCalls, 2)

// A different card, and the same card asked with a different limit, are different questions.
await ask('Cultivate', { freshIsolate: true })
assert.equal(upstreamCalls, 3)
await ask('Sol Ring', { limit: 30, freshIsolate: true })
assert.equal(upstreamCalls, 4)

// Ten people opening the same card at once make one request upstream.
slowMs = 30
const together = await Promise.all(Array.from({ length: 10 }, () => ask('Rhystic Study')))
slowMs = 0
assert.equal(upstreamCalls, 5)
assert.equal(together.filter((r) => r.cache === 'miss').length, 10)

// Spellbook failing is not an answer: it isn't held, and the next look tries again.
nextStatus = 503
const failed = await ask('Mox Opal', { freshIsolate: true })
assert.equal(failed.status, 502)
assert.equal(upstreamCalls, 6)
const writesBefore = dbWrites
nextStatus = 200
assert.equal((await ask('Mox Opal', { freshIsolate: true })).cache, 'miss')
assert.equal(upstreamCalls, 7)
assert.equal(dbWrites, writesBefore + 1)

// The database being unreachable slows things down; it doesn't break them.
dbDown = true
const withoutDb = await ask('Deadeye Navigator', { freshIsolate: true })
assert.equal(withoutDb.status, 200)
assert.equal(withoutDb.cache, 'miss')
assert.equal(upstreamCalls, 8)
dbDown = false

// This isolate's own memory stays small: the card nobody's asking for goes, one kept warm stays.
for (let i = 0; i < COMBOS_KEEP + 50; i++) await ask(`filler ${i}`)
assert.ok(comboCache.size <= COMBOS_KEEP, `size ${comboCache.size}`)
assert.equal((await ask(`filler ${COMBOS_KEEP + 49}`)).cache, 'hit')

// A decklist is held the same way, under the cards it holds.
const askDeck = async (commanders, main, { freshIsolate = false } = {}) => {
  if (freshIsolate) comboCache.clear()
  const res = await handle(new Request('https://relay.test/api-relay/combos/find-my-combos', {
    method: 'POST',
    headers: { Origin: 'https://bellavaffa-cmd.github.io', 'Content-Type': 'application/json' },
    body: JSON.stringify({ commanders: commanders.map((card) => ({ card })), main: main.map((card) => ({ card })) }),
  }))
  return { status: res.status, cache: res.headers.get('X-Relay-Cache'), body: await res.text() }
}

const deckCallsBefore = upstreamCalls
const deckFirst = await askDeck(['Omnath, Locus of Mana'], ['Sol Ring', 'Cultivate'])
assert.equal(deckFirst.cache, 'miss')
assert.equal(upstreamCalls, deckCallsBefore + 1)

// The same deck from another device — order and case aside — is answered from the table.
const deckAgain = await askDeck(['omnath, locus of mana'], ['cultivate', 'Sol Ring'], { freshIsolate: true })
assert.equal(deckAgain.cache, 'db')
assert.equal(deckAgain.body, deckFirst.body)
assert.equal(upstreamCalls, deckCallsBefore + 1)

// A card added makes it a different deck, so it's asked again.
const edited = await askDeck(['Omnath, Locus of Mana'], ['Sol Ring', 'Cultivate', 'Rhystic Study'], { freshIsolate: true })
assert.equal(edited.cache, 'miss')
assert.equal(upstreamCalls, deckCallsBefore + 2)

// A decklist's key is a short row key, not the whole list.
for (const key of dbRows.keys()) assert.ok(key.length <= 64, `key too long: ${key.length}`)

// An unknown route is still an unknown route.
assert.equal((await handle(new Request('https://relay.test/api-relay/nope'))).status, 404)

console.log(`api-relay cache: all checks passed (${upstreamCalls} upstream calls, ${dbReads} table reads, ${dbWrites} writes, ${dbDeletes} sweeps)`)
