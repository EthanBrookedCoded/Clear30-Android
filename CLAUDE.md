# CLAUDE.md

Guidance for Claude Code when working with this Clear30 Android app.

## ⚠️ CRITICAL RULES — READ FIRST

### Android development
- [ ] **Search for existing components first** — don't create new ones if similar
      ones exist. Verify params match your use case.
- [ ] **Use existing utilities** — `Dimens`/`Clear30Gradients`/`Clear30Colors`
      tokens for spacing/color/gradients, `Clear30Card`/`DefaultButton`/text
      composables for UI, `sfSymbol()` for SF Symbol → Material icon mapping.
- [ ] **Match the existing layout style** — `Modifier.cardStyle(...)` /
      `Clear30Card { ... }` for card-like elements; never hard-code spacing —
      always go through `Dimens` (and its `/2`, `/3`, `* 2` variants).
- [ ] **No raw alpha values** other than `0.25`, `0.5`, `0.75`, `1` — same rule
      as the iOS app.
- [ ] **Persist state through `Clear30Store`** — the SwiftData/`ModelContext`
      replacement. Don't reach into DataStore directly.

### Backend (Supabase)
- [ ] **Use `supabase migration new`** for ALL schema changes; never edit
      migrations directly. Test with `supabase db reset` before pushing.
- [ ] **Use MCP Supabase tools** (`mcp__supabase__list_tables`,
      `mcp__supabase__execute_sql`) to verify schema before writing queries;
      DO NOT grep the `Backend/supabase/migrations` folder.
- [ ] Non-public schemas (`community`, `achievements`) need the supabase-kt
      Postgrest schema configured — see `// TODO(port)` markers in `data/supabase/`.
- [ ] **During dev, run against the LOCAL Supabase** (the `supabase` CLI stack)
      **unless otherwise specified.** The env is a build flag: `SUPABASE_LOCAL` in
      `android/local.properties` (default `true` = local). The app logs which env
      it's on at launch (`SupabaseController` → "Supabase → LOCAL/PROD @ <url>").
- [ ] **When debugging a backend issue, query the LOCAL DB to see what's actually
      happening** before guessing — inspect rows, RLS, function output. Don't
      assume; look.

#### Local vs prod Supabase
- **Switch:** `SUPABASE_LOCAL=true|false` in `android/local.properties`, then
  Gradle sync. (Optional overrides: `SUPABASE_LOCAL_URL`, `SUPABASE_LOCAL_ANON_KEY`,
  and `SUPABASE_ANON_KEY` for prod.)
- **Local URL:** the CLI serves `http://127.0.0.1:54321`, but from the Android
  **emulator** that host is `http://10.0.2.2:54321` (the default). On a **physical
  device**, set `SUPABASE_LOCAL_URL` to your machine's LAN IP. Cleartext HTTP to
  these hosts is allowed via `res/xml/network_security_config.xml`.
- **Query / inspect the local DB:**
  - `supabase status` — URLs + keys.
  - SQL: `psql postgresql://postgres:postgres@127.0.0.1:54322/postgres` (or
    `supabase db ... ` / the MCP `execute_sql` against local).
  - Studio UI: `http://127.0.0.1:54323`. Mail (OTP/magic links): `http://127.0.0.1:54324`.

### General
- [ ] **Search/explore first** — never guess file paths or assume code exists.
- [ ] **Ask if unclear** — don't make assumptions about requirements or
      implementation approach.

---

## Project overview

Clear30 is a health and wellness app for substance-use reduction. Tech stack:
- **Android app**: Kotlin + Jetpack Compose (Material3), Glance for widgets
- **Backend**: Supabase (PostgreSQL + edge functions)
- **Payments**: RevenueCat (Helium / Stripe / Shopify fallback chain TODO)
- **External**: Firebase (FCM, Crashlytics), Twilio (SMS — TODO), OpenAI (AI chat
  via Supabase edge functions)

## Quick reference

**Android structure** (mirrors iOS `App/Clear30/` layout):
- `android/app/src/main/java/org/clear30/`
  - `MainActivity.kt`, `Clear30Application.kt`, `AppState.kt`, `AppRoot.kt`,
    `AppRootViewModel.kt` — app entry (iOS `Clear30App.swift` + `ContentView.swift`)
  - `data/` — mirrors iOS `Data/`
    - `model/` — all `@Model`/value-type ports as `@Serializable` data classes
    - `supabase/` — mirrors iOS `Data/Supabase/`
    - root: `LocalStore`, `Clear30Store` (DataStore), `Logger`, `PaywallController`,
      `AttributionHandler`, `GroupController`, `CheckInLogger`, `ProgramMessageHandler`
  - `views/` — mirrors iOS `Views/`
    - `components/` (CardStyle, Text, Buttons, SfSymbols, AiChatScreen)
    - `newuser/` (intro, signup, paywall, sales) + `newuser/assessment/`
    - `existinguser/` (today, profile, community, groups, support)
    - `theme/` (Color, Gradients, Dimens, Anim, Type, Theme, Haptics — iOS `GlobalData`)
  - `widget/` — Glance home-screen widgets (iOS `Clear30 widgets`)
  - `messaging/` — FCM service (iOS `AppDelegate` MessagingDelegate)
  - `util/` — `Dates` (kotlinx.datetime helpers), `Strings`

