# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Layout

DriveMark is two independent clients that write to the **same Google Sheets spreadsheet** in the user's Drive:

- `chrome-extension/` — Manifest V3 Chrome extension (vanilla JS + vendored Preact/htm)
- `android-app/` — Jetpack Compose Android app (Hilt + Room + Google API client)
- `docs/` — setup/publication guides referenced throughout this file

Each client owns its tooling under `<client>/scripts/` (`android-app/scripts/` for the Android gradle/adb wrappers, `chrome-extension/scripts/` for the extension).

There is no monorepo tooling tying the two clients together. They share only the Sheet row schema and the Google Cloud OAuth project.

## Common Commands

### Chrome extension
No bundler. Load `chrome-extension/` as an unpacked extension at `chrome://extensions`. After editing `background.js` or `manifest.json`, click the reload icon; popup files hot-reload on popup reopen. Debug the service worker via the "Service Worker" link on `chrome://extensions` — the popup hides real auth errors, they only appear in the SW console.

The one build step is vendoring Preact/htm. From `chrome-extension/`:
```sh
npm install          # installs deps AND builds vendor/ (postinstall)
npm run vendor       # rebuild vendor/ from node_modules (copies pinned Preact/htm)
npm outdated         # report available dependency updates (Preact/htm/vitest)
```
`vendor/` is a **gitignored build artifact** — a fresh clone must run `npm install` before loading the extension. To upgrade Preact/htm, bump the pinned version in `chrome-extension/package.json`, run `npm install`, and commit `package.json` + `package-lock.json`.

### Android app
From `android-app/`:
```sh
./gradlew assembleDebug        # debug APK → app/build/outputs/apk/debug/
./gradlew installDebug         # install to connected device/emulator
./gradlew assembleRelease      # signed release APK (requires keystore.properties)
./gradlew bundleRelease        # AAB for Play Store
```

Convenience scripts (from `android-app/`):
```sh
scripts/dev-build.sh           # assembleDebug
scripts/dev-deploy.sh [serial]  # install + launch on device
```

Launch / test the share intent:
```sh
adb shell am start -n com.drivemark.app/.MainActivity
adb shell am start -a android.intent.action.SEND -t text/plain \
  --es android.intent.extra.TEXT "https://example.com" com.drivemark.app
```

JVM unit tests live under `app/src/test/` (Robolectric + MockK + Turbine, coverage via Kover) — run with:
```sh
./gradlew test                 # all JVM unit tests
./gradlew testDebugUnitTest    # debug variant only
./gradlew koverHtmlReport      # coverage report → app/build/reports/kover/
```
There are no instrumentation tests (`app/src/androidTest/` does not exist).

## Required Configuration (both clients)

Both clients authenticate against the **same GCP project** using a **Web application** OAuth client ID with the single scope `https://www.googleapis.com/auth/drive.file`. The Android OAuth credential exists only to register the debug SHA-1; the app uses the Web client ID for ID tokens.

- `chrome-extension/config.js` → `CLIENT_ID` (dev config, imported directly by `background.js`); `config.prod.js` (prod, swapped into the package by `chrome-extension/scripts/publish.sh`).
- `android-app/config.dev.properties` → `webClientId` (dev); `config.prod.properties` (prod); both read by `build.gradle.kts`.
- **These config files are gitignored (not committed).** Create each from its committed `.example` counterpart, then fill in the client ID: `cp config.example.js config.js`, `cp config.prod.example.js config.prod.js`, `cp config.dev.example.properties config.dev.properties`, `cp config.prod.example.properties config.prod.properties`.
- Redirect URI for the extension: `https://<extension-id>.chromiumapp.org/` (the `key` field in `manifest.json` pins the unpacked extension ID for OAuth stability — **must be removed before Web Store packaging**, see `docs/publication.md`)

Release signing reads `android-app/keystore.properties` (gitignored). Missing file → only debug builds work.

Full setup walkthroughs live in `docs/chrome-extension-development.md`, `docs/android-development.md`, and `docs/publication.md`.

## Shared Data Contract

Both clients read/write `Sheet1` with columns **A–I**: `URL | Title | Folder | Date Added | Notes | Excerpt | Cover | ID | Modified`. The sheet tab must be named `Sheet1`.

Key invariants any change must preserve:

- **UUID in column H is the record identity.** Legacy rows without an ID are back-filled on first load.
- **Append-only with tombstones.** Updates and deletes append a new row rather than mutating in place. Deletes are rows with an empty URL. Readers collapse rows by `ID`, keeping the one with the latest `Modified` (falling back to `Date Added`), and drop tombstones.
- **Cleanup is explicit.** `cleanupSheet` in `chrome-extension/background.js` physically deletes superseded/tombstone rows via `batchUpdate`/`deleteDimension`. No automatic compaction.

