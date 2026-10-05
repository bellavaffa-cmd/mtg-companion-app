# Data safety form (Play Console › Policy › App content › Data safety)

Derived from the code on branch `ux/store` (October 2026). Answers are for the **Play build**
(`bundleRelease -PplayStore=true`, package `com.mtgcompanion.app`) — not the Manabind Tester app,
which is a separate package that never goes to Play. Re-check this form whenever a release adds a
server call, an SDK or a new kind of user data.

Play's definitions, in short: *collected* = leaves the phone (to Manabind's server or a third party
the app calls); *shared* = handed to a third party, except when the user plainly asked for it or a
service provider processes it on Manabind's behalf. Data that stays on the phone, or is processed
only on the phone, is neither.

## Overview questions

| Question | Answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | **Yes** | Accounts, sync and friends send data to Manabind's Supabase server. |
| Is all of the user data collected by your app encrypted in transit? | **Yes** | Every server and API call is HTTPS (Supabase, Scryfall, EDHREC, Commander Spellbook, MTGJSON, Frankfurter, Giphy, GitHub, Google). No cleartext traffic is allowed (no `usesCleartextTraffic`). |
| Do you provide a way for users to request that their data is deleted? | **Yes** | In the app: Settings › Account & sync › Delete my account. On the web: https://manabind.com/delete-account (signed-in deletion, or how to ask). |
| Delete account URL (asked separately under App content › Data deletion) | https://manabind.com/delete-account | |
| Is your app's account creation optional? | **Yes** — every feature except sync and friends works signed out. |

Before submitting, the owner must have **run** `supabase/migrations/20261006040000_delete_account.sql`
and **pushed** the web pages: Play reviewers try both.

## Data types

Optional = the user can use the app without providing it. All of the below is **optional**,
because signing in is optional. Nothing is used for advertising or sold.

| Play data type | Collected | Shared | Ephemeral | Required / optional | Purposes | What it is in Manabind |
|---|---|---|---|---|---|---|
| Personal info › **Email address** | Yes | No | No | Optional | Account management, App functionality | The sign-in email (Supabase Auth). Also confirmation and password-reset emails. |
| Personal info › **Name** | Yes | No | No | Optional | App functionality | Display name and username on a friends profile; names typed for guests in pod games, game night, events and loans (synced with the user's library). Shown to friends because the user chose to share — not "sharing" in Play's sense. |
| Personal info › **User IDs** | Yes | No | No | Optional | Account management, App functionality | The account's ID (a UUID) on every synced row. |
| Photos and videos › **Photos** | Yes | No | No | Optional | App functionality | A profile picture the user picks with the system photo picker (no storage permission), stored in the `avatars` bucket and shown to people who can see the profile. |
| Messages › **Other in-app messages** | Yes | No | No | Optional | App functionality | Messages between friends, trade messages and replies, report notes. |
| App activity › **Other user-generated content** | Yes | No | No | Optional | App functionality | The synced library: decks, binders, wishlist, storage places, loans, pod games, shares, trades, trade ratings, the activity feed. |
| App activity › **In-app search history** | Yes | No | **Yes** | Optional | App functionality | Card searches and card names go from the phone to Scryfall (and EDHREC / Commander Spellbook for a card's page) to answer them. Manabind's server doesn't store them; Scryfall etc. are named in the listing, and the user starts each search. |
| App activity › **App interactions** | **Only if anonymous usage counts ship** | No | No | Optional if there's a switch, else Required | Analytics | Counts of which screens/features are used, being added on another branch. If they are in the build you upload, tick this; if they are off or not merged, leave it unticked. Check whether they carry a user or install ID — if they do, they aren't anonymous and User IDs / Device IDs also apply to Analytics. |
| Device or other IDs | Yes | No | No | Optional | App functionality | The Firebase Cloud Messaging token, stored in `push_tokens` so friends and trade notifications reach the phone. Only after the user turns notifications on. |
| App info and performance › Crash logs / Diagnostics | **No** | | | | | Crash and bug reports (`tester_reports`) exist only in the Manabind Tester app (`Tester.on` is false in the release build). If a future Play build sends crash reports, tick these. |
| Location, Contacts, Calendar, Financial info, Health, Audio, Files, Web browsing, Installed apps | **No** | | | | | None of these are read. Prices are card prices, not the user's financial info. |

### Not collected, though it sounds like it

- **Camera:** scanning reads card titles with ML Kit and matches art with an ONNX model, both on the
  phone. Frames never leave the phone (in the Play build — the Tester app can attach a scan picture
  to a report the tester chooses to send).
- **NFC:** writing a badge happens between the phone and the badge.
- **Local library:** without an account, decks and binders stay on the phone.
- **Android Auto Backup** (`allowBackup="true"`) copies app data to the user's own Google account
  backup; that's the user's backup, not collection by Manabind. (It includes the sign-in session —
  worth considering `android:fullBackupContent` rules to leave the auth store out; not changed here.)
- **Google Sign-In** is only used once, by people who used the retired Google Drive sync, to import
  their old backup; the Drive file is read on the phone.

## Security practices section

- Data encrypted in transit: **Yes**.
- Users can request deletion: **Yes** (in-app and https://manabind.com/delete-account).
- Committed to Play Families Policy: **No** (not a children's app).
- Independent security review: **No**.
