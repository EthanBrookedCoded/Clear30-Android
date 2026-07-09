# Clear30 Android — remaining work for a fully 1:1 app

The Swift/iOS source has been removed from this folder (it lives in your separate
backup). The Android app is structurally complete and runs the full happy path,
but reaching **1:1 parity** with the iOS app still requires the items below.
Ordered roughly by what unblocks the most.

Legend: 🔴 blocking build/run · 🟠 core feature gap · 🟡 depth/polish

---

## 0. Build & run (do these first) 🔴
- [ ] Open `android/` in **Android Studio** (bundles JDK 17+; system JDK here is 11).
- [ ] Generate the Gradle wrapper jar (`gradle wrapper --gradle-version 8.11.1` or let Studio do it).
- [ ] Add `app/google-services.json` (Firebase, app id `org.clear30`); uncomment the
      `google-services` + `firebase-crashlytics` plugins in both `build.gradle.kts` files.
- [ ] Fill `local.properties`: `SUPABASE_ANON_KEY`, `REVENUECAT_API_KEY` (+ Helium key,
      Facebook app id / AppStack key when those are wired).
- [ ] **Expect compile fix-ups.** The code was written without a compiler in the loop;
      the most likely mismatches are the exact **supabase-kt** API surface (rpc/auth/
      schema selection, `decodeAs`/`decodeList`, `functions.invoke`) and **RevenueCat**
      (`PurchaseParams`/`getOfferingsWith`). Reconcile against the installed versions.
- [ ] Reconcile non-public Supabase **schemas** (`community`, `achievements`) — iOS used
      `.schema("community")`; configure the supabase-kt Postgrest schema accordingly
      (see `// TODO(port)` notes in `data/supabase/`).
- [ ] App launcher icons (`mipmap/ic_launcher*`) are not yet added.
- [ ] Import brand **SVGs** from `android/svg-import/` via Studio → New → Vector Asset
      (logo `clear30_logo`, partners NIH/umich/harvard/MLB, Reddit/YouTube icons, widget steps).
- [ ] Re-copy **3D assets** from your iOS backup (`App/Clear30/3D`, ~2.6 MB SceneKit/USDZ)
      and choose an Android approach (Filament / SceneView) if the 3D scene is needed.
      (Videos were preserved into `res/raw`.)
- [ ] Lexend weights: all weights currently map to the single `lexend.ttf`. Add the real
      weight files (or a variable font) for exact type parity.

## 1. Onboarding (New User) 🟠
- [ ] **Assessment question script** — port `AssessmentSlides2` / `AssessmentSlides3`
      (the entire branching question + affirmation sequence). The engine, paging, and
      renderers exist; only the content/branching is stubbed (`AssessmentViewModel.addInitialSlides`).
- [ ] Remaining question renderers: image-choice, date picker, days/number, carousel,
      pop-up cards, multi-spectrum emoji variant, slider-with-custom.
- [ ] Affirmation/info slides: `AssessmentInfoSlide2`, PainPoint, DreamOutcome, SocialProof,
      Credibility, WhatBringsYouHere, loading + `SegmentedCircleAnimation`.
- [ ] `AssessmentSubmissionHandler` — submit assessment (`program_submit_assessment_response_v2`),
      `verifyProgramSetup`, create the break, fetch feedback (`program_get_feedback`).
- [ ] `NormativeFeedbackView` — render `ProgramNormativeFeedback` (score/amount/text cards).
- [ ] Sales slides (currently placeholders): Reviews, Checkmark-Commitment, Referral / School-referral.
- [ ] Intro variants: new-style `IntroScreenVariant`, reviews intro, experiment-driven selection;
      partner/laurel imagery once SVGs are imported.
- [ ] Sign-up: account creation (`create_user`), returning-user/login path, error states,
      `AccountSetupView`; **Apple Sign-In → Google Sign-In** for Android.
- [ ] Mode slideshows + routing: `CounselorSlideshow`, `GenericSlideshow` (counselor/b2b/nys)
      — `AppRoot` currently always routes to `AllNewUser`.
