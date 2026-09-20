# Manabind

A native Android app (Kotlin + Jetpack Compose) for searching Magic: The Gathering cards, pulling
data from these sources:

- **Scryfall** — card search, rules text, images, and bundled TCGPlayer/Cardmarket price snapshots. No key needed.
- **Commander Spellbook** — infinite combos that use a given card. No key needed.
- **EDHREC** — "cards played with this commander" recommendations, shown for any card EDHREC has a
  commander page for. Uses EDHREC's own JSON endpoints (`json.edhrec.com`); this is not an official
  public API, so it could change or break without notice.
- **MTGJSON** — official Commander precon decklists. No key needed.
- **MTG Arena Zone / Star City Games** — news headlines, through the app's own `api-relay` Supabase
  function (their feeds don't allow browser requests, which the web app needs).

The visual theme (dark near-black background, gold accent, Cinzel/DM Sans fonts) is matched to
[mtgoracle.gg](https://www.mtgoracle.gg/)'s own design system, including its exact color tokens and
bundled Google Fonts.

## Scanning physical cards

Tap the camera icon on the search screen to batch-scan cards - there's no shutter button, just point
the camera at a card and it resolves automatically. Recognized cards drop into a running list below
the camera rather than interrupting the flow; keep panning across a pile and each new card is added.
From the list, add any card straight to your collection or to a deck (existing or a new one created
inline), or swipe it off the list. The same card won't be added twice in one session.

Under the hood, CameraX streams frames through on-device ML Kit text recognition (no cloud call, no
key needed) to read the card's title - the top-most line of text on the frame - then looks that name
up via Scryfall's fuzzy-name search, so minor OCR misreads still resolve to the right card. A frame
is only analyzed once the previous one's OCR + lookup has finished, so it naturally throttles to
roughly one attempt per round trip instead of hammering ML Kit/Scryfall at full camera frame rate.
Misses just keep scanning silently; a status line surfaces the last result.

## Collection and decks

- **Collection** tab: track owned copies of any card (normal/foil quantities) via "ADD TO COLLECTION"
  on a card's detail page. Adjust or remove counts from the Collection tab itself.
- **Decks** tab: create named decks, add cards to them via "ADD TO DECK" on a card's detail page
  (pick an existing deck or create a new one on the spot), and star any commander-eligible card in
  a deck to set it as that deck's commander.

Both are stored on the device as JSON via DataStore (`CollectionRepository`, `DeckRepository`), so
everything works offline and without an account.

## Account and sync

Signing in (Settings → Account & sync; email + password, Supabase Auth) keeps decks and binders in
sync with other phones and the web app (`MtgCompanionWeb`). Each deck and binder is its own row in
`public.library_items` (`supabase/migrations/`); when two devices change the same deck, the edits
are merged card by card rather than one replacing the other.

- **When:** a second after an edit, when the app opens or goes to the background, from the sync
  button or pull to sync, and — while the app is open — within a second of another device saving,
  through Supabase Realtime live updates (`SupabaseRealtime`). A check every 15 seconds (every 60
  while live updates are connected) is the backup.
- **How:** every decision is in `SyncCore` (pure, no I/O); `SupabaseSync` does the storage and
  network around it. Pushes are compare-and-swap (`push_library_items_v2`): a device only overwrites
  the version it merged from. `SyncCoreTest` runs the sync scenarios against both push functions —
  the web app's `npm test` runs the same ones.
- **Signing out** — or being signed out, when the server ends the session — removes the account's
  decks and binders from the phone; they come back on signing in. Sign out syncs first and warns
  about anything that couldn't be sent. If the server ends a session with edits not yet synced,
  those few items are kept out of sight and merged back in when the same account signs in again
  (another account, or 7 days, and they're dropped).

## Build

```bash
./gradlew assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

(Built and verified successfully against an installed Android SDK/JDK 21 on 2026-07-10.)

## Prices

Prices come from Scryfall's bundled snapshots (USD, USD foil, EUR). A card's page links out to
TCGplayer, and a deck can be sent to TCGplayer's mass-entry cart, but the app never calls their API:
TCGPlayer only issues API credentials through a developer programme, so live marketplace pricing was
dropped in v1.84.0 rather than left as code that could never run.

## Project layout

- `network/` — Retrofit API interfaces + Moshi data classes per source (`scryfall`, `spellbook`,
  `edhrec`, `mtgjson`), plus `NetworkModule.kt` wiring up the Retrofit/OkHttp instances.
- `data/` — repositories combining the network layer for each feature (`CardRepository`,
  `EdhrecRepository`, `ComboRepository`, `PreconRepository`, `SettingsRepository`), plus
  on-device storage (`CollectionRepository`, `DeckRepository`) backed by DataStore + Moshi JSON.
- `ui/search`, `ui/detail`, `ui/settings`, `ui/scan`, `ui/collection`, `ui/decks` — Compose screens + ViewModels.
- `ui/nav/NavGraph.kt` — navigation graph, including the bottom nav bar (Search/Collection/Decks).

## Known limitations

- EDHREC's slug algorithm is reverse-engineered from observed URLs (lowercase, strip punctuation,
  spaces to hyphens). It matches every commander tested during development, but edge cases (e.g.
  card names with numerals or unusual symbols) aren't guaranteed.
- "Can this card be a commander" is inferred heuristically (legendary creature, or oracle text
  saying "can be your commander") rather than pulled from an authoritative legality source.
- Combo search calls Commander Spellbook's `card="<name>"` query syntax; extremely long card names
  or unusual characters aren't specifically escaped beyond normal URL encoding.