When adding fields, extend the column range consistently in both clients and the `ensureHeaders` writer.

## Chrome Extension Architecture

Entry points:
- `background.js` — MV3 service worker. All Google API access (Sheets, Drive) goes through `authedFetch`, which retries once on 401 after re-auth. Owns the in-memory `cache` object, mirrored to `chrome.storage.local` under `sheetCache` so the SW can be killed and restored. Messages from the popup are dispatched by `handleMessage`.
- `popup.js` — thin router. Holds a single `state` object (see `lib/state.js`), re-renders the whole Preact tree via `setState`. Three screens: `login` / `picker` / `main`. The main screen uses a **two-phase load**: first paints cached data (`getCachedSheetData`), then revalidates against Drive `modifiedTime` (`getSheetData`). A 403/404 on revalidation means the selected sheet is gone — clear `spreadsheetId` and return to the picker.

Module layout:
- `auth.js`, `spreadsheet-picker.js`, `save-form.js`, `browser.js` — one screen/component group each, exporting both Preact components and their business-logic helpers.
- `lib/state.js` — global mutable state + render callback (no framework state).
- `lib/html.js` — `htm` bound to Preact `h`.
- `lib/tree.js`, `lib/helpers.js` — folder grouping, date formatting, `sendMessage` wrapper, error surface.
- `vendor/` — Preact + htm ES modules, built from `node_modules` by `scripts/build-vendor.mjs`. Gitignored build artifact; never edit by hand, regenerate with `npm run vendor`. Only the modules `lib/html.js` imports are vendored.

The cache-vs-Drive `modifiedTime` check is the only mechanism keeping multi-client writes consistent — a stale `modifiedTime` here means the user sees stale bookmarks.

## Android Architecture

Package root: `com.drivemark.app`. `minSdk = 26`, `compileSdk = 37`, `targetSdk = 36`, Java/Kotlin target 17. Single-activity Compose app (`MainActivity`) hosting a `NavGraph`.

Layers under `app/src/main/java/com/drivemark/app/`:
- `data/remote/` — `GoogleAuthManager`, `GoogleSheetsService`, `GoogleDriveService`, `MetadataExtractor` (Jsoup-based OG tag scraping).
- `data/local/` — Room (`DriveMarkDatabase`, `BookmarkDao`, `SpreadsheetDao`, entities) + `PreferencesManager` (DataStore).
- `data/repository/` — `AuthRepository`, `BookmarkRepository`, `SpreadsheetRepository` are the only boundary the UI calls into.
- `di/AppModule.kt` — Hilt bindings. New `@Inject`/`@Module` requires a rebuild (KSP codegen).
- `ui/` — Compose screens (`login`, `picker`, `bookmarks`, `detail`, `save`) + `navigation/` (route sealed class + `NavGraph`) + `theme/`.

Auth uses Credential Manager + `GoogleIdOption` with `BuildConfig.WEB_CLIENT_ID`. API calls use the Google API Java client (`google-api-client-android`, `google-api-services-sheets`, `google-api-services-drive`).

Share intents: `MainActivity` accepts `ACTION_SEND` / `text/plain`, extracts the first URL via `Patterns.WEB_URL`, and stashes it as Compose state (`pendingShareUrl`). A `LaunchedEffect` navigates to `Screen.SaveBookmark` once the user is past login/picker — deep-linking into that route before auth would skip the gate.

`DriveMarkApplication` is `@HiltAndroidApp`; `MainActivity` is `@AndroidEntryPoint`. Any new Activity needs the same annotation or Hilt injection will crash on launch.

## Things That Are Easy to Break

- **Sheet header order.** Column index is hardcoded in `parseRows` (extension) and the Android mapper layer. Reordering or inserting columns requires coordinated changes.
- **Tombstone semantics.** If you add a new deletion path, it must append an empty-URL row with the existing `ID` and `dateAdded`, not mutate or physically delete (except inside `cleanupSheet`).
- **Extension ID drift.** Removing the `manifest.json` `key` for dev or forgetting to strip it for Web Store upload both break OAuth. `docs/publication.md` covers the publish flow.
- **`WEB_CLIENT_ID` placeholder.** `config.dev.properties` is gitignored, so a fresh clone has no client ID — the build falls back to `MISSING_WEB_CLIENT_ID`. Copy `config.dev.example.properties` → `config.dev.properties` and set a real client ID before `assembleDebug`.
