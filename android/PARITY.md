# Clear30 Android — Parity & Bug Tracker

> Single source of truth for iOS→Android parity work. Merges the 2026-07-13 code
> audit (5-agent deep dive), Thatcher's emulator smoke-test findings, prod-DB
> verification, and Thatcher's product decisions (§17). **Supersedes `TODO.md`
> for planning** (TODO.md is stale in both directions — see §14).

## For AI agents working from this doc

- **Read `CLAUDE.md` first** and follow it (design tokens, `Dimens`, no raw
  alphas, `Clear30Store` persistence, local-Supabase-by-default, MCP for schema
  verification).
- **Run against the LOCAL Supabase stack** (`SUPABASE_LOCAL=true`). Never point
  a dev build or test account at prod. The backend lives in the iOS repo:
  `~/Workspace/iOS/Clear30/Backend/` (run `supabase` CLI from there).
- **iOS is the behavior spec** unless an item says otherwise. Verify against the
  cited iOS source before implementing — don't trust this doc's summary over
  the code. Scheduling/break/restore semantics are additionally specced in
  `~/Workspace/iOS/Clear30/Docs/MESSAGE_TIMELINE_ARCHITECTURE.md`.
- **Every item cites evidence** as `file:line` (valid as of 2026-07-13; re-grep
  if lines have drifted). Paths: `A/` = `android/app/src/main/java/org/clear30/`,
  `iOS/` = `~/Workspace/iOS/Clear30/App/Clear30/`, `BE/` =
  `~/Workspace/iOS/Clear30/Backend/`.
- **Verify by building and exercising the flow** (CLI runbook in
  `android/README.md`), and where an item touches the backend contract, inspect
  the local DB rows (`psql postgresql://postgres:postgres@127.0.0.1:54322/postgres`)
  to confirm the written JSON matches the iOS shape.
- **Tick the checkbox and append `(done <date>, <commit>)`** to an item when it
  lands. Don't remove items; strike through only if invalidated.

Severity: **P0** = data integrity / prod blocker · **P1** = core-flow bug ·
**P2** = needed feature gap · **P3** = polish.
Status: `bug` (confirmed in code) · `missing` · `divergent` (works, differs
from iOS) · `env` (local/dev environment task).

## P0 quick list (prod gate — nothing ships before these)

