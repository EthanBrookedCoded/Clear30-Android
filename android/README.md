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

Android Studio is **not** required — the full loop (build → emulator → install →
launch → screenshot) runs from the CLI. See "Running from the CLI" below.

Prerequisites (already true on this machine as of 2026-07-13):
- Android SDK at `~/Library/Android/sdk` (platform-tools, build-tools, emulator,
  at least one AVD — currently `Medium_Phone_API_36.0`)
- JDK 17+ (`brew install openjdk@21`; select with `/usr/libexec/java_home -v 21`)

One-time steps:
1. **Gradle wrapper jar** — not committed (it's binary). Fetch the pinned
   version straight from the Gradle repo (or let Android Studio regenerate it,
   or `gradle wrapper` if you have standalone Gradle):
   ```bash
   cd android
   curl -fsSL -o gradle/wrapper/gradle-wrapper.jar https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradle/wrapper/gradle-wrapper.jar
   curl -fsSL -o gradlew https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradlew
   chmod +x gradlew
   ```
2. **`local.properties`** (git-ignored) — see the committed comments in
   `app/build.gradle.kts` for the full key list. Minimum for local dev:
   ```properties
   sdk.dir=/Users/<you>/Library/Android/sdk
   SUPABASE_LOCAL=true
   # Anon key of the RUNNING local stack (`supabase status` in the Backend dir);
   # newer CLI versions issue a different demo JWT than the default baked into
   # build.gradle.kts, so set it explicitly.
   SUPABASE_LOCAL_ANON_KEY=<ANON_KEY from `supabase status -o env`>
   # Prod anon key (used only when SUPABASE_LOCAL=false); source: iOS confidential.yml
   SUPABASE_ANON_KEY=<prod anon key>
   # Blank is fine: no Play Store app exists in RevenueCat yet (no goog_ key);
   # all purchase code no-ops on a blank key.
   REVENUECAT_API_KEY=
   ```
3. **`app/google-services.json`** (optional until FCM/Crashlytics needed) —
   Firebase console (project `clear30-24f18`) → add Android apps
   `org.clear30.Clear30v1` and `org.clear30.Clear30v1.debug`, then uncomment
   the `google-services` / `crashlytics` plugin lines in `build.gradle.kts`
   (root) and `app/build.gradle.kts`.

## Running from the CLI

**Shortcut — one command does all of the below:**
```bash
./run.sh              # start backend + emulator if needed, build, install, launch
./run.sh --no-build   # skip the build; reinstall + relaunch the last-built APK
```
It's idempotent (skips whatever is already running) and prints the app's
"Supabase → LOCAL/PROD" log line at the end so you know which backend it hit.

The manual steps, for reference / debugging:

The backend lives in the iOS repo — start it first:
```bash
cd ~/Workspace/iOS/Clear30/Backend && supabase start   # or `supabase status` if already up
```

Build, boot, install, launch (from `android/`):
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
SDK=~/Library/Android/sdk

./gradlew assembleDebug                                # APK → app/build/outputs/apk/debug/app-debug.apk

$SDK/emulator/emulator -avd Medium_Phone_API_36.0 &    # list AVDs: emulator -list-avds
$SDK/platform-tools/adb wait-for-device
# wait for full boot:
until [ "$($SDK/platform-tools/adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 3; done

$SDK/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
$SDK/platform-tools/adb shell monkey -p org.clear30.Clear30v1.debug -c android.intent.category.LAUNCHER 1
```
(The applicationId is `org.clear30.Clear30v1` — it must match the OLD Play
listing so the new app ships as an update; debug builds add a `.debug` suffix.)

Verify it's on the right backend (logged at launch):
```bash
$SDK/platform-tools/adb logcat -d | grep "Supabase →"   # expect: Supabase → LOCAL @ http://10.0.2.2:54321
```
`10.0.2.2` is the emulator's alias for the host machine's `127.0.0.1`; on a
physical device set `SUPABASE_LOCAL_URL` in `local.properties` to your LAN IP.

Screenshot (handy for iOS visual-parity checks):
```bash
$SDK/platform-tools/adb exec-out screencap -p > /tmp/screen.png
```

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
- [ ] SVG vector assets (optional — PNG fallbacks exist for every referenced
      drawable, so this is polish, not a build blocker; see below)
- [ ] Swift removal + Xcode project teardown (most remaining .swift are partially-ported
      re-implementations; delete each as its behavior is fully reproduced)

## Build prerequisites recap

1. Fetch the Gradle wrapper jar + `gradlew` (see "First-time setup"); JDK 17+.
2. Fill `local.properties` (see "First-time setup").
3. Optional: `app/google-services.json` + uncomment the google-services/crashlytics
   plugins (only needed for FCM/Crashlytics).
4. Optional: import the SVG assets as Vector Assets (below).
5. Reconcile non-public Supabase schemas (community/achievements) — see the `// TODO(port)`
   schema notes in `data/supabase/`.

## Assets

91 raster imagesets were migrated from `Assets.xcassets` to `res/drawable-xxxhdpi`
(largest density, Android-safe lowercase names, e.g. `Intro Calendar Flat` ->
`intro_calendar_flat`).

**SVG assets are an optional manual step:** the logo, partner marks
(NIH/umich/harvard/MLB), Reddit/YouTube icons, and the widget-step illustrations
have vector sources in `svg-import/`, but every `R.drawable.*` reference in code
already resolves to a migrated PNG (verified 2026-07-13), so the app builds and
runs without them. For crisper rendering, import each via Android Studio
**File → New → Vector Asset → Local SVG** (or a CLI SVG→VectorDrawable
converter), naming them `clear30_logo`, `nih`, `umich`, etc.
