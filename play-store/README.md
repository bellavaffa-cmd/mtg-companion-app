# Google Play — what's ready and what the owner does

Manabind is distributed today as APKs on GitHub releases, with an in-app updater. This folder is
everything needed to also put it on Google Play. **Nothing here publishes anything.** Play policies
and Console screens change; where this says "Google currently requires…", check it in Play Console
before relying on it.

| File | What it is |
|---|---|
| `listing.md` | App name, short and full description, category, contact fields. `python3 play-store/check-lengths.py` checks the limits. |
| `screenshots.md` | The 8 phone screenshots to take, the feature graphic and icon specs. |
| `content-rating.md` | IARC questionnaire answers. |
| `data-safety.md` | Data safety form answers, derived from the code. |

## What's already done in the code (branch `ux/store`)

- **App bundle in CI.** A real release (`vX.Y.Z` tag or `release-build` push) now also builds
  `app-release.aab` (`bundleRelease -PplayStore=true`, its own Gradle run) and attaches it to the
  GitHub release, after checking it's signed with the release key. The APKs and their names are
  unchanged.
- **No self-updating on Play.** The Play build has `BuildConfig.PLAY_STORE = true`, which turns the
  GitHub updater off, and its manifest drops `REQUEST_INSTALL_PACKAGES` (`app/src/play/`). As a
  second guard, any copy whose installer is Google Play (`com.android.vending`) doesn't update
  itself either (`update/InstallSource.kt`). Settings › App Updates then says "Google Play keeps
  Manabind up to date". `tests.yml` checks the Play manifest has no install permission.
- **Delete my account** in Settings › Account & sync, calling `delete_my_account()`. If the
  server doesn't have it yet the app says so and deletes nothing.
- **Privacy policy** link in Settings › Account & sync (manabind.com/privacy).
- On the web (repo `mtg-companion-web`, branch `ux/store`, **not pushed**): `/privacy`,
  `/delete-account` (signed-in users can delete there), linked from Settings and Account.

## Before anything goes to Play — must fix (blocking)

1. **Target SDK — done on `ux/sdk`.** compileSdk/targetSdk 36 (Android 16), AGP 8.13, Gradle 8.14.3,
   Kotlin 2.2 with the Compose compiler plugin. Edge-to-edge (enforced from 35): the app already drew
   behind hidden system bars; the keyboard is now handled once in MtgNavGraph (adjustResize +
   imePadding) and the life counter's overlays keep clear of cutout/bars/keyboard. Predictive back is
   opted in (`enableOnBackInvokedCallback`). Re-test the camera, notifications, the widget, printing
   and background price checks (WorkManager) on an Android 15/16 phone before a release.