| ID | Item |
|----|------|
| X1 | `encodeDefaults=false` serializer bug (breaks iOS restore; drops `platform`; 3 symptoms, 1 fix) |
| X2 | Double assessment submission per signup (fix with B4) |
| X3 | Live content path (`fetchAndApply`) wipes per-day progress; faithful scheduler is dead code |
| X4 | No existing-account detection in sign-up path (data-loss risk) |
| X5 | `latest_check_in_method` write to a column that doesn't exist in prod |
| B1 | `restartBreak` off-by-one (today's progress not reset) |
| B2 | `endBreak` must land users in weed-free, mirroring iOS (decided §17-Q5) |

## Suggested implementation order

1. **Wave 1 — P0 data integrity:** X1 → X2+B4 → X5 → B1 → B2 → X3 → X4.
2. **Wave 2 — onboarding correctness:** O1, O2, O3, O4+X7, O6, O7, O8, O10.
3. **Wave 3 — environment (unblocks testing):** E1–E4, N1.
4. **Wave 4 — content & viewers:** T7+S5 (reddit), S8 (YouTube), S6, T5, T6,
   S1–S4, S7, S9–S11.
5. **Wave 5 — check-in / profile / community / groups:** T2–T4, P1–P6, C1–C3, G1.
6. **Wave 6 — pilot features:** F1–F3, O11, B3, X6, S12, N2.

---

## 1. Cross-cutting / data integrity

- [x] **X1 · P0 · bug — Serializer omits default/empty fields; breaks the backend contract.** (done 2026-07-13, 3a63877 — verified locally: `platform='android'`, `loggedSymptoms` in day_info, `message_ids` in content_info; iOS-simulator cross-check still worth doing)
  The shared push encoder sets `explicitNulls=false` and leaves `encodeDefaults`
  false (`A/data/supabase/SupabaseController.kt:36-39`). Three confirmed symptoms:
  (a) `SupabaseNewUser.platform: String = "android"` (`A/data/supabase/SupabaseUser.kt:46`)
  is omitted from the `create_user` payload → the RPC defaults the row to `'ios'`
  (verified against the live `create_user` function — it validates `platform`
  correctly; the bug is purely client-side).
  (b) Empty `loggedSymptoms` omitted from `day_info` objects
  (`A/data/model/ProgramCheckIns.kt:145-151`) → iOS decodes that key
  **non-optionally** (`iOS/Data/Program/ProgramCheckIns.swift:66`) → iOS sign-in
  restore of Android-written data **throws**.
  (c) Empty `message_ids` omitted from `content_info` entries
  (`A/data/supabase/SupabaseRestore.kt:38-40`) → same iOS decode failure.
  **Fix:** `encodeDefaults = true` on the push/create encoder (or emit those
  fields explicitly). **Verify:** sign up on Android against local DB, then
  sign in on the iOS simulator with the same account — restore must succeed;
  inspect the `users` row: `platform='android'`, `day_info` objects contain
  `loggedSymptoms`, `content_info` entries contain `message_ids`.

- [x] **X2 · P0 · bug — Assessment submitted twice per signup.** (done 2026-07-13, 3a63877 — verified: exactly one `program_assessment_responses` row after full signup incl. payment; `verifyProgramSetup` ported, closing B4)
  `AssessmentSubmissionHandler.submitAssessment` runs after OTP verify
  (`A/views/newuser/signup/AllSignUp.kt:183-187`) AND again at payment
  (`A/views/newuser/AllNewUserViewModel.kt:175-182`) → duplicate
  `program_assessment_responses` rows, double break registration. iOS submits
  once and runs only `verifyProgramSetup` at payment
  (`iOS/Views/New User/Assessment/AssessmentSubmissionHandler.swift:443-467`,
  called from `AllNewUser.swift:290`). **Fix:** remove the payment-path
  re-submit; port `verifyProgramSetup` there instead (closes B4). **Verify:**
  one `program_assessment_responses` row per signup in local DB.

- [x] **X3 · P0 · bug/divergent — Two parallel schedulers; live path is the wrong one.** (done 2026-07-13, 3a63877 — onboarding now runs `ProgramTimelineHandler.start` → faithful `schedule()`/`scheduleStartSoon()`; `fetchAndApply`/12h timer deleted; refresh = iOS `updateMessages` port: in-place field copy by messageID keyed on `program_get_latest_update`, so buckets/progress/unlockOn are never rebuilt. `ensureContent` keeps an empty-timeline repair path for old dev installs.)
  Faithful ports exist (`ProgramMessageHandler.schedule()`/`scheduleStartSoon()`,
  `A/data/ProgramMessageHandler.kt:122,151`) but the onboarding/refresh path uses
  simplified `fetchAndApply()` (`:86`, called from TodayTab/SupportTab) which:
  has no `removePrefix`; never schedules `start_soon_messages`; and
  `putAll(byDate)` **replaces ContentInfo buckets with `progress=null`** on every
  12h refresh — wiping completed-day progress (`:104`). Also uses a local 12h
  staleness timer instead of the server `program_get_latest_update` version
  (`:106,222-226`). `ProgramTimelineHandler.start()/startWithClear30()` are dead
  code (no call sites). **Fix:** wire onboarding + refresh through the faithful
  scheduler; refresh must merge in place (preserve `progress`) and use the
  server content version. **Verify:** complete a day, force a refresh, progress
  survives; future-dated break produces start-soon topics.

- [x] **X4 · P0 · bug — No DB-level existing-account detection at sign-up.** (done 2026-07-13, 3a63877 — `checkIfReturningUser` runs after every OTP verify; verified: wipe + sign-in restores the full program state incl. the day's check-in. Also fixed the restore decode of `fetch_messages_by_ids` raw column shapes — meditation/resources/legacy claire_prompts are `{title: value}` maps, converted like iOS `SupabaseModels.swift:247-274`; A5's non-empty-content_info validation folded in as planned.)
  New-vs-returning decided solely by the "Sign In" button flag
  (`A/views/newuser/signup/AllSignUp.kt:183`). **Fix:** port iOS
  `checkIfReturningUser` (`iOS/.../AllSignUpViewModel.swift:249-301`): after
  every OTP verify, `getUserID` + non-empty `content_info` → route to restore.
  Old-Android-app users (~3.1k prod rows with EMPTY content_info/breaks —
  verified) will correctly fall through to fresh onboarding while keeping their
  users row/ID; their fuller local-data migration is deferred (§18-D2).

- [x] **X5 · P0 · bug — Write to nonexistent column.** (done 2026-07-13, 3a63877 — write deleted from syncProgramState + CheckInLogger; `latestCheckInMethod` stays device-local) `syncProgramState` writes
  `latest_check_in_method` (`A/data/supabase/SupabaseProgramSync.kt:86-93,110`);
  the column **does not exist in prod** (verified). **Fix:** delete the write.

- [ ] **X6 · P1 · missing — `trigger_responses` (if-then slip plans) never restored or written.**
  Wire model carries it (`A/data/supabase/SupabaseRestore.kt:121`) but
  `restoreToModels` drops it; no write path exists. iOS:
  `iOS/Data/Program/ProgramRestoreHandler.swift:103-115`. (Pairs with S12.)

- [x] (done 2026-07-13 — response ID in 3a63877, normative_feedback in 6d33fe8; both verified in the users row) **X7 · P1 · missing — Break rows pushed with null `assessment_response_id` + `normative_feedback`.**
  Android never captures the submit-response ID onto the break and never calls
  `program_get_feedback` (`A/data/AssessmentSubmissionHandler.kt:82-99`; iOS:
  `AssessmentSubmissionHandler.swift:151,166`). Fix together with O4.
  *(2026-07-13, 3a63877: half done — `assessment_response_id` is now captured
  onto the break at submit; only the `normative_feedback` fetch remains, O4.)*

- [ ] **X8 · P2 · divergent — loggingID fallback is a fresh random UUID per install**
  (`A/AppRootViewModel.kt:79-80`) vs iOS's stable `identifierForVendor`
  (`iOS/Views/ContentView.swift:198-216`). Pollutes the prod `logging_id`
  append-history array (verified it's an array). **Fix:** stable device ID
  (e.g. `Settings.Secure.ANDROID_ID`).

- [ ] **X9 · P3 · divergent — Lifecycle hooks stubbed:** no content-info flush /
  `endedSession` on background, no `openedApp` on start
  (`A/MainActivity.kt:58,63` TODOs).

## 2. Auth / sign-up / sign-in

*(User-ID resolution matches iOS cross-platform — same `auth_id → users.id`
lookup, no client-minted IDs. Restore pipeline verified faithful except X6.
Google Sign-In: NOT needed — phone/email OTP is enough for launch, §17-Q8.)*

- [ ] **A1 · P1 · missing — Email OTP verify fallback.** iOS tries `signup` then
  `.email` types (`iOS/Data/Supabase/SupabaseFunctions.swift:105-119`); Android
  single type (`A/data/supabase/SupabaseAuth.kt:28-30`) → existing email users
  may fail to verify.
- [ ] **A3 · P3 · partial — Phone entry:** no E164 normalization; region picker
  hardcoded `+1` (`A/views/newuser/signup/AllSignUp.kt:99,282-294`).
- [ ] **A4 · P3 · partial — Attribution:** `appstack_id` never populated,
  `appstack_attribution` field absent (`A/data/supabase/SupabaseUser.kt:41-42`).
- [ ] **A5 · P3 · partial — Restore misc:** adolescent-mode not set on restore;
  returning-user validation doesn't require non-empty content_info (folded into
  X4's fix).

## 3. Onboarding & assessment

- [x] (done 2026-07-13, 6d33fe8 — verified: Moderation → back → Taking-a-break lands the Clear30 track) **O1 · P1 · bug — "Quitting → Better Life Program" routing.**
  **Requirement (§17-Q1): the most recent What-brings-you-here choice must win.**
  Root cause: only the Moderation branch touches `choseClear30` (sets `false`,
  `A/views/newuser/assessment/AssessmentSlides3.kt:203`); nothing resets it to
  `true`, so back-navigating after touching Moderation poisons the flag; the
  program-confirmation slide branches on it (`:113-114`). **Fix:** explicitly
  set `choseClear30 = true` in the Quit/Break/Don't-know branches (0/1/3) so the
  latest answer always determines the track.

- [x] **O2 · P1 · bug — Wrong assessment ID for Life onboarding.** Android submits
  `"life"` (`A/data/AssessmentSubmissionHandler.kt:76-80`); must be
  `"life-onboarding"` with all onboarding answers (iOS
  `AssessmentSubmissionHandler.swift:224-225`). `"life"` is the post-assessment
  ID (used by F3). One-line change; payload already sends all responses.
  (done 2026-07-13, 3a63877 — landed as part of X3's `handleLife` port, which
  submits `AssessmentType.LifeOnboarding`)

- [x] (done 2026-07-13, 6d33fe8) **O3 · P2 · decided — Remove moderation-vs-weed-free question from the Life branch.**
  **Decision (§17-Q2):** the ONLY route into Life is selecting "Moderation" on
  What-brings-you-here; do not ask mod-vs-weed-free afterward — the auto-set
  `LO_USE_STATE=1` (moderation) stands. **Fix:** drop `modAbsQuestion()` from the
  `lifeContext` branch (`A/.../AssessmentSlides3.kt:123`). Note: current iOS
  *does* still ask it (`iOS/.../AssessmentSlides3.swift:272-276`) — this is an
  intentional Android divergence per Thatcher; iOS change out of scope here.

- [x] (done 2026-07-13, 6d33fe8 — CORRECTION: the onboarding feedback comes from the `program_generate_normative_feedback` EDGE function (iOS `getNormativeFeedback`), NOT the `program_get_feedback` RPC — that RPC feeds the legacy `[ProgramAssessmentFeedback]` cards. Submit → feedback → break (X7) → `Feedback` screen after SignUp via `Program.initialFeedback`, all verified e2e. Locally requires `supabase functions serve --no-verify-jwt` (E1); the local runtime rejects even the local anon key without the flag.) **O4 · P2 · missing — Normative feedback never shown.** Three breaks:
  `program_get_feedback` (plain RPC — no edge functions needed) has zero call
  sites (`A/data/supabase/SupabaseController.kt:167`); the `Feedback` screen is
  never inserted into the flow (`A/views/newuser/AllNewUserViewModel.kt:78-91`,
  TODO at `:83`); submission runs post-payment so no break exists during the
  sales slides. `NormativeFeedbackView` is fully ported. **Fix:** submit after
  sign-up (see X2), call `program_get_feedback`, store on the break (X7), insert
  the `Feedback` screen after `SignUp`.

- [ ] **O5 · P2 · missing — Social-proof cards should open detail sheets** (review /
  community post), incl. live `getCommunityPostById` fetch. Android only inline-
  expands (`A/views/newuser/assessment/AssessmentSocialProof.kt:517-603`); iOS
  sheets at `AssessmentSocialProof.swift:124-173`.

- [x] (done 2026-07-13, 6d33fe8) **O6 · P2 · bug — Shuffle flag never consumed.** `shuffled=true` set on
  breakReason/triggers (`A/data/model/ProgramAssessmentQuestions.kt:97,351`) but
  no renderer reads it. **Fix:** port iOS's shuffle-once-with-"Other"-pinned-last
  (`iOS/.../AssessmentMultipleChoice.swift:70-81`) into Android's
  `AssessmentMultipleChoice`, computed in `remember{}` so redraws don't reshuffle.

- [x] (done 2026-07-13, 6d33fe8 — Method badge + Monthly Spend cards added to the hero-card state; dead MoneyLossView removed; verified "🍪 Edible" + "😬 -$140") **O7 · P2 · decided — Pain-point slide: port the consumption-method variant.**
  **Decision (§17-Q3):** target is iOS `AssessmentPainPoint.swift` (the variant
  currently shipping on iOS — experiment off): `showCard` state shows a
  **Method** card from `consumptionMethod.question.badges[responseIndex]`
  (`:54-67`) + **Monthly Spend** card = `weeklySpend*4` rendered "✅ $0" /
  "😬 -$X" (`:69-85`) + the age×help-harm Impact card. Android currently ported
  the *other* variant (`AssessmentPainPoint2`) and even dropped its money stage
  (`MoneyLossView` defined, never called — `A/.../AssessmentPainPoint.kt:342-360`).

- [x] (done 2026-07-13, 6d33fe8 — 75sp emoji in 80dp frame on showDots, Heading1 otherwise; swapped semantics fixed) **O8 · P3 · bug — Help/harm emoji ~half size.** Spectrum `showDots` branch
  renders `Heading1` (~32sp) (`A/.../AssessmentRenderers.kt:244-245`); iOS uses
  75pt with 80pt frame (`AssessmentSpectrum.swift:47-61`). Note the branch
  semantics are also swapped vs iOS (large-emoji belongs to `showDots==true`).

- [ ] **O9 · P3 · divergent — Reviews slide:** static placeholder reviews (should
  fetch `getReviews` like iOS `OnboardingReviews.swift:129-139`), no Play
  In-App Review prompt (iOS `requestReview()` `:71`), no edge fade, unicode 🌿
  laurels, flat star color (`A/views/newuser/sales/ReviewsSlide.kt:69-194`).

- [x] (done 2026-07-13, 6d33fe8 — TutorialController deleted along with the AllTabs trigger, settings reset row, and the popup payload/renderer) **O10 · P3 · decided — Delete the tab tutorial popups** ("Today (1/3)" etc.)
  **for ALL tabs** (today/profile/community/groups — §17-Q4). Self-contained
  Android plumbing: remove the `maybeShow` trigger (`A/views/existinguser/AllTabs.kt:68`)
  and the flows (`A/data/TutorialController.kt:26-45`); also remove the reset
  entry point (`A/.../settings/SettingsSection.kt:130`).

- [ ] **O11 · P2 · decided — Add the start-date step to onboarding** (§17-Q9).
  Port iOS `Tutorial2DatePicker` behavior (`iOS/.../Tutorial2/Tutorial2DatePicker.swift`):
  range today−29…today+14, default tomorrow; on confirm compute the Day-0 delta
  and call `adjustBreakTime(days:)` (already ported & verified); past/today
  selection back-fills sober check-ins (`logPastDaysSober` →
  `CheckInLogger.handleMultiCheckIn`) and backdates `lastSmoked`; future
  selection resets `lastSmoked`. **Placement (Thatcher): right after the
  notification-permission popup** — confirm exact spot at implementation time.
  Clear30 users only; Life/moderation users skip.

- [ ] **O12 · P2 · missing — Assessment question script depth.** Remaining
  unported renderers (image-choice, date picker, carousel, pop-up cards,
  multi-spectrum emoji, slider-with-custom) and full Slides3 branching.

## 4. Break mechanics

*(Model, `adjustBreakTime` incl. start-soon bridge + overlap truncation,
`day0StartNow`, `newClear30` mechanics, picker ranges: verified matching iOS.)*

- [x] **B1 · P0 · bug — `restartBreak` off-by-one.** (done 2026-07-13, 3a63877 — `resetFutureProgress(includeToday = true)` for restartBreak only; adjustBreakTime keeps `>`) Progress reset uses `> today`
  (`A/data/ProgramTimelineHandler.kt:72-74,361`); iOS uses `>= today`
  (`iOS/Data/Program/ProgramTimelineHandler.swift:158`) → new Day 1 can render
  already-complete. Note: `adjustBreakTime` correctly uses `> today` (matches
  iOS) — the `>=` applies to restartBreak ONLY.
- [x] **B2 · P0 · decided — `endBreak` lands users in weed-free** (mirror iOS,
  §17-Q5). Android currently inverts it to moderation
  (`A/data/ProgramTimelineHandler.kt:384-385` → `switchCore(newModeration=true)`).
  iOS net effect: `coreModeration=false`, submits `LO-Use-State=0`
  (`iOS/.../ProgramTimelineHandler.swift:223-224` + `ProgramMessageHandler.swift:368-420`).
  (done 2026-07-13, 3a63877 — `switchCore(startOn = today, newModeration = false)`)
- [ ] **B3 · P1 · bug — New Break skips the break assessment.** `NewBreakSheet` is a
  name-only AlertDialog passing `emptyList()` responses
  (`A/views/existinguser/profile/NewBreakSheet.kt:74-77`). iOS runs the full
  break assessment → `handleNewBreak` (`iOS/.../AssessmentSubmissionHandler.swift:265`,
  question set: newBreakType, breakReason, consumptionMethod, daysUsing,
  moneySpent, helpHarm, newBreakStart (date, 0–14), previousBreak, triggers,
  afterClear30, commitment — defined `iOS/.../BreakAssessmentAbstracted.swift:11,32-43`),
  submits under `"clear30"`, sets `assessmentResponseID`, fetches normative
  feedback. Android's assessment components under `A/views/newuser/assessment/`
  can be reused.
- [x] **B4 · P1 · missing — `verifyProgramSetup`** (midnight-crossing timeline
  shift at payment) — implement together with X2. (done 2026-07-13, 3a63877 —
  `AssessmentSubmissionHandler.verifyProgramSetup`, called from handlePayment;
  submission time stamped via the iOS `initialAssessmentSubmission` cache key)
- [ ] **B5 · P2 · bug — Settings `ProgramStartDatePicker` writes `program.startDate`
  directly** with no content/break shift
  (`A/views/existinguser/profile/ProgramStartDatePicker.kt:71-78`) — desyncs the
  timeline; not a port of any iOS behavior. Remove it or route through
  `adjustBreakTime`.
- [ ] **B6 · P1 · bug — Life mode toggle doesn't call `switchCore`.** Profile
  toggle flips `coreModeration` + saves locally
  (`A/views/existinguser/profile/ProfileTab.kt:324-329`); never re-fetches Life
  content. `switchCore` exists and is faithful
  (`A/data/ProgramTimelineHandler.kt:259-288`) — call it from the confirm
  handler (with a loading state, like iOS `ProfileCards.swift:317-323`).

## 5. Today tab & check-in

- [ ] **T2 · P3 · bug — Check-in slider screen background** uses the brand
  gradient (`A/views/existinguser/today/checkin/CheckInSheet.kt:139`); iOS uses
  plain `clear30Background` (`iOS/.../CheckIn.swift:66-68`). One-line. (Slider
  track/handle already match iOS.)
- [ ] **T3 · P2 · missing — Rewards engine parity.** Android is a self-described
  "faithful subset": 10/18 variable types
  (`A/data/CheckInRewardVariableGenerator.kt:15-26,31-42`; `calendarFillAnimation`
  filtered out at `:91`), flat non-animated views inlined in
  `CheckInSheet.kt:616-774`; static rewards only mined for a money amount —
  `WeedFreeTimer` (live timer) and `SmokedStats` never render
  (`CheckInSheet.kt:516-519`); no per-reward confetti/animated counters.
  iOS reference: `CheckInRewardViews.swift` (19 animated views),
  `CheckInRewardVariableContainer.swift`, `CheckInRewardStaticContainer.swift`.
- [ ] **T4 · P2 · missing — Check-in card states.** Android card = 3 static
  states + one button (`A/views/existinguser/today/checkin/CheckInDayCard.kt:57-138`);
  iOS shows per-method `CheckInStatus` rows with remove (`xmark.circle.fill`),
  "smoked again" (+), inline amount picker, timestamps, and a detail editor
  (`iOS/.../CheckInDayCard.swift:97-104,182-234,388-472`). Port the stateful card.
- [ ] **T5 · P2 · divergent — Feed cards internally scroll instead of expanding.**
  `MessageContentCard`/`GuidesFeedCard`/`RedditFeedCard` cap at 62% screen height
  with inner `verticalScroll`
  (`A/views/existinguser/today/feed/FeedContentCards.kt:91-92,132,173,218`).
  Wanted: cards take max page height, tap-to-expand instead of inner scroll,
  and inline reddit/YouTube embeds (`RedditFeedCard` is preview-only `:214-245`;
  `YouTubeFeedCard` static thumbnail `:258-289`). Depends on T7 (reddit data)
  and S8 (YouTube player).
- [ ] **T6 · P2 · missing — End-of-feed celebration.** iOS `TodayTabEndFeedView`:
  animated progress ring → fills to 100% → `ConfettiPop(num:100, radius:300)` +
  `successHeavy` haptic → "All Messages"/"Enter the Community" CTAs + "Back to
  Top" (`iOS/.../TodayFeedViews.swift:931-1069`). Android feed ends at the
  community item (`A/.../TodayTab.kt:197`); `ConfettiOverlay`
  (`A/views/components/Confetti.kt:33`) exists to reuse.
- [ ] **T7 · P1 · bug — Reddit proxy payload mismatch (fixes reddit everywhere).**
  Android sends `{"url": ...}` to `reddit_proxy`; the edge function requires
  `{subreddit, postId}` and 400s (`BE/supabase/functions/reddit_proxy/index.ts:136-141`)
  → Android silently falls back to direct reddit.com scraping (403-blocked from
  emulator/DC IPs) and never uses the `library.reddit_threads` cache (table
  verified in prod). **Fix:** extract subreddit/postId from the URL and mirror
  iOS `fetchRedditThread` (`iOS/Data/Supabase/SupabaseEdgeFunctions.swift:54-60`,
  `RedditScraper.swift:349`) in `A/data/supabase/SupabaseReddit.kt` +
  `A/data/RedditScraper.kt:41-75`. Root cause of S5's nondeterminism. Requires
  E1 locally.

## 6. Support tab & content viewers

- [ ] **S1 · P2 · divergent — Meditations: one standardized sheet.** Shared
  `MeditationPlayer` exists but is presented ad-hoc (bottom mini-player in
  library + cravings, inline swap in message detail, no-op route from hub rail —
  `A/.../library/MeditationsScreen.kt:101-222`, `cravings/CravingHub.kt:158-160`,
  `today/feed/MessageDetail.kt:191-214`, `SupportTab.kt:244`). iOS = one
  full-screen `MeditationPage` sheet everywhere (`iOS/.../MeditationPage.swift`).
  Keep cravings' separate `craving_resources` data source (matches iOS).
- [ ] **S2 · P2 · divergent — Dr Fred + Gerad (peer-support) chat composers.**
  Both use bare `OutlinedTextField` + arrow IconButton
  (`A/.../drfred/DrFredChat.kt:229-237`, `peersupport/PeerSupportChat.kt:216-224`)
  instead of the Claire/`AiChatScreen` composer (off-white input + gradient send
  disc, `A/views/components/views/AiChatScreen.kt:109-137`). They're human-backed
  `comms.*` threads — share the composer + bubble styling, not the whole screen.
- [ ] **S3 · P3 · bug — Reddit viewer close button is top-LEFT**
  (`A/views/components/RedditDialog.kt:86-98`); move the `xmark` to the trailing
  side of the top bar.
- [ ] **S4 · P1 · bug — Symptom → Claire → back skips the symptom page.** Support
  tab uses one local `route` state, no back stack
  (`A/views/existinguser/support/SupportTab.kt:88,146,165-171`). Fix with a real
  back stack (preferred; TODO.md §6's Navigation Compose item) or by
  remembering/restoring the prior route.
- [ ] **S5 · P2 · bug — Reddit viewer inconsistency.** Symptom "Real stories"
  opens raw `WebViewDialog` (`A/.../SymptomDetailScreen.kt:109-135`) while all
  other paths use `RedditDialog` (orange-bar native viewer); and `RedditDialog`
  silently falls back to WebView when the scrape fails
  (`A/views/components/RedditDialog.kt:63-66`) — currently always, per T7.
  **Fix:** T7 + route symptoms through `RedditDialog`.
- [ ] **S6 · P2 · missing — Message viewer as paged feed.** iOS
  `ProgramMessagesView` = full-screen pager, one section per content part
  (topicCard/video/message/carousel/pageInfo/meditation/instagram/reddit/youtube/
  memberPerk/clairePrompt/journal/feedEnd) with progress header + feedEnd
  celebration (`iOS/.../ProgramMessagesView.swift:13-31,92-161,276-291,463-563`).
  Android `MessageDetail.kt:126-236` = flat scroll with collapsed link rows.
  Pagination note: the Today tab already uses `VerticalPager`, so paging is not
  hard — do it.
- [ ] **S7 · P3 · bug — Remove per-row star in the messages list**
  (`A/.../library/MessagesLibraryScreen.kt:140-146`; iOS list has no star —
  favoriting is a heart inside the opened message,
  `iOS/.../ProgramMessagesView.swift:319-348`).
- [ ] **S8 · P1 · bug — YouTube player white screen.** Raw WebView embed can't
  detect YouTube's in-player embed errors (only main-frame HTTP errors handled —
  `A/views/components/VideoPlayer.kt:147-181,163-169`) → blank player, "Watch on
  YouTube" fallback never triggers. iOS uses YouTubePlayerKit with a real error
  state (`iOS/.../YouTubeViewer.swift:150-158,206-216`). **Fix:** adopt the
  `android-youtube-player` IFrame library (or JS bridge for
  `onError`/`onStateChange`) with the watch-on-YouTube fallback.
- [ ] **S9 · P2 · missing — Claire prompts "View all" → all-prompts list.**
  Currently routes to empty Claire (`A/.../SupportTab.kt:243`); no list route
  exists in `SupportRoute` (`:64-84`). iOS `AllPromptsView.swift`:
  break-filtered, stage-sectioned 2-col grid of prompt cards, each seeding
  Claire (`:59-96,200-231`).
- [ ] **S10 · P3 — Remove "Journal Prompts" + "Is Therapy For Me?"** from the
  Extras section (`A/.../SupportTab.kt:252-255` + routes/handlers
  `:75,:77,:157,:159`; the Extras label goes too since the section empties).
  Per-message journal prompts in `MessageDetail.kt:227-230` stay.
- [ ] **S11 · P3 · decided — Feedback monster colors.** **Decision (§17-Q10): the
  default/unfed monster is ORANGE (sad, nobody fed it); fed state stays
  yellow/journals.** Currently `FeedbackMonsterCard` hardcodes yellow for both
  states (`A/.../SupportTab.kt:451-470`); also fix the async-null experiment
  flash (`:94-101,285-286`). (Config-card colors already match iOS.)
- [ ] **S12 · P2 · missing — Slip-up sheet.** Android substitutes a canned Claire
  prompt (`A/.../SupportTab.kt:198-204`); iOS: `SlippedSheet` /
  `SlippedActivities` / `SlippedContent` under
  `iOS/Views/Existing User/Support/Slipped/` (also writes userWhy —
  `SlippedActivities.swift:517`). Pairs with X6.

## 7. Profile

- [ ] **P1 · P1 · bug — "Your why" never synced to DB.** Save handler is
  local-only (`A/views/existinguser/profile/ProfileTab.kt:280-284`);
  `SupabaseController.updateYourWhy` exists with zero callers
  (`A/data/supabase/SupabaseFunctions.kt:66-67`). iOS: `ProfileCards.swift:102`.
  One-line wire-up.
- [ ] **P2 · P2 · missing — Snake calendar for in-break users.** Android always
  renders the Roman calendar (`A/.../ProfileCalendar.kt`, used unconditionally at
  `ProfileTab.kt:119`); iOS branches: in-break → `ProfileSnakeCalendar(height:275)`
  (`iOS/.../Profile.swift:144`), Life → Roman (`:193`). Port
  `ProfileSnakeCalendar.swift` and branch on `program.currentBreak != null`.
- [ ] **P3 · P3 · bug — Timer bar corner radii distort on short fills.** Fill Box
  clipped independently with the full 14dp shape
  (`A/views/existinguser/profile/DopamineTimer.kt:250,278-280`) → radii shrink
  when the fill is narrow. iOS masks the whole composited bar once
  (`iOS/.../DopamineTimer.swift:422-425`). **Fix:** clip the container once;
  fill = left-aligned plain rect.
- [ ] **P4 · P3 · bug — "in X hours" badge on every health card.** Badge renders
  per gauge (`A/.../health/HealthGaugeCards.kt:153`); iOS shows it only for the
  single soonest-updating category (`iOS/.../Profile.swift:263-265`;
  `nextIncreaseCategory` = min by nextStepDate,
  `ProgramHealthProgress.swift:193-195`). Compute the earliest `nextIncrease` in
  `HealthCardsRow` and pass a `showTimeLeft` flag.
- [ ] **P5 · P3 · missing — Health cards not clickable.** No click handler at all
  (`HealthGaugeCards.kt:132-163`); iOS cards are always tappable — even at 0% —
  navigating to the health timeline detail (`iOS/.../HealthCard.swift:26`,
  `Profile.swift:380-388`). Route to `HealthTimelineSection`/detail regardless
  of percentage.
- [ ] **P6 · P2 · missing — Journal UX.** Text entry = cramped AlertDialog
  (`A/.../journal/JournalSection.kt:202-218`); video title hardcoded
  "Video journal" (`:98`). iOS: full-screen `TextEntry` editor with title field +
  share-to-community (`iOS/.../TextEntry.swift:61,70,73,113`); video entries
  named by the selected prompt (`NewVideoEntryViewModel.swift:321`). Minimum:
  full-screen text editor + nameable video entries (prompt picker optional).

## 8. Community

- [ ] **C1 · P2 · missing — Pinned-post behavior.** `is_pinned` parsed but never
  read (`A/data/model/Post.kt:22`; `CommunityTab.kt:259-272` renders all posts
  flat). Port iOS: `opened_pinned_<id>` visited tracking cached on UserInfo,
  hide opened pinned posts in Newest mode, and auto-seed the feed's tag filter
  from the current program's tag
  (`iOS/.../CommunityFeedViewModel.swift:186,406-416`;
  `CommunityFeed.swift:225,264-265,340-352`).
- [ ] **C2 · P3 · decided — Remove the prompt card at the top of the feed**
  (hardcoded random local string — `A/views/existinguser/community/CommunityTab.kt:240-243,290-320`).
  Part of §17-Q4's "remove tutorial popups, same with community and such."
- [ ] **C3 · P2 · missing — Community depth:** activity/notifications inbox
  (currently a stub — `A/.../CommunityHeader.kt:161`), edit post, owner actions
  + flagging un-stub (`CommunityTab.kt:520-562`). Per-user profile view (iOS
  `SocialUserView`) is nice-to-have.

## 9. Groups

- [ ] **G1 · P2 · divergent — Header overhaul.** Android wraps the header in a
  full gradient card (3 text lines incl. "X members · Y days clear together") +
  full-width InviteChip (`A/views/existinguser/groups/GroupsTab.kt:335-354`);
  iOS is plain text — dim group name over `Heading1` section title — with a
  small top-right "+" circle button opening a share sheet, no member-count line
  (`iOS/.../GroupAll.swift:54-102,137`). Drop the card, drop the subtitle line,
  compact invite. Section picker/tabs already match.
- [ ] **G2 · P2 · missing — Groups depth:** create/join/leave flows
  (`add_member`/`remove_member`), group calendar, notes, member detail, pings.

## 10. Notifications & FCM

- [x] **N1 · P2 · env+feature — FCM end-to-end test.** Needs
  `google-services.json` + the two plugins uncommented. Token/channel/routing
  plumbing already ported (`A/messaging/Clear30MessagingService.kt`).
  (done 2026-07-13 — `google-services.json` in place (release + debug clients
  registered in project `clear30-24f18`; keep BOTH — debug builds fail without
  a matching client), plugins enabled, device obtains a token, and a console
  test push was delivered and displayed on the emulator. Fixed two real token
  gaps found on the way: nothing fetched the token at launch (`onNewToken`
  only fires on creation) — now fetched in `Clear30Application.onCreate`; and
  nothing ever wrote `UserInfo.fcmToken` (so `create_user` pushed a null token
  and rotations never synced) — ported iOS `ContentView.updateFCM` into
  `AppRootViewModel.observeFcmToken`. Note for N2: the FCM service-account key
  exists only as a prod Supabase secret (`FIREBASE_SERVICE_ACCOUNT_JSON_B64_ENC`),
  not in either repo — local `notification_send` runs need it added to
  `BE/supabase/functions/.env`.)
- [ ] **N2 · P2 · missing — Health + pop-in notification scheduling.**
  `A/data/NotificationHandler.kt` has no `scheduleHealth`/`schedulePopIn` (only
  a pop-in *cancel* tag at `:189`); silent-push payload is stored
  (`AppState.setNotification`) but never consumed. iOS:
  `NotificationHandlerHealth.swift`, pop-in scheduling, `NotificationHandlerSilent`.
- [ ] **N3 · P3 · divergent — Content notification details:** fires at `unlockOn`
  (10:00) vs iOS smoke-time (`iOS/.../NotificationHandlerContent.swift:64-72`);
  skips messages lacking notification copy instead of falling back to
  title/subtitle (`A/data/NotificationHandler.kt:95-96`); no same-day de-dupe or
  50-cap (iOS `:44-59`).

## 11. School / pilot features (all wanted)

- [ ] **F1 · P2 · missing — School mode.** No `get_school_data` call anywhere; no
  email → `schools.school_leads` lookup. Needed at sign-up (referral code →
  school_id) AND sign-in (email recovery), then regenerate school messages into
  the feed anchored to `program.startDate`
  (`iOS/Data/Other/SchoolDataAbstracted.swift:32`; merge points
  `iOS/Data/Program/ProgramContent.swift:96,109`). Note: iOS's own sign-in
  email-recovery may be a gap — Android should implement it regardless.
  Android `A/data/model/SchoolData.kt` model exists.
- [ ] **F2 · P2 · missing — Mid-pilot assessment.** iOS
  `Views/Existing User/MidPilotAssessment/` (3 files incl. wellbeing sliders);
  no Android counterpart.
- [ ] **F3 · P2 · missing — Post-assessment flow.** iOS
  `Views/Existing User/PostAssessment/` (7 files: Intro, Achievement,
  Testimonial, CoachReferral, ViewModel); Android has the experiment keys
  (`A/data/model/ExperimentController.kt:48-50`) but nothing renders them.
  Includes submitting the `"life"` assessment (see O2) and pushing the break's
  `post_assessment_*` fields.

## 12. Local dev environment

- [x] **E1 · env — Run edge functions locally** (`supabase functions serve` from
  `BE/`) — required for Claire/AI chat and `reddit_proxy` (T7). Claire failing
  on the emulator is almost certainly this, not app code.
  *(2026-07-13: partially done — serve is running in the background with
  `--no-verify-jwt` (the local runtime rejects even the local anon key when
  verification is on; likely a JWT-secret mismatch in the local stack) and is
  now also needed for onboarding's normative feedback (O4). Remaining: wire
  `supabase functions serve --no-verify-jwt` into `android/run.sh` so it
  survives new sessions.)*
  (done 2026-07-13 — `run.sh` step 1 now starts
  `supabase functions serve --no-verify-jwt` in the background when no serve
  process is running, logging to `/tmp/clear30-functions-serve.log`; both the
  already-running and cold-start branches exercised, and a function call
  through the local gateway verified reaching the app's auth middleware)
- [x] **E2 · env — Silence Slack on local.** Slack pings come from edge functions
  (`slack_send_message`, `user_handle_new`, etc. under `BE/supabase/functions/`)
  — neuter via local function env (unset the Slack webhook secret), not a seed
  file. (verified 2026-07-13: already silent — webhook URLs live per-channel in
  `comms.slack_channels`, which is EMPTY locally, and `slack_send_message` also
  guards against cross-environment calls; nothing to neuter)
- [x] **E3 · env — Seed sleep meditations** into the local DB. Prod tables
  verified: `library.sleep_resources` (and `library.craving_resources`). Pull
  rows from prod via the Supabase MCP → new numbered seed file (pattern:
  `BE/supabase/seeds/01…37_*.sql`).
  (done 2026-07-13 — `BE/supabase/seeds/38_sleep_resources.sql`, 4 rows;
  applied to the running local DB and md5-verified identical to prod;
  read back through local PostgREST with `Accept-Profile: library`)
- [x] **E4 · env — Seed feature wishlist** the same way.
  (done 2026-07-13 — `BE/supabase/seeds/39_feature_ideas.sql`, 51
  `comms.feature_ideas` rows with `user_id` remapped to the seeded Test user
  `'1'` (prod author IDs would violate the FK to `public.users`; the UI never
  shows the author) + `setval` so new local submissions don't collide;
  content md5-verified identical to prod incl. trailing-whitespace fidelity;
  `feature_idea_votes` intentionally not seeded — it's per-user data)

## 13. Prod readiness (external — Thatcher only)

*(Decided 2026-07-13: the new app ships as an UPDATE to the old RN app's Play
listing — `applicationId` changed to `org.clear30.Clear30v1` (debug:
`org.clear30.Clear30v1.debug`). Use these package names in every console
below. Verify the old app's signing key still exists (or the listing is on
Play App Signing) — without it the update route is impossible.)*

- [ ] RevenueCat: create the Play Store app in the dashboard → `goog_` key into
  `local.properties`.
- [ ] Firebase: add Android apps `org.clear30.Clear30v1` +
  `org.clear30.Clear30v1.debug` to project `clear30-24f18` →
  `google-services.json` + uncomment plugins (unblocks N1).
- [ ] Play Console: signing keystore (must be the OLD app's key), update the
  existing `org.clear30.Clear30v1` listing, internal-testing track.
- [ ] Launcher icons + SVG vector import (polish).

## 14. TODO.md corrections (audit 2026-07-13)

Claimed TODO but actually **done**: experiments refresh + exposure logging;
FCM payload routing + per-type channels; CheckInLogger backend sync (pushes
day_info/last_smoked per check-in); video journals (system-camera, not CameraX);
URLManager / ShortcutHandler / PopupManager / TutorialController / AlertHandler /
LoadingCoordinator / Confetti all exist as real implementations.
Still accurate: assessment script stubs (O12), Helium not initialized,
SMS/Twilio fully missing, only StatsWidget ported, community depth stubs (C3).

## 15. Explicitly out of scope (per Thatcher, 2026-07-13)

Experiments setup work · ShortcutHandler depth · Welcome-back popup ·
Three-day encouragement · Claire voice mode · Counselor/B2B/NYS modes ·
Supplements · Video testimonials · Influencer mode · School leaderboard ·
Google Sign-In (phone/email OTP is enough) · SMS/Twilio.

## 16. Verified non-issues (don't "fix" these)

- User-ID model matches iOS cross-platform (`auth_id → users.id`); no client
  IDs minted.
- Core scheduling math in `schedule()`/`scheduleStartSoon()` is byte-faithful
  (10:00 anchor, `day − removePrefix`, +N-sec ordering, start-soon `+2` walk,
  −5s nudge) — the problem is only which path calls it (X3).
- Restore pipeline (derived startDate, `date@10:00+index` ordering,
  `_CLIENTNAME_`, past-progress=1.0, unknown-break-type drop) matches iOS.
- `endDate`/`endDateOverride` ±1 semantics round-trip identically.
- No data from this new app has ever reached prod (verified 2026-07-13); the
  3,116 prod `android` rows are the OLD Android app's users.
- The smoked-check-in crash could NOT be reproduced statically — flow is
  defensively written; see §18-D1 before attempting a fix.
- Prod `create_user` handles `platform` correctly — X1 is client-side only.

## 17. Decision log (Thatcher, 2026-07-13)

- **Q1 (O1):** Repro unknown; requirement = the most recent
  What-brings-you-here choice wins. Fix via flag reset in all branches.
- **Q2 (O3):** Only "Moderation" on What-brings-you-here routes to Life; do NOT
  ask moderation-vs-weed-free afterward (auto-set moderation stands).
  Android-only divergence; iOS unchanged for now.
- **Q3 (O7):** Pain-point target = iOS `AssessmentPainPoint.swift`
  (consumption-method + monthly-spend variant; the experiment shipping it is
  off, so this is what iOS users see).
- **Q4 (O10, C2):** Remove the tab tutorial popups on all tabs; also remove the
  community prompt card.
- **Q5 (B2):** endBreak lands users in weed-free (mirror iOS).
- **Q6 (T1):** Smoked-crash investigation deferred (§18-D1).
- **Q7 (X4):** Old-Android-app migration deferred (§18-D2); for now iOS-style
  detection (row + non-empty content_info) is correct — old-app users fall
  through to fresh onboarding keeping their users row/ID.
- **Q8 (A2):** Phone/email OTP is enough — no Google Sign-In.
- **Q9 (O11):** Start-date step is in scope; placement after the
  notification-permission popup.
- **Q10 (S11):** Default/unfed feedback monster = orange (sad/unfed); fed =
  yellow.

## 18. Deferred / backlog

- **D1 — Smoked-check-in crash investigation.** Deferred pending a logcat stack
  trace (`adb logcat -d | grep -A 30 "FATAL EXCEPTION"`). Documented suspects:
  (a) uncaught exception in the async persist coroutine — achievement sync +
  Supabase calls with no try/catch (`A/data/CheckInLogger.kt:105-141`);
  (b) reward generator invoked twice, mutating `program.dayInfo` during
  composition (`A/.../CheckInSheet.kt:510-512` vs `CheckInLogger.kt:56`).
  Cheap hardening (try/catch the coroutine; hoist the generator out of
  composition) is safe to fold into T3/T4 work.
- **D2 — Old-Android-app user migration (~3.1k users, empty program state).**
  Goal: the old app's users seamlessly land on the new app — ideally migrate
  the OLD app's local data and push it to the backend so the new app restores
  it. Requires investigating the old app's local storage format. Until then,
  X4's detection routes them to fresh onboarding (acceptable interim).
- **D3 — Widgets beyond StatsWidget** (Snake/Roman calendar, Health, Timer).
- **D4 — Navigation Compose migration** (full back-stack/sheets/toasts rework;
  S4 may land a scoped fix first).
- **D5 — Markdown rendering** in text components
  (`A/views/components/defaults/Text.kt:29`).
- **D6 — AchievementEngine criteria** partially guessed vs iOS
  (`A/data/AchievementEngine.kt:24`) — needs a criteria-parity pass.
