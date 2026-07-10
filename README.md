# MTG Companion

A native Android app (Kotlin + Jetpack Compose) for searching Magic: The Gathering cards, pulling
data from four sources:

- **Scryfall** — card search, rules text, images, and bundled TCGPlayer/Cardmarket price snapshots. No key needed.
- **Commander Spellbook** — infinite combos that use a given card. No key needed.
- **EDHREC** — "cards played with this commander" recommendations, shown for any card EDHREC has a
  commander page for. Uses EDHREC's own JSON endpoints (`json.edhrec.com`); this is not an official
  public API, so it could change or break without notice.
- **TCGPlayer** — optional live marketplace pricing via TCGPlayer's partner API. Requires your own
  API credentials (see below); without them the app just shows Scryfall's bundled price data.

The visual theme (dark near-black background, gold accent, Cinzel/DM Sans fonts) is matched to
[mtgoracle.gg](https://www.mtgoracle.gg/)'s own design system, including its exact color tokens and
bundled Google Fonts.

## Build

```bash
./gradlew assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

(Built and verified successfully against an installed Android SDK/JDK 21 on 2026-07-10.)

## Using TCGPlayer pricing (optional)

TCGPlayer has no self-serve/keyless API tier — a client ID and secret only come from applying to
their developer program at [docs.tcgplayer.com](https://docs.tcgplayer.com/). Once you have one:

1. Open the app, tap the settings icon (top right of the search screen).
2. Enter the Client ID / Client Secret. They're stored locally on-device via DataStore.
3. Card detail pages will now show live TCGPlayer market/low/high prices instead of just the
   Scryfall snapshot.

## Project layout

- `network/` — Retrofit API interfaces + Moshi data classes per source (`scryfall`, `spellbook`,
  `edhrec`, `tcgplayer`), plus `NetworkModule.kt` wiring up the Retrofit/OkHttp instances.
- `data/` — repositories combining the network layer for each feature (`CardRepository`,
  `EdhrecRepository`, `ComboRepository`, `TcgPlayerRepository`, `SettingsRepository`).
- `ui/search`, `ui/detail`, `ui/settings` — Compose screens + ViewModels.
- `ui/nav/NavGraph.kt` — navigation between the three screens.

## Known limitations

- EDHREC's slug algorithm is reverse-engineered from observed URLs (lowercase, strip punctuation,
  spaces to hyphens). It matches every commander tested during development, but edge cases (e.g.
  card names with numerals or unusual symbols) aren't guaranteed.
- "Can this card be a commander" is inferred heuristically (legendary creature, or oracle text
  saying "can be your commander") rather than pulled from an authoritative legality source.
- Combo search calls Commander Spellbook's `card="<name>"` query syntax; extremely long card names
  or unusual characters aren't specifically escaped beyond normal URL encoding.