2. **16 KB memory pages — done on `ux/sdk`.** CameraX 1.4.2 (1.3.x's `libimage_processing_util_jni.so`
   wasn't aligned); `tests.yml`'s `native-16kb` job builds a debug APK and fails if any 64-bit `.so`
   isn't 16 KB aligned (`scripts/check-16kb-alignment.sh`, which also runs `zipalign -c -P 16`).
   The Play bundle (`-PplayStore=true`) stores native libs uncompressed; the GitHub APKs keep them
   compressed for download size.
3. **Run the delete-account migration** `supabase/migrations/20261006040000_delete_account.sql`
   in the Supabase SQL editor (project `ftjwwbkqqoctlozubopv`) after reading it — it contains
   DELETE statements by design (only the caller's own rows). Then test with a throwaway account:
   in the app, and at manabind.com/delete-account.
4. **Web pages — done.** `/privacy`, `/delete-account` and `/community-rules` are live on
   manabind.com, with support@manabind.com as the contact (`src/account/contact.ts` in the web repo).
   Make sure that mailbox receives mail.
5. **User-generated content terms — done on `ux/sdk` (app and web).** A one-time Community rules
   sheet before the first profile save, message, trade request/reply and share; the agreement is
   kept on the device and in the account's `user_metadata.community_rules_version`. The rules page
   is manabind.com/community-rules (web `ux/sdk` branch — not live until the web change is pushed),
   linked from Settings and the privacy policy.
6. **Tester build first.** The delete-account and Play changes are app changes: per the usual
   rule, they go out in a Manabind Tester build (with TesterNotes entries for them) before a release.

## Owner's steps in Play Console

1. **Developer account.** https://play.google.com/console/signup — one-time US$25 fee. Choose
   *Personal* or *Organisation* (an organisation needs a D-U-N-S number).
2. **Identity verification.** Google currently requires legal name, address, a verified email and
   phone, and ID documents; a personal account's name is shown on the listing unless you're an
   organisation. Takes from hours to days.
3. **Create the app.** Name from `listing.md`, default language English (United Kingdom), App,
   Free. The package name is fixed by the first upload: `com.mtgcompanion.app`.
4. **Play App Signing — choose carefully.** The GitHub APKs are signed with the release key
   (SHA-256 `c3626947…`). To let people move between the GitHub and Play copies without
   uninstalling (and losing their unsynced library), Play must sign with **the same key**:
   choose *"Use a different key / Export and upload a key from Java keystore"* and follow Google's
   PEPK tool steps with the existing keystore. If you let Google generate a new key instead, the
   Play app and the GitHub app can't update each other — a Play user switching would have to
   uninstall first. With the existing key as the app signing key, CI's `app-release.aab` (signed
   with it) can be uploaded directly; Google may suggest a separate upload key — optional, and if
   you add one, CI would need it too.
5. **Testing — closed test first.** Google currently requires **new personal developer accounts**
   (created after 13 November 2023) to run a **closed test with at least 12 testers who stay
   opted in for at least 14 days in a row** before production access can be requested. Verify the
   current numbers on the Console's dashboard ("Apply for production"). Organisation accounts
   don't have this rule. Practical route: Testing › Closed testing › create a track, add testers by
   Google Groups or email list, upload `app-release.aab` from the latest GitHub release, share the
   opt-in link with 12+ people (friends from the playgroup), and keep them installed for 14 days.
6. **App content** (Policy › App content), all from this folder:
   - Privacy policy: https://manabind.com/privacy
   - App access: everything works without an account; for review, give a demo login (email +
     password of a test account) so reviewers can see Friends and sync.
   - Ads: **No, the app contains no ads.**
   - Content rating: `content-rating.md`.
   - Target audience: **18 and over** is the simplest (avoids Families policy); **13+** is accurate
     too. Do not select any age under 13 — the app has messaging with strangers-by-username and is
     not designed for children. Answer "No" to "could unintentionally appeal to children".
   - News app: **No**.
   - Data safety: `data-safety.md`.
   - Data deletion: https://manabind.com/delete-account
   - Government app: No. Financial features: None. Health: No.
   - Permissions: the Play build declares camera, notifications, NFC, vibrate, internet. No
     sensitive-permission declarations should be needed once `REQUEST_INSTALL_PACKAGES` is gone.
7. **Store listing**: `listing.md` texts, `screenshots.md` images, feature graphic, 512 px icon.
8. **Production.** After the closed test requirement is met, apply for production access, then
   create a production release with the same AAB (or a newer one). First review can take days.

## Releasing once Play is live

Each real release then has two halves:
1. As now: owner says "release X.Y.Z", the release workflow publishes the GitHub release with APKs
   **and** `app-release.aab`.
2. Download `app-release.aab` from that GitHub release and upload it in Play Console (Production ›
   Create new release), with a few lines from `release-notes/vX.Y.Z.md` as the "What's new" text
   (500 characters max). Automating this (a Play service account and the Publishing API) is
   possible later; it would need a new secret and the owner's go-ahead.

`versionCode` must keep increasing for Play (it already does: major*10000 + minor*100 + patch).

## Also worth knowing

- **Developer verification outside Play.** Google has announced that, from September 2026 in some
  countries and later worldwide, certified Android phones will only install apps (including
  sideloaded APKs like the GitHub releases) from verified developers. A Play developer account
  with `com.mtgcompanion.app` registered should cover the GitHub APKs too — verify on the Android
  developer verification pages once the account exists.
- **Trademarks.** The listing uses "MTG" and carries Wizards' Fan Content Policy notice. Don't use
  Magic card art or logos in the feature graphic or screenshots' overlays beyond what's on screen.
