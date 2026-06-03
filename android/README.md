# Clear30 — Android

Native Android port of the Clear30 iOS app (Kotlin + Jetpack Compose). This
replaces the SwiftUI/SwiftData app in `../App/`, which is being deleted file by
file as each piece is ported.

## Stack mapping (iOS → Android)

| iOS                         | Android                                            |
|-----------------------------|----------------------------------------------------|
| SwiftUI                     | Jetpack Compose + Material3                         |
| SwiftData (`@Model`)        | Room (collections) + DataStore/JSON (singletons)   |
| `GlobalData` singleton      | `ui/theme` (`Dimens`, `Anim`, `Color`, `Gradients`)|
| `AppDelegate`               | `Clear30Application` + `AppState` + FCM service     |
| Supabase Swift SDK          | supabase-kt (`io.github.jan-tennert.supabase`)      |
| RevenueCat                  | `com.revenuecat.purchases`                          |
| Firebase / Crashlytics      | Firebase Android BOM                                |
| `WidgetKit`                 | Glance (`androidx.glance:glance-appwidget`)         |
| `AVFoundation` video        | Media3 / ExoPlayer                                  |

## First-time setup (required to build)

1. **Open this `android/` folder in Android Studio** (Ladybug or newer). Studio
   ships JDK 17+; the system JDK here is only 11, which is too old for AGP 8.7.
2. **Generate the Gradle wrapper jar** — not committed (it's binary). Either let
   Android Studio do it on import, or run:
   ```
   gradle wrapper --gradle-version 8.11.1
   ```
3. **Add `app/google-services.json`** (Firebase console → Android app
   `org.clear30`), then uncomment the `google-services` / `crashlytics` plugin
   lines in `build.gradle.kts` (root) and `app/build.gradle.kts`.
4. **Add secrets** (Supabase URL/anon key, RevenueCat key, etc.) — these were in
   `confidential.yml` / ConfidentialKit on iOS. Put them in `local.properties`
   (git-ignored) and surface via `BuildConfig`; wiring lands with the
   `SupabaseController` port.

## Port status

- [x] Gradle project + version catalog
- [x] Design system (colors, gradients, dimens, animations, typography, theme, haptics)
- [x] App entry (Application, MainActivity, AppState, FCM service)
- [x] Data models (all @Models + value types via kotlinx-serialization + DataStore)
- [x] Data-layer services core (Supabase client/auth/community, Logger, Paywall, Attribution, GroupController, CheckInLogger)
- [x] Onboarding pipeline (intro, assessment renderers, sign-up OTP, notifications, paywall)
- [x] Main tab shell + all 5 tabs (Today, Community, Groups, Profile, Support)
- [x] Shared components (CardStyle, text scale, buttons, SF-symbol mapping)
- [x] Home-screen widget (Glance StatsWidget)
- [x] Today: day badge, program summary, live "time clear" timer, check-in (writes ProgramDayInfo), month calendar, content feed (program_get_messages)
- [x] Profile: live stats, achievements (fetch), journal (text CRUD), notification settings, sign-out
- [x] Community: feed (read), post detail + reactions + comments, create post
- [x] Support: Claire + Dr Fred AI chat (shared AiChatScreen)
- [~] Remaining depth: full assessment question-script (AssessmentSlides2/3), rewards engine,
      health timeline, previous breaks, video journals (CameraX), Reddit/YouTube/meditation
      viewers, program sync/restore handlers, push-notification scheduling
- [ ] SVG vector assets (manual Studio import — see below)
- [ ] Swift removal + Xcode project teardown (most remaining .swift are partially-ported
      re-implementations; delete each as its behavior is fully reproduced)

## Build prerequisites recap

1. Open `android/` in Android Studio (JDK 17+); generate the Gradle wrapper jar.
2. Add `app/google-services.json` and uncomment the google-services/crashlytics plugins.
3. Put secrets in `local.properties`: `SUPABASE_ANON_KEY`, `REVENUECAT_API_KEY`.
4. Import the SVG assets as Vector Assets (below).
5. Reconcile non-public Supabase schemas (community/achievements) — see the `// TODO(port)`
   schema notes in `data/supabase/`.

## Assets

91 raster imagesets were migrated from `Assets.xcassets` to `res/drawable-xxxhdpi`
(largest density, Android-safe lowercase names, e.g. `Intro Calendar Flat` ->
`intro_calendar_flat`).

**SVG assets need a manual step:** the logo, partner marks (NIH/umich/harvard/MLB),
Reddit/YouTube icons, and the widget-step illustrations are vectors. Import each
via Android Studio **File → New → Vector Asset → Local SVG**, naming them
`clear30_logo`, `nih`, `umich`, etc.