**Backend structure** — the backend lives in the iOS repo, NOT here
(the `Backend/` folder was removed from this repo 2026-07-13):
- `~/Workspace/iOS/Clear30/Backend/supabase/functions/` — edge functions
- `~/Workspace/iOS/Clear30/Backend/supabase/migrations/` — SQL migrations
- `~/Workspace/iOS/Clear30/Backend/supabase/seeds/` — seed data
- Run all `supabase` CLI commands from `~/Workspace/iOS/Clear30/Backend/`.

## Key commands

**Android — build/run/debug from the CLI** (Android Studio NOT required):
```bash
android/run.sh              # everything: backend + emulator if needed, build, install, launch
android/run.sh --no-build   # reinstall + relaunch last-built APK
~/Library/Android/sdk/platform-tools/adb logcat -d | grep "Supabase →"     # verify env
~/Library/Android/sdk/platform-tools/adb exec-out screencap -p > screen.png  # for iOS parity checks
```
Manual step-by-step (what `run.sh` automates): `android/README.md` → "Running
from the CLI".

**Build prerequisites** (one-time, already done on this machine — details in
`android/README.md`):
1. Gradle wrapper jar + `gradlew` (git-ignored; fetch from the Gradle repo, tag
   `v8.11.1`, or let Studio regenerate).
2. Fill `android/local.properties`: `sdk.dir`, `SUPABASE_LOCAL_ANON_KEY` (from
   `supabase status` — differs from the default in `app/build.gradle.kts`),
   `SUPABASE_ANON_KEY` (prod), `REVENUECAT_API_KEY` (blank OK — no Play Store
   app in RevenueCat yet, purchase code no-ops).
3. Optional: `android/app/google-services.json` + uncomment the
   `google-services`/`firebase-crashlytics` plugins (only for FCM/Crashlytics).
4. Optional: import the 25 brand SVGs in `android/svg-import/` via Studio →
   `File → New → Vector Asset` (all referenced drawables already resolve to
   migrated PNGs, so this is polish, not a blocker).

**Supabase** (run from `~/Workspace/iOS/Clear30/Backend/`):
```bash
supabase migration new MIGRATION_NAME    # always use this for schema changes
supabase db reset                        # test locally
supabase db push                         # deploy
supabase functions new FUNCTION_NAME
supabase functions deploy FUNCTION_NAME
```

## Development patterns

**Compose — adding screens**:
- Place in `views/newuser/` or `views/existinguser/<tab>/` to match the iOS folder
- Pull models from `Clear30Store`; for in-memory state use the `lateinit` props on
  `AppRootViewModel` that are threaded through `AllTabs(...)`
- Use `Clear30Card { ... }` / `Modifier.cardStyle(...)` for card UI
- Use the typed text composables (`Heading1` / `SmallText` / `TinyText` etc.) —
  they inherit `LocalContentColor`, so they go white inside a gradient card
- Map SF Symbol names through `sfSymbol("chevron.backward")` to a Material icon
- Mutate models in place (matching SwiftData) and call `scope.launch { Clear30Store.save(model) }`

**Data layer**:
```kotlin
// Backend call
suspend fun something() {
    SupabaseController.callFunction(SupabaseFunction.getMessages, MyType::class.java)
}

// Persist a singleton model
Clear30Store.save(program)

// Log an analytics event
Logger.logEvent(userInfo.loggingID, LogEventType.openedHome)
```

**Adding a notification**: use the per-type channel constants on
`Clear30Application` (`CHANNEL_CONTENT`, `CHANNEL_CHECK_IN`, ...) so users can
mute each category in system settings — mirrors iOS `ToggleSettingsOption`.

## Important notes

**Security**: secrets live in `android/local.properties` (git-ignored) and reach
the app via `BuildConfig`. RLS policies protect Supabase data. JWT auth
required. Validate user input.

**Performance**: local-first via DataStore-backed `LocalStore`; background sync
via coroutines. Paginate large queries (community feed, achievements).

**Payments**: currently RevenueCat-only. The full Helium → Stripe → Shopify →
Superwall fallback chain from iOS is a TODO — see `android/TODO.md`.

**Experiments**: feature flags via `ExperimentController` (`showFeature` /
`getFeature`). Show/hide/off/custom variants supported.

**Code style** (Kotlin):
- camelCase vars/functions, PascalCase types/composables; `@Composable` fun
  names start with uppercase
- Compose-first state — `mutableStateOf` / `remember` / `LaunchedEffect`; expose
  `StateFlow` from controllers; avoid `LiveData`
- No raw spacing values — always `Dimens.cardSpacing` (and variants)
- No raw alphas other than `0.25`, `0.5`, `0.75`, `1` (iOS rule kept)

**File references**: use `file_path:line_number` (e.g.,
`android/app/src/main/java/org/clear30/AppRoot.kt:30`).

**Remaining work**: see `android/TODO.md` for the full 1:1 parity list.