- [ ] Paywall: **Helium** Android SDK, Stripe/Shopify/Superwall fallback chain.
      (Done: hard-paywall detection `isHardPaywall`, free-code auto-skip, RC sign-in
      attribution incl. email/phone/Amplitude, trial-conversion logging. BLOCKED on
      dashboard: the RevenueCat project has no Play Store app — create one and put
      the goog_ key in `local.properties` `REVENUECAT_API_KEY`.)

## 2. Today tab 🟠
- [ ] Full calendar: month paging, week view, `CalendarViewModel`, custom check-in modes
      (split/custom), share calendar.
- [ ] Check-in depth: fullscreen check-in, multi-check-in, puff counter (per-method amounts),
      custom check-in setup/edit, **rewards engine** (`CheckInReward*` static + variable
      generation), pop-ins.
- [ ] Message detail: open a content message (full body, page-info pager, Reddit/YouTube/
      meditation/Instagram embeds, carousel), favorites + visited tracking.
- [ ] Health setback recompute (`updateProgramHealthSetbackDays`), last-smoked handling.
- [ ] **Sync `Program` to Supabase** (`day_info`, `content_info`, `breaks`, `custom_check_ins`)
      — `CheckInLogger` currently saves locally only.

## 3. Community / Groups 🟠
- [ ] Community: reaction toggle/remove, comment owners, tags, video posts, flagging,
      edit/delete, pagination, activity counts, prompts.
- [ ] Groups: create/join/leave (`add_member`/`remove_member`), group calendar, notes,
      member detail, subscriptions, leaderboard.

## 4. Profile / Support 🟠
- [ ] Profile: health timeline (real `ProgramHealthProgress` model + UI), previous breaks,
      dopamine timer, achievements grid (rarity tiers, locked, detail, stats + **sync**),
      program start-date picker, **video journals** (CameraX capture + thumbnail), full
      settings (SMS settings, account, privacy, manage/cancel subscription).
- [ ] Support library: Meditations (**Media3** audio player), Messages library, Journal
      prompts, Reddit viewer, YouTube viewer, School resources.

## 5. Cross-cutting services 🟠
- [ ] **Notifications** (`NotificationHandler`): schedule content/check-in/pop-in/health/
      achievement/abandoned-onboarding notifications, silent-push handling + FCM payload
      routing, per-type channels.
- [ ] **SMS** (Twilio): accountability sign-up flow, contact picker, schedule/clear SMS.
- [ ] `ExperimentController.refreshExperiments` (`get_user_experiments`) + exposure logging.
- [ ] `Logger`: complete the `LogEventType` taxonomy (~40 of 200+ events ported) +
      `AttributionHandler` real SDK wiring (Facebook Android, AppStack alt, Amplitude).
- [ ] Remaining `SupabaseFunctions` (~700 LOC): program restore/sync, groups, Dr Fred,
      payment checks, QA flag, edge functions.
- [ ] App infra: `URLManager` (deep-link routing), `ShortcutHandler`, `PopupManager` +
      popup/highlight/tutorial system, `AlertHandler` (master alert), `LoadingCoordinator`,
      confetti.
- [ ] Program restore/migration: `ProgramSyncHandler` (friend start-date sync deep link),
      message migrations. (Done: `ProgramRestoreHandler` sign-in restore,
      `ProgramTimelineHandler`, wire-model push of content_info/program_breaks/
      custom_check_ins matching the iOS backend contract.)
- [ ] PeerSupport chat; full Dr Fred handler; School/B2B/NYS modes (`SchoolData` fetch,
      leaderboard).

## 6. Polish & parity 🟡
- [ ] Replace the local-state screen toggles with a real **Navigation Compose** stack
      (sheets, toasts, system back).
- [ ] Animation/transition fidelity (springs, blur-replace, numeric-text content transitions).
- [ ] Remaining **widgets**: Snake calendar, Roman calendar, Health, Timer (only `StatsWidget` done).
- [ ] Accessibility, haptics fidelity, localization, tests (XCTest → JUnit/Compose).
