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

**Backend structure**:
- `Backend/supabase/functions/` — edge functions
- `Backend/supabase/migrations/` — SQL migrations
- `Backend/supabase/seeds/` — seed data

## Key commands

**Android**: open `android/` in Android Studio. The Gradle wrapper jar is
git-ignored; let Studio regenerate it or run `gradle wrapper`.

**Build prerequisites** (one-time):
1. Add `android/app/google-services.json` (Firebase, app id `org.clear30`);
   uncomment the `google-services` + `firebase-crashlytics` plugins in both
   `android/build.gradle.kts` and `android/app/build.gradle.kts`.
2. Fill `android/local.properties` with secrets: `SUPABASE_ANON_KEY`,
   `REVENUECAT_API_KEY` (+ Helium / Facebook / AppStack as those are wired).
3. Import the 25 brand SVGs in `android/svg-import/` via Studio →
   `File → New → Vector Asset`; name them `clear30_logo`, `nih`, `umich`, etc.

**Supabase**:
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
