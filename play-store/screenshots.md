# Phone screenshots (8) and the feature graphic

Play wants 2–8 phone screenshots, PNG or JPEG, 16:9 or 9:16, each side 320–3840 px (the long side
no more than twice the short side). Take them on a real phone, portrait, at 1080 × 1920 or the phone's
own resolution if it's 9:16 or close (Play also accepts up to 9:20 — crop if it rejects one).

Before taking them:
- Use the **release build** (not the Tester app — its overlay and name would show) on a clean
  account made for the purpose, e.g. "Manabind Demo".
- Dark theme, Gold accent (the default look), status bar tidy (full battery, no notifications —
  Android's demo mode: Developer options › System UI demo mode).
- Real card data only. Use a believable mid-sized collection (300–600 cards) and a couple of
  well-known Commander decks; no real people's names — guests are "Alex", "Sam", "Jo".
- No prices that look like financial advice; whatever Scryfall shows on the day is fine.

| # | Screen | What should be on it | Caption idea (optional, overlaid in an editor) |
|---|---|---|---|
| 1 | **Scanner** (Scan tab, add mode) | Camera on a real card on a table, the card recognised, 3–4 cards already in the running list below. | Scan cards in seconds |
| 2 | **Storage** (Collection › Storage) | A shelf with two boxes (one with a colour sort rule) and two binders, with card counts and values. | Know where every card is |
| 3 | **Binder pages** (a binder › Pages) | A full 9-pocket page of a good-looking binder (e.g. a set's rares), page arrows visible. | Your binders, page by page |
| 4 | **Deck** (a Commander deck › Cards, grouped by category) | A well-known commander (e.g. Atraxa or Edgar Markov) with the legality badge, categories and the mana curve on Stats if it fits. | Build and check your decks |
| 5 | **Build this deck** (pull list) | The pull list grouped by place, a few cards ticked. | Pull a deck from your boxes |
| 6 | **Life counter** (4-player layout) | Four seats with commander art, a mid-game state (life 31, 24, 18, 40; some commander damage, a poison counter). | A life counter for the whole table |
| 7 | **Collection value / Advanced filters** | All cards with two or three filter chips on and the count and value line; or Value by place's bars. | Find any card in your collection |
| 8 | **Friends** (Friends tab) | Two or three demo friends, a trade waiting, a shared binder. Use demo accounts you own, with demo display names and no real faces as avatars. | Trade and share with friends |

Optional extras (Play allows more for tablets): a 7" and 10" tablet set from the same screens if the
owner wants the tablet badge — not needed.

## Feature graphic (required)

- **1024 × 500 px**, PNG or JPEG, no transparency.
- Background: the app's near-black (#0E0E10 or the theme's `Bg`) with a soft gold glow.
- Left: the Manabind wordmark (manabind.com's `wordmark.svg`) in gold, and one line under it:
  "Your Magic collection, decks and games".
- Right: three tilted card-shaped silhouettes or the app icon — **not** real Magic card art or the
  Magic logo (Wizards' artwork and marks aren't ours to use in marketing).
- Keep important content inside the central 924 × 400 px: Play crops the edges in some places and
  may lay a play button over the middle if a promo video is added.
- No "Best", "#1", "Free" or prices in the graphic (Play's metadata policy).

## App icon

- **512 × 512 px**, 32-bit PNG, up to 1 MB. Export it from the launcher icon's source
  (`app/src/main/res/mipmap-*` is too small), with the same artwork. Play applies the
  rounded mask itself, so give it a full square background.
