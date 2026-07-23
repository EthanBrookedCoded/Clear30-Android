# Clear30 Android — Parity & Bug Tracker

> Single source of truth for iOS→Android parity work. **Supersedes `TODO.md`.**
> Cleaned up 2026-07-21: Waves 1–8 plus the T10/T11 follow-ups are DONE — the
> full per-item history (evidence, fix notes, verification detail) lives in git
> (`git log --follow android/PARITY.md`, last full version at `d62f296`).
> This version keeps: remaining work, the decision log, verification debt,
> and the implementation gotchas still worth knowing.
> **Active work: Wave 17 = Thatcher's big change batch (2026-07-22) — 51/~54
> items landed + clean `assembleDebug` green; full per-item spec/status/manual-tests in
> `android/PLAN-2026-07-22.md` (+ `VIDEO_REENCODE_LIST.md`). Only E15 (device),
> G26 (device / HEVC re-encode), and J49/J50 (needs clear30.org assetlinks.json)
> remain from Wave 17. Also still open: Wave 15 W15, §1b verification debt, §1c
> prod-readiness externals, Wave 16 findings (§1a-viii).**

## For AI agents working from this doc

- **Read `CLAUDE.md` first** and follow it (design tokens, `Dimens`, no raw
  alphas, `Clear30Store` persistence, local-Supabase-by-default, MCP for schema
  verification).
- **Run against the LOCAL Supabase stack** (`SUPABASE_LOCAL=true`). Never point
  a dev build or test account at prod. The backend lives in the iOS repo:
  `~/Workspace/iOS/Clear30/Backend/` (run `supabase` CLI from there).
- **iOS is the behavior spec** unless an item says otherwise (intentional
  divergences are in the decision log, §5). Verify against the cited iOS source
  before implementing. Scheduling/break/restore semantics are specced in
  `~/Workspace/iOS/Clear30/Docs/MESSAGE_TIMELINE_ARCHITECTURE.md`.
- Paths: `A/` = `android/app/src/main/java/org/clear30/`,
  `iOS/` = `~/Workspace/iOS/Clear30/App/Clear30/`, `BE/` =
  `~/Workspace/iOS/Clear30/Backend/`.
- **Verify by building and exercising the flow** (CLI runbook in
  `android/README.md`); for backend-contract items, inspect the local DB rows
  (`psql postgresql://postgres:postgres@127.0.0.1:54322/postgres`).
- **Tick items with `(done <date>, <commit>)`** when they land.

Severity: **P0** = data integrity / prod blocker · **P1** = core-flow bug ·
**P2** = needed feature gap · **P3** = polish.

---

## 1. Remaining work

### 1a. Open items

- [x] (done 2026-07-21, uncommitted — scoped to what can actually FIRE in prod:
  (1) **remote assessment questions ported** — `RemoteAssessmentQuestion` model
  + `SupabaseAssessmentData.kt` fetch (`programs.remote_assessment_questions`,
  enabled only) + iOS-faithful injection in `AssessmentSlides3.addNextSlides`
  (match on question strippedPrompt / info-id raw, prepend to branch slides,
  splice-into-pending path with affirmation dedup). Prod has ONE live row:
  "Planned-Usage" after `triggers_affirmation` — prod iOS users see it today,
  Android now does too. (2) **Live normative data ported** — `library.one_offs`
  key `normative_data` → pain-point percentile; baked-in defaults remain the
  fallback. (3) **sliderWithCustom custom amount ported** (closes EXP4b) —
  "Custom amount" link swaps the slider for a focused numeric field (<1000),
  Next submits it as an input-style answer, iOS AssessmentSlider parity.
  (4) **BONUS P1 BUG FOUND+FIXED: slider answers were off by one** — the raw
  1-based slider value was recorded as the 0-based option index
  (`AssessmentQuestionView` never applied iOS's mapping despite the comment
  saying so). Every Days-Using/Money-Spent answer submitted the next-higher
  option, skewing the assessment payload, pain-point percentile, dream-outcome
  savings, and the profile weekly-spend/usage stats. NOT ported, documented:
  guardian/adolescent branch (out of scope), Slides2 old-onboarding
  (`new-onboarding` is show-100% + hardcoded), multiSpectrum renders as plain
  spectrum + carousel/pop-up info views (no live question/slide uses them).
  Compile-verified only — the local 54321 stack currently hosts ANOTHER
  project's DB, so on-device exercise is folded into the §1b fresh-signup
  pass; BE seeds 06/28 already carry the needed local rows.)
  **O12 · P2 · missing — Assessment question script depth.** Remaining
  unported renderers (image-choice, date picker, carousel, pop-up cards,
  multi-spectrum emoji, slider-with-custom) and full Slides3 branching.
  *(Was DEFERRED §17-Q17; un-deferred by Thatcher 2026-07-21.)*

- [x] (done 2026-07-21, uncommitted — all four halves, specced against iOS
  source first. (1) **ZoomableImageOverlay**: fullscreen dialog from tapping a
  carousel image (medium haptic) — pinch 1×–5× with two-finger-centroid pan,
  springs back to fit on release (iOS zoom is deliberately non-persistent),
  tap/✕ dismiss; iOS's ultraThinMaterial dim approximated with the app
  background at 0.75 alpha. (2) **Catch-up card** (iOS
  `UnreadMessagesFeedView`): `Program.getContentInfo(break)` +
  `getNonStartedMessages` ported; card inserts under the check-in page for
  today+active-break+≥3 never-started days+not dismissed
  (`hide_catch_up_card` cached bool); up to 3 missed-day rows (topic + second
  topic + Day-N pill) opening the day's lesson in MessageDetail, "+N more" →
  messages library, "Don't show again" persists. (3) **Journal feed cards**:
  free-form same-day entries surface as a feed page (iOS
  `TextJournalFeedView`, carousel when >1), message-less days get the New
  Journal card in the check-in page's stretch space, and both paths open the
  full-screen `TextEntryEditor` (create seeded/edit-in-place). (4) **Message
  card badge**: `ProgramBreak.getBadgeText` ported — heading now carries the
  school pill or the user's-own-answer assessment badge, via `FeedCardHeading`
  trailing. iOS audit findings: the message card has NO animated emoji or
  visited node on iOS either — the badge was the only missing rich part.
  Noted divergences: catch-up rows open the day's FIRST message (Android's
  viewer is per-message; iOS pushes the whole group), the day pill skips the
  progress ring (always empty here by construction — these days have
  progress 0), journal card keeps Android's single "Write entry" (no separate
  Video button) and feed-surfaces text entries only. Compile-verified;
  on-device checks folded into §1b.) **T12 · P3 · missing — Today feed
  leftovers from T10/T11:** carousel pinch-zoom overlay (iOS
  `ZoomableImageOverlay`), iOS's unread-catch-up + journal feed cards, and
  the richer iOS message-card internals.

- [x] **EXP1 · P1 · audit — Experiment parity for onboarding / life / new-break
  assessments** (Thatcher, 2026-07-21; audit done 2026-07-21). Prod
  `experiments.experiments` (verified via MCP): `assessment-short-flow`,
  `assessment-usage-duration`, `new-onboarding`, `new-tutorial`,
  `onboarding-referral`, `meta-sdk`, `message-testimonial-submission` = show
  100%; `video-testimonial-welcome` = 4 copy variants 25% each;
  `post-assessment-coach-referral`, `post-assessment-testimonial`,
  `testimonials` = hide 100%; `assessment-biological-sex` = **disabled**.
  **Amplitude Experiment still serves some legacy flags to iOS** (the MCP
  can't enumerate the deployment — analytics project 661074 shows no
  deployments — but prod data proves it: see EXP4a). Android is Supabase-only,
  so Amplitude-only keys resolve to their fallbacks on Android; for the
  audited flows the effective behavior matches anyway (symptoms suppressed by
  the short flow; interview hardcoded out per §17-Q22).
  **Plumbing verdict: MATCHES.** Both `showFeature`s: present+show → true,
  present+other-variant → hard false, absent/loading/failed → caller fallback;
  both persist assignments and never evict (a later-disabled experiment keeps
  serving the cached variant to previously-assigned users); neither awaits the
  fetch before onboarding renders (`AppRootViewModel.loadStorage` refreshes
  after state routing, mirroring iOS `LoadingCoordinator`).
  **Flow verdicts:** Life post-assessment ✅ match (incl. per-break-reason
  `*-Met` spectrums, `Used-Less` >8-days branch, LO-Use-State → Moderation-Tech
  branch, `"life"` submit). New-break ✅ match (identical question list/order,
  `"clear30"` submit; nits in EXP4). Onboarding ❌ — see EXP2.

- [x] (done 2026-07-21, uncommitted — short flow HARDCODED per §17-Q22, no
  experiment read: `AssessmentSlides3.kt` drops consumption-method, bio-sex
  gate, program-confirmation slides, previous-break, symptoms gate,
  credibility, in-assessment referral + fair-trial; `clear30Recommendation` →
  `Trigger` for everyone; dead builders deleted; progress estimate matches
  prod-iOS numbers (27). Moderation users now share the main path and route to
  Life at submission only — closes EXP3. Compiles; fresh-onboarding on-device
  pass tracked in §1b.) **EXP2 · P1 · bug — Onboarding assessment ran the LONG
  flow; prod iOS runs the SHORT flow** (`assessment-short-flow` = show 100%;
  iOS gates ~8 slides at `iOS/.../AssessmentSlides3.swift:85,122-124,160,
  196-206,255-287`). Android extras removed: `Consumption-Method`,
  `clear30ProgramConfirmation`, `Previous-Break` (+affirmation), credibility,
  **`Referral` double-ask** (sales slide is now the only ask, like prod iOS),
  fair-trial. `handleClear30` defaults consumption to PEN when unanswered on
  both platforms.

- [x] (done 2026-07-21 with EXP2 — decision §17-Q22: MIRROR iOS, no
  divergence) **EXP3 · P2 — Moderation/Life onboarding branch diverged beyond
  §17-Q2.** Moderation users now get the same slides as everyone (incl.
  `Trigger`); `choseClear30`/`LO-Use-State` auto-set at What-brings-you-here
  (identical to iOS `AssessmentSlides3.swift:437-445`); Life routing at
  submission only. §17-Q2 is moot: the short flow never reaches the modAbs
  question on either platform.

- [x] **EXP4 · P3 · polish — Small assessment deltas found by the audit:**
  (a) ~~post-assessment interview slide shown to everyone by fallback~~
  **(done 2026-07-21 on Android, §17-Q22: interview branch hardcoded OUT of
  `PostAssessmentViewModel.kt` — chain is now coach-referral(exp) →
  testimonial(exp) → Comments. iOS needs NO change — verified against prod
  data 2026-07-21: `life` submissions flipped 0% → ~85% Comments the exact
  week the `post-assessment-testimonial` hide-row was created (2026-05-11),
  which is only possible if the interview key was ALREADY resolving non-show —
  i.e. the legacy Amplitude deployment still serves `post-assessment-interview`
  off to iOS, including fresh installs. Android's hardcoded removal lands on
  the same Comments outcome.)**
  (b) ~~`Money-Spent` plain slider missing the typed custom amount~~ (done
  2026-07-21 with O12 — see O12 note).
  (c) ~~"(More breaks coming soon)" suffix always shown on new-break~~ (done
  2026-07-21 — count logic now mirrors iOS `allCases`, suffix never shows).
  (d) ~~Android's Age question adds a Terms/Privacy footer iOS doesn't have~~
  (done 2026-07-22 — subtext removed from the age question; verified iOS
  ProgramAssessmentQuestions.swift:214-219 has none); referral-logo rendering
  is moot now that the in-assessment referral is gone.
  (e) latent only-if-flags-change gaps (accepted): `new-onboarding`→hide has
  no Android Slides2; remote `afterQuestionId` questions, guardian/adolescent
  branch, and live normative-data fetch unported (baked-in
  `NormativeData.defaultData`); bio-sex/symptoms/short-flow now hardcoded on
  Android, so flipping those experiments only affects iOS.

### 1a-ii. Wave 9 — emulator smoke test (Thatcher, 2026-07-21)

*(Raw notes from Thatcher's on-device pass. STATUS 2026-07-21 evening: iOS
specs extracted for all items (full agent reports in the session transcript);
code landed + compiling for W2, W5, W6, W8, W10, W11, W12-gate, W13, W16,
W20, W21 — all pending one on-device pass. Environment note: the local stack
is swapped to Clear30 (`clear30-education` stack was stopped, volumes kept);
emulator quick-boot snapshot was corrupted → cleared, cold boot works; local
DB was found reset, so verification needs a fresh signup — no restorable
account exists locally.)*

- [x] (done 2026-07-21, uncommitted — full rebuild to the iOS calendar:
  welcome copy, card with month/selected-day header, Mon-start weekday row @
  0.25, paged month calendar (HorizontalPager, 55dp rows, StandardCalendarNode
  fills: sober→clear30 gradient, smoked→gray, other days LowOpacity+disabled),
  stretch chevron month-nav pair, full-width Smoked / Didn't-Smoke buttons on
  selection (3dp-border unselected style), and a real slide-to-confirm control
  (85% threshold, successHeavy haptic, 0.55s delay) replacing the Confirm
  button. Defaults ported: all days sober, last date pre-selected, last month
  page first. Submit now routes through logger.handleMultiCheckIn (iOS parity
  incl. lastSmoked reset) instead of per-day logCheckIns w/ reward generation.)
  **W1 · P2 — Multi check-in should use a CALENDAR like iOS.** Spec
  (iOS `MultiCheckIn.swift`, whole file): paged month calendar in a card
  (TabView pages, 55pt rows, StandardCalendarNode 30/40pt), weekday labels @
  0.25, month chevron stretch-buttons, full-width Smoked / Didn't-Smoke pair
  when a day is selected (gray vs clear30 gradient), bottom = full-width
  SlideToConfirmCheckIn slider (NOT a button; fires at 85% drag + confetti +
  0.55s). All dates default sober=true; last date auto-selected; last month
  page first. Submit = existing `handleMultiCheckIn`.
  `A/.../checkin/MultiCheckInSheet.kt`.
- [x] (done 2026-07-21, uncommitted — LazyListState now INITIALIZED at the current card (no LaunchedEffect scroll → no first-frame jump); future cards reachable by scrolling up. NB iOS actually centers via scrollTo(anchor:.center) — top-anchor is Thatcher's requested divergence.) **W2 · P3 — Health timeline shouldn't scroll to the current card** on
  open — current card should simply be at the top. `HealthTimelinePage`.
- [x] (AUDIT PASSED 2026-07-21 — Android matches iOS on every point of the
  spec: step-function percentage (currentStep.percentage, no interpolation);
  unlockDate = startDate + setbackDays + daysWithoutWeed days + minuteOffset
  min; setbackDays = numDaysSmoked(since: startDate) per category (forward
  shift, never reset); personal-best promoted on RAW startDate comparison;
  hasNew = lastVisited < currentStepDate w/ pre-visit copy driving the
  animation; steps sorted ascending at merge; 24h staleness gate; reset/
  adjust mutations identical. No changes needed.)
  **W3 · P1 — Audit health-progress CALCULATION parity vs iOS.** Spec
  (iOS ProgramHealthProgress.swift): percentage is a STEP function (current
  step's hardcoded %, no interpolation); unlockDate = startDate + setbackDays
  + step.daysWithoutWeed days + step.minuteOffset min; setbackDays =
  numDaysSmoked since startDate (forward SHIFT, never reset); personal-best
  promoted when current reaches best (compared on RAW startDate);
  hasNew = lastVisited < currentStepDate; lastVisited stamped on nav-in with
  the pre-visit copy driving the reward animation. Diff
  `A/data/model/ProgramHealthProgress*` + `HealthDataHandler` line-by-line.
- [x] (done 2026-07-21, uncommitted — the substantive gap vs the iOS row
  table: Android inlined the notification/SMS toggles as big cards; now
  compact push rows ('Notifications ›' / 'Text Messages ›', chevron trailing,
  adolescent dim preserved) opening full-screen sub-pages with the same
  toggles (iOS SettingsToggleView push). Rest already matched (Name/Emoji/
  Custom Check In/Info/Account/links). Still deliberately cut: Show Tutorial
  (§17-Q4), widgets showcase, influencer mode; the iOS
  'To receive notifications, tap here' permission-denied swap not ported.)
  **W4 · P2 — Settings page still doesn't match iOS** (post-P8
  discrepancies — re-audit row-by-row vs `SettingsView.swift`).
- [x] (done 2026-07-21, uncommitted — handleLastSmoked now ports iOS CheckInLogger.swift:140-164: anchor = smoked DAY @ current time-of-day, logged timestamp only used when past + different nearest-hour, hard-clamped ≤ now. Android had used the raw timestamp unconditionally → future anchor → negative timer.) **W5 · P1 · bug — Check-in reward WeedFreeTimer shows "-6 minutes"**
  right after checking in, while the profile timer is correct — they must
  match (likely lastSmoked anchor vs now skew in
  `CheckInRewardViews.kt` timer math).
- [x] (done 2026-07-21, uncommitted — headline SmallText+Heading2 now fillMaxWidth + TextAlign.Center, both branches.) **W6 · P3 — Check-in reward title + subtitle should both be centered**
  (title currently isn't).
- [x] (AUDIT PASSED 2026-07-21 — Android's variable generator matches the iOS
  decision tree end-to-end: sober guard, PlainDate seed, day-0/1/2 rigging
  (unstamped like iOS), didn't-smoke/smoked/general candidate lists with all
  15 live types + their gates (hours<24h, milestone≤5h, streak≥2,
  decrease>0, weekly≥3/≥2, custom≥2/≥3, personalBest≥25%<best),
  boostForSmoked weighting, previous-day-type + excluded filter
  (calendarFillAnimation is excluded on BOTH platforms), identical
  weighted-random math, and variableRewardType stamping. Only gap:
  overrideRewardType plumbing — iOS sets it nil in prod, so no effective
  difference. No changes needed.)
  **W7 · P2 — Audit check-in reward selection logic vs iOS.**
- [x] (done 2026-07-21, uncommitted — reward + check-in columns get navigationBarsPadding; Continue pill gets a cardSpacing*2 bottom spacer; Skip pill's bottom spacer doubled.) **W8 · P3 — Move the check-in reward "Continue" button UP; same for
  "Skip check in" in the check-in flow** (both sit too low).
- [x] (done 2026-07-21, uncommitted — full rebuild of CustomCheckInSetup.kt:
  list = iOS Screen A (fixed default weed pill, per-row edit + red delete w/
  confirm, gradient '+ Custom check in'); editor = iOS 2-phase form ('I want
  to...' OffWhiteInput + 6 template chips w/ autofill; slider preview whose
  two 40dp corner buttons are now REAL emoji pickers (grid dialog), editable
  side labels, the reused Groups HuePicker (sat .6/bri .9), hue-gradient
  Save). iOS id scheme ported (name-dashed + 8-char uuid). Phase-3
  try-it-out demo not ported — noted.)
  **W9 · P2 — Custom check-in editor rebuild.** Spec (iOS
  `CustomCheckInEdit.swift` + `CustomCheckInSetup.swift`): Screen A = card
  list (default weed row disabled, per-row edit slider.horizontal.3 + red
  minus delete w/ confirm, bottom '+ Custom check in'). Screen B = 3 phases:
  (1) 'I want to...' OffWhiteInput + template chips (🏃‍♂️ Run etc.);
  (2) slider preview — the TWO 40×40 buttons on it are EMOJI PICKERS
  (notCompletedEmoji 🚫 left / completedEmoji ✅ right, each opens
  EmojiPicker) + TinyTextInput labels below + ONE HuePicker rainbow slider
  (hue 0..1, sat .6 bri .9, gradient hue→hue+0.07) + Save; (3) try-it-out
  SlideToCheckIn demo + Done. Model: {id, name, incompleteOption,
  completeOption, hue}.
- [x] (done 2026-07-21, uncommitted — CheckInSheet gained onCompleted, fired only from the reward Continue path; TodayTab scrolls to page 1 in onCompleted only. Skip/close leave the feed alone, matching iOS CheckInViewModel.swift:131-146.) **W10 · P2 · bug — Skipping a check-in must NOT advance the Today feed**
  (the post-check-in scroll-to-page-1 fires on skip too —
  `TodayTab.kt` CheckInSheet onDismiss).
- [x] (done 2026-07-21, uncommitted — progress-bar column now CenterHorizontally.) **W11 · P3 — Today-tab progress-bar title should be horizontally
  centered.**
- [x] (done 2026-07-21, uncommitted — feed gate `hasCommunity && selectedDay
  == today` (iOS TodayFeedViewModel.swift:350 guards isToday), and the card
  was rebuilt full-PAGE-height per iOS DayPostCommunity: horizontal pager of
  one full Clear30Card per post ('Community Responses' gradient heading,
  title, dim body, Spacer-pinned footer w/ tap hint + comment/view stats) +
  trailing share-your-experience page; page counter below.)
  **W12 · P2 — Community preview cards only when selected day == TODAY**;
  verify against iOS; card is also too short.
- [x] (done 2026-07-21, uncommitted — both description strings White@0.5 on the gradient card.) **W13 · P3 — Profile program card text** ("Your long term support
  program") **should be white.**
- [x] (done 2026-07-21, uncommitted — timed intro ported (heading @+1s, dim
  body @+3s, auto-dismiss @+7s; pulsing-circle invention removed), library =
  Heading3 title + sleep-timer dropdown (Off/5/10/15/30/45/60 min; expiry
  bumps a new SleepTimerBus that every MeditationPlayerCore collects to
  pause — iOS .clear30SleepTimerExpired analog) + HORIZONTAL pager of
  MeditationFeedCard pages embedding the player, replacing the vertical row
  list. Applies to cravings too (shared screen, timer sleep-only).)
  **W14 · P2 — Sleep meditations page rebuild.** Spec (iOS
  `CravingResources.swift:69-277`, kind .sleep): timed intro ('Take a deep
  breath' @+1s, 'It's time to rest' @+3s, dismiss @+7s, slow spring) →
  'Your Sleep Library' Heading3 + sleep-timer Menu (Off/5/10/15/30/45/60min,
  fires pause via notification) → HORIZONTAL pager (height ≈50%) of
  MeditationFeedView cards (FeedCardHeading 'Meditation' + embedded player,
  no visited node).
- [ ] **W15 · P1 · bug — Meditation player play button sometimes disappears**
  after tapping play. *(Investigated 2026-07-21: NOT statically reproducible —
  icon mapping ✓, pressScale ✓, no conditional hiding. LIKELY FIXED by the
  W31/W33 shared-player rebuild (the per-card create/release lifecycle it
  replaced was the prime suspect): the Wave-12 on-device pass played a sleep
  meditation and the disc correctly flipped Play→Pause and back. Leave open
  until Thatcher's own pass stops reproducing it, then close.)*
- [x] (done 2026-07-21, uncommitted — search Box removed from the Support heading; NOTE iOS Support2 KEEPS search, this is an intentional divergence, log as §17-Q23.) **W16 · P3 — Remove the search button/feature from the Support tab**
  (not in iOS).
- [x] (DIAGNOSED 2026-07-21 — Thatcher's encoding hunch was right: all 8 of
  `https://m.clear30.org/testimonials/1..8.mp4` are **HEVC/H.265 (`hvc1`)**,
  55–71 MB, moov atom at END of file. iPhones hw-decode HEVC so iOS plays
  them; the Android emulator (+ many real Android devices) has no HEVC
  decoder → silent failure. The main content videos are fine —
  `videos/testimonial_intro_2.mp4` verified `avc1` H.264 + faststart. **FIX IS
  SERVER-SIDE (Thatcher): re-encode the 8 files** — `ffmpeg -i in.mp4 -c:v
  libx264 -crf 22 -c:a aac -movflags +faststart out.mp4` — and re-upload. No
  app change needed.) **W17 · P1 · bug — Some testimonial videos don't
  load** — encoding/codec issue confirmed.
- [x] (COMPLETED 2026-07-22, uncommitted — the remaining pieces landed +
  verified on-emulator: per-break **FilterListSheet** (ModalBottomSheet @ 0.4
  height, gradient-highlighted active row w/ break start-month @ 0.5, "Better
  Life Program" entry, opened from a `line.3.horizontal.decrease.circle.fill`
  header button shown only when >1 option) across all four libraries;
  **Reddit 'Symptoms' filter** → per-symptom alphabetical sections with the
  symptom gradients (banner hidden there, iOS AllRedditsView:223-233);
  **MoreContentBanner** ("More {type} {reltime}", clock.fill, opacityGray, only
  when a locked unlock date exists) atop every library; **Meditations became
  the 2-col MeditationCard grid** (gradient play disc + VisitedNode + "emoji
  name"; Reddit/YouTube already had grid cards, now on the shared skeleton);
  **Messages day pill** got the iOS progress RING (stage gradient @ 0.25
  outline + PathMeasure-trimmed full-alpha arc, 3dp) and the completed pill's
  white 0.5 outline; ResourcesScreen/AllPrompts rebuilt onto the shared
  `buildLibraryTabs` (killing a private oldest-first copy). Intentional
  deltas: iOS's normative-feedback pseudo-card at the messages-list bottom not
  wired; filter-switch fade animation skipped; Reddit brand icon still the
  glyph pending svg-import. Original partial notes below.)
  (PARTIAL 2026-07-21, uncommitted — (a) **SORTING fixed at the shared
  level**: buildLibraryTabs now buckets newest-first (sections AND items),
  matching iOS .reversed() — applies to Messages/Reddit/YouTube/Meditations
  at once; (b) **Messages library**: iOS MessageCard row ported (emoji+title
  left, Day-N pill right — gradient+checkmark when the day's feed is complete,
  outlined+arrow otherwise) + favorites-heart mode (header heart → Favorites
  list, xmark exits). REMAINING: per-break filter SHEET (Android keeps the
  pill tab row — functionally equivalent), MoreContentBanner, progress-ring
  pill outline (binary fill/outline for now), the media libraries' 2-col
  RedditCard/YouTubeCard/MeditationCard grids, and Reddit's 'Symptoms'
  filter option.)
  **W18 · P2 — Library lists rebuild.** Spec (iOS `AllMessagesView.swift`
  + AllReddits/AllYouTubes/AllMeditations): shared skeleton = back +
  Heading1 + filter button (line.3...circle.fill, only if >1 option) +
  favorites heart (messages only); MoreContentBanner up top; sections =
  program STAGES via ProgramMessageSectionCard, sections AND items
  newest-first (.reversed()); filter = per-BREAK FilterListSheet (detent 0.4,
  active row gradient-highlighted, + 'Better Life Program' entry; Reddit adds
  a 'Symptoms' option → per-symptom alphabetical sections); media libraries
  are 2-col grids (RedditCard/YouTubeCard/MeditationCard per Cards.swift);
  messages = MessageCard w/ day pill + progress-ring outline + normative
  feedback pseudo-card.
- [x] (done 2026-07-21, uncommitted — PostCard now branches: TEXT posts render
  the same content FLAT (no card) with a SmallText 3-line title and a 1dp
  text@0.1 divider below; VIDEO posts keep the Clear30Card. Content order
  (author line, tag pills, dim body, stats+reaction footer) already matched
  iOS.) **W19 · P2 — Community feed flat rows.** Spec (iOS `FeedCardText.swift`
  + CommunityFeed.swift:449-521): TEXT posts = flat rows (no card): author
  line 'emoji name · reltime' (TinyText .75/.25) → title SmallText medium
  lineLimit 3 → inline tag pills (gradient@0.25 bg) → body SmallText .75
  lineLimit 3 → footer (bubble.fill + chart.bar.fill counts @0.25 left,
  reaction pills right, '+' chip if <3 emojis); separated by 1pt
  text@0.1 divider with cardSpacing*1.5 above+below. VIDEO posts KEEP the
  card (FeedCardMedia). No pinned chrome. 'Other suggested posts' separator
  row.
- [x] (done 2026-07-21, uncommitted — Done is now a full-width gradient-button Row with SmallText "Done" + checkmark sfSymbol, ports iOS TextIconButton in AllTagView.swift:60-62.) **W20 · P3 — Community tag-selection "Done" button is wrong** (unknown
  origin — likely a Material default); match iOS.
- [x] (done 2026-07-21, uncommitted — ROOT CAUSE: AllTabs wrapped tab content in Crossfade(280ms) which rendered BOTH tabs stacked mid-transition (the overlapping cards) and composited fading alpha over blur shadows (the corruption). Now a plain when-switch. Also removed the Android-only scrollStackItem deck tilt/overlap from the feed pager and disabled the card glow (glow=null) per "basic and good".) **W21 · P1 — Today feed rendering breaks when switching tabs: shadows
  get corrupted and cards OVERLAP.** Decision: do NOT chase the iOS glow —
  keep shadows basic and correct. Do this FIRST (likely explains other
  visual complaints).

### 1a-iii. Wave 10 — Thatcher's follow-up list (2026-07-21)

*(All coded + compiling 2026-07-21, uncommitted; pending one on-device pass.)*

- [x] (done — `AssessmentSpectrum` dropped the Material3 `Slider` +
  solid-green `clear30SliderColors` for a custom `SpectrumSlider`: full-width
  clear30-gradient capsule track w/ green glow, per-step notches
  (black@0.15, iOS-source alpha), the same white 27dp thumb as
  `GradientSlider`, tap/drag + snap-on-release + mediumImpact per step.)
  **W22 · P2 — Help/harm slider must look like the other assessment
  sliders.** Spec: iOS `AssessmentSpectrum.swift` (SpectrumBackground capsule
  gradient + notches + white RoundedRectangle thumb).
- [x] (done — `triggersAffirmationSlide` now takes the VM and builds
  `affirmationCards` from the chosen triggers (emoji = option's first char,
  title = rest, subtitle = that trigger's affirmation body, iOS
  AssessmentSlides3.swift triggersAffirmationSlide), and `customViewFor` maps
  `triggersAffirmation → AffirmationCardsView` like goalsAffirmation — the
  Android port had dropped the custom-view wiring, leaving the slide bare.)
  **W23 · P1 — Onboarding "Clear30 was made for you." slide shows nothing.**
- [x] (done — ROOT CAUSE of the ~5× repeat: the remote-question re-append
  path (`addNextSlides`, slidesToAdd.isEmpty branch) re-queued the pending
  list — which already contained the previously-injected remote question —
  then prepended the remote questions again; each back/forward round-trip
  added one more copy. Now the re-appended pending slides are filtered
  against the remote questions' strippedPrompts (the affirmation was already
  filtered).) **W24 · P1 — "What do you plan to use Clear30 for" asked ~5
  times after navigating back/forward in onboarding.**
- [x] (done — new `BreakStartCountdown` in DopamineTimer.kt (iOS
  `StartTimer` + Profile.day0StartNow): on Day 0 of an upcoming break
  (iOS displayMode condition: `currentBreakNotStartSoon`, !inCoreProgram,
  currentBreakDay <= 0) the Profile weed-free streak card is replaced by a
  countdown to Day 1 (start of startDate+1) w/ "<name> countdown" header,
  "Your break starts today/tomorrow/on <date>" label, and a "Start Break
  Now" pill → iOS "Start now?" yes/no alert → `day0StartNow` (appScope) +
  `day_0_start_now` log event (added to Logger). Countdown-zero re-derives
  and swaps back. NOT ported: iOS's full `programBreakStartSoonLayout` page
  swap (hides progress/program sections on Day 0) — Android keeps the rest
  of the profile; also iOS's other day-0 special cases (check-in reward
  rigging, pop-in copy, health-notification suppression, community-tag hide)
  are separate items if wanted.) **W25 · P1 — Day-0 weed-free timer should
  count down to Day 1 with a Start-now button (iOS StartTimer).**
- [x] (done — the break-info dialog (ⓘ on the program card) now offers "End
  Break" during a break (iOS sheetInfoView) → "End <name>?" / "This will end
  your break and put you in The Life Program." confirm → endBreak under
  LoadingCoordinator on appScope, errors to AlertHandler, bumps ProfileTab
  refresh via new onChanged.) **W26 · P2 — Break details (ⓘ on program tab)
  needs an End Break option like iOS.**
- [x] (done — "Write entry" button row got cardSpacing/2 top padding on top
  of the column's spacedBy(cardSpacing/2) → full cardSpacing gap under the
  title.) **W27 · P3 — Journal-prompt feed card: spacing between title and
  button.**
- [x] (done — `FeedItem.Community` now added BEFORE `FeedItem.FeedEnd`,
  matching iOS insertCommunityPosts (TodayFeedViewModel.swift:404-417 inserts
  community before feedEnd).) **W28 · P2 — Community posts should come before
  the end card in the Today feed.**
- [x] (done — `TopicProgressCard` grew a `stretch` param (iOS
  ProgramMessageTopicCard `stretch:`); the feed-end celebration passes
  stretch=false so the topic card hugs its content (title gets cardSpacing/2
  vertical padding instead of weight(1f)) while the page still centers
  card + CTAs + Back-to-Top — animation/CTA reveal behavior unchanged and
  already matched iOS TodayTabEndFeedView.) **W29 · P3 — Today feed end card
  shouldn't take max height (iOS stretch:false compact card).**
- [x] (done — SharePill + shareProgress + onShare plumbing removed from the
  month header; ChevronBtn restyled to the UpDownButton treatment (full
  opacityGray, pressScale w/ haptic, full-strength tint) + cardSpacing/2 gap
  between the pair + content descriptions.) **W30 · P3 — Expanded month
  calendar: remove Share button; restyle month-nav chevrons to match app
  buttons.**

### 1a-iv. Wave 11 — Thatcher's second follow-up list (2026-07-21)

*(All coded + compiling 2026-07-21, uncommitted; pending an on-device pass.
Numbering continues from Wave 10.)*

- [x] (done — could NOT reproduce statically: the pause icon mapping
  (`sfSymbol("pause")` → Icons.Rounded.Pause, extended icons included) and
  the disc layout are correct. The player lifecycle was rebuilt anyway for
  W33 (shared app-wide player instead of a per-composable ExoPlayer released
  on dispose), which replaces the suspect lifecycle entirely — re-test
  on-device; if it still vanishes, grab a screen record.) **W31 · P2 —
  Cravings-hub meditation player: play/pause icon disappears when playing.**
- [x] (done — sleep pager: the corner "dots" were the VisitedNode checkbox on
  each meditation card (now hidden via `showVisited = false` in the hub);
  `FeedPagerDots` added centered under the pager (iOS FeedView pageDots).)
  **W32 · P3 — Sleep meditations: remove card-corner dots, add page dots
  under the pager.**
- [x] (done — real background audio: new `MeditationAudioController`
  (app-wide shared ExoPlayer) + `MeditationPlaybackService`
  (media3 `MediaSessionService`, foreground `mediaPlayback`, lock-screen
  controls; media3-session dep + FOREGROUND_SERVICE perms + manifest service
  added). `MeditationPlayerCore` now uses the shared player (listener-only on
  dispose, no release). Sleep timer moved off the composable `LaunchedEffect`
  onto the controller (appScope) so it fires with the app backgrounded and
  pauses the shared player; swiping the app away stops audio
  (onTaskRemoved).) **W33 · P1 — Sleep timer must actually work: app closed,
  audio keeps playing, stops when the timer ends.**
- [x] (done — `MessageDetail`'s pager aligned to the Today feed's W21 fix:
  `scrollStackItem` deck tilt/overlap removed, glow forced null, header
  Crossfade → plain switch.) **W34 · P1 — Support-tab message viewer:
  corrupted shadows + overlapping cards (same W21 treatment as Today).**
- [x] (done — the favorite heart (top-right of the message viewer, page 0)
  removed along with its `FavoriteHeart` composable; the Messages-list
  favorites toggle + favorites list kept (existing favorites still
  reachable).) **W35 · P3 — Remove the heart in the top right of messages.**
- [x] (done — `VideosFeedCard` (iOS `VideosFeedView`): horizontal paged
  carousel of bare native videos (`FeedNativeVideoPlayer`, settled-page-only
  playback) + page dots; `instagramVideos` wired into both feed builders
  (Today feed + viewer) after meditation / before reddits, matching iOS
  order. The model field already existed.) **W36 · P2 — Instagram-videos
  carousel missing from feeds.**
- [x] (done — VERIFIED: both platforms already source Reddit through the
  `reddit_proxy` edge function, which serves from the backend
  `library.reddit_threads` cache — iOS does NOT read the table directly
  either. Card comment updated to say so; no data-path change needed.)
  **W37 · P3 — Reddit card should pull from the library.reddit_threads
  cache like iOS.**
- [x] (done — Claire now renders as an OVERLAY above the route it was opened
  from (SupportTab keeps the base route composed under it, overlay
  registers the winning BackHandler): back from Claire returns to the exact
  viewer page, list state intact. Route `when` un-returned to allow the
  overlay; behavior otherwise unchanged.) **W38 · P2 — Claire prompt from a
  message: back should return to the message page, not the list.**
- [x] (done — journal prompt card: title block pinned at the top,
  stretch Spacer, "Write entry" at the bottom (chevrons sit just above the
  button).) **W39 · P3 — Journal feed card: title top, Write-entry bottom.**
- [x] (done — smoked check-in no longer forces the amount picker
  (`awaitingAmount` never set): logs immediately with `amount = null` (the
  logger already accepts it; editable later from the day card). iOS only
  shows the amount list on a deliberate hold anyway.) **W40 · P2 — Smoked
  check-in: skip the amount picker.**
- [x] (done — `DayNode`: smoked = `Gray` (was red — iOS has NO red calendar
  state, MultiCheckInDayNode uses .gray), no-check-in = `LowOpacity` so the
  two stay distinguishable, matching iOS exactly.) **W41 · P2 — Today
  calendar: smoked should be gray, not red.**
- [x] (done — topic-card badge: iOS padding (h = cardSpacing,
  v = cardSpacing*0.75, was ½/⅓) and left-aligned text (was centered),
  per ProgramMessageTopicCard.) **W42 · P3 — Topic-card "Clear30 Day 0" tag:
  more padding + left-aligned text.**
- [x] (done — see W37: the fetch already comes from the cache via
  reddit_proxy with `res.title` as the no-data fallback; "Tap to see more"
  moved from the stats row to pinned bottom-center (like the guides card),
  card restructured from ExpandingFeedCard to a full-height Clear30Card so
  the pin works.) **W43 · P3 — Reddit feed card: "Tap to see more" at the
  bottom; cache-first data with title fallback.**
- [x] (done — root cause is content-side/emulator, not a wiring bug: the
  embed uses the IFrame API with a genuine youtube.com baseURL; failures are
  (a) uploader-disabled embedding (error 101/150) or (b) EMULATOR codec
  failures (error 5) which hit every video. Hardened anyway: explicit
  `origin` playerVar, load timeout 12s→20s (slow cold loads no longer
  misreported), timeout now logged. Check logcat tag `YouTubeEmbed` for the
  error code if it recurs on a real device.) **W44 · P2 — YouTube "This
  video can't play in-app" — why, and fix what's fixable.**
- [x] (done — `TodayTabUiState` holder (process-lifetime) saves the pager
  page + selected day; `rememberPagerState(initialPage = saved)` restores on
  tab return, day-change still resets to page 0.) **W45 · P2 — Switching
  tabs shouldn't reset the Today feed to the top.**
- [x] (done — community carousel "1/6" counter replaced with the shared
  `FeedPagerDots` (made internal), matching iOS FeedView pageDots.)
  **W46 · P3 — Community carousel: page dots instead of "1 / 6".**
- [x] (done — `GroupTextIconButton` now stretches (fillMaxWidth + centered
  content, mirroring iOS TextIconButton's twin Spacers) — fixes the lone
  "Invite to Group" button.) **W47 · P3 — Groups: invite button should
  stretch full-width.**
- [x] (MOSTLY DONE 2026-07-21 evening — autonomous on-emulator pass with
  DB-seeded data (Goldie added to thatcher's group via
  `groups.group_members`). VERIFIED working: members tab with 2 members +
  per-member day states ("Didn't smoke!"/"Smoked." from seeded day_info) +
  July check-in stats; full-width Invite button (solo) ↔ "Add to Inner
  Circle" (multi) swap; chat tab (activity rows + sending a message —
  DB row confirmed in groups.group_messages); settings tab (name field, hue
  picker, member list); REMOVE MEMBER via the minus icon (DB row deleted,
  then re-seeded); Leave button style + its confirm wiring (not confirmed
  through — would leave the group). NOT yet exercised: join-via-code from a
  second device, the notes/activity middle tab (a mis-tap opened a system
  page — retest by hand), member-badge taps + ping RPC (G3 debt), group
  calendar interactions. Goldie remains seeded for hands-on testing.)
  **W48 · P2 — Test ALL the groups features end-to-end.**
- [x] (done — leave button restyled to the iOS GroupSettings look: full-width
  neutral card button with red content (`GroupTextIconButton`,
  foreground = red1, figure.walk.departure), replacing the left-aligned red
  `DefaultButton` pill; member-remove icon got pressScale. The odd one-off
  styles on that page are gone.) **W49 · P3 — Groups settings: fix Leave
  button style; remove the odd button styles.**
- [x] (done — full iOS `programBreakStartSoonLayout`: on Day 0 the profile
  now shows ONLY the green "Your Break Starts X" card (X = iOS
  relativeToToday: Today/Tomorrow/"In N days"), the clear30-GREEN countdown
  card (was meditation blue; TimerBar accent param added) with Start Break
  Now, and the Journal/Previous-Breaks buttons — why card, streak timer,
  health, achievements, program card, calendar, stats and break options all
  hidden on Day 0.) **W50 · P1 — Day-0 progress tab must match iOS
  (green countdown, replace why card, hide everything else).**
- [x] (done — the messages LIST now groups same-day messages (iOS
  AllMessagesView): one card per day, the 2nd message (the assessment
  question/response one, already ordered after the core message per
  d179e6a) shows as a pill badge on the card and the viewer takes the whole
  group — `MessageDetail` now accepts `List<ProgramMessage>` (single-message
  convenience overload kept) and appends each message's content after the
  main one (iOS generateFeedItems). Catch-up + daily-topics open the full
  day group too.) **W51 · P2 — Support-tab messages: same-day sub-message
  treatment like iOS.**
- [x] (done — Restart break / Change start date (+ Start now / End) are now
  plain neutral StretchedButtons — no green gradient — matching iOS
  ProfileBreakOptions' default TextIconButtons.) **W52 · P3 — Progress tab:
  restart/change-start-date as regular white buttons.**
- [x] (VERIFIED, no change — achievements IS fully set up on Android: real
  mini-display UI (`AchievementsSection` → list/reveal/detail), engine
  (`AchievementEngine`), and Supabase reads/writes against the
  `achievements` schema. Like iOS it only RENDERS once ≥1 achievement is
  earned, and the tables are RLS-gated to authenticated users — so a fresh
  local account shows nothing until something is earned. Nothing to add to
  the to-do list.) **W53 · P3 — Is achievements set up?**
- [x] (done — money-saved card is now tappable (pressScale) → "Edit Money
  Saved" numeric dialog; saves `desired − autoCalculated` to
  `currentBreak.moneySavedAdjustment` exactly like iOS handleEditMoneySaved
  (model fields already existed — the tap/dialog was never wired).)
  **W54 · P2 — Editing money saved didn't work.**

- [x] (done — TWO root causes found, both fixed. (1) **Wrong param keys**: the
  engine's threshold lookup tried `min_days/min_total/day/value/target/count`
  but the live `achievements.definitions` rows use `{days}` (cumulative),
  `{type: money_saved, amount}` and `{type: program_day, day, program}`
  (milestone) — so EVERY evaluation returned not-earned and
  `user_achievements` stayed at 0 rows (verified in the local DB: 53 defs,
  0 earned). `isEarned` rewritten 1:1 against iOS AchievementManager's
  checkers. (2) **Empty-cache dead end**: `syncNewlyEarned` no-oped forever
  if the defs cache was empty at check-in time (app-load refresh can race
  auth). New `evaluateNow` self-primes the cache, check-ins use it, and
  opening the Profile achievements section now retroactively evaluates —
  so already-qualified achievements appear immediately. Also:
  `refreshAchievementsCache` no longer wipes locally-earned-but-unsynced
  rows, and the section displays local + server earns merged. RLS verified
  fine.) **W55 · P1 — Achievements never show (follow-up to W53).**
- [x] (done — new shared `TextIconButton` in Buttons.kt (iOS Buttons.swift
  TextIconButton): full-width CardStyle button — white card bg + soft
  shadow, optional gradient/foreground/trailing icon/subtext.
  ProfileBreakOptions now uses it with the iOS icons (Restart ↺, Change
  date 📅, End ✕, Start-now ⏲) and cardSpacing gaps — the flat
  StretchedButtons had no card bg/shadow.) **W56 · P2 — Break-option
  buttons had no background/shadow (follow-up to W52).**

### 1a-v. Wave 12 — autonomous audit + on-device pass (2026-07-21 evening)

*(Agent session while Thatcher was away: adversarial 3-agent review of ALL
uncommitted Wave 10/11 work, on-emulator smoke test of every tab (no
crashes), achievements verified end-to-end, groups exercised per W48.
Everything below compiles + installed on the emulator.)*

- [x] (done — achievements verified END-TO-END on device: 7 achievements
  (first_day, three_days, day_one_done, first_weekend_warrior, coffee_break,
  lunch_special, coffee_fund) awarded for the day-3 test account, synced to
  `achievements.user_achievements`, and rendering in the Profile
  mini-display. Required one BACKEND fix: the local DB was missing sequence
  grants that prod has out-of-band — inserts failed with "permission denied
  for sequence user_achievements_id_seq". New migration
  `20260722013010_grant_achievements_sequence_usage.sql` (in the iOS repo's
  Backend) captures the prod grants; applied locally + recorded. Engine
  hardening added while diagnosing: unsynced rows now RETRY on every
  evaluation (were stranded forever after one failed push), duplicate-key
  responses count as synced, insert failures are logged, evaluation also
  runs once per app load (the Day-0 layout hides the profile section that
  used to be the only other trigger), and `refreshAchievementsCache` no
  longer wipes the earned cache when the server read fails/empty.)
  **W57 · P1 — Achievements end-to-end verification + backend grant fix.**
- [x] (done — review-pass fixes, most severe first:
  · P1 meditation: `prepare()` ran at COMPOSITION on the shared player, so
    merely composing a card (sleep-pager pre-compose, feed page) hijacked /
    stopped whatever was playing and could falsely mark tracks visited; now
    composition only obtains the player, the media item is set exclusively
    on the play tap, and all listener/scrubber/visited state is gated on
    the card OWNING the loaded track (MeditationPage.kt).
  · P2 TodayTab: `feedItems` remember was missing the `selectedDay` key —
    two message-less days produce equal `messages`, leaking today-only
    Community/CatchUp pages onto other days.
  · P2 TodayTab: scroll restore raced the async community fetch — the
    restored index could land on FeedEnd and falsely write progress=1.0;
    community posts now persist in the same process-lifetime holder, which
    also drops state saved on a previous calendar day (overnight process).
  · P2 check-in sheet: the commit `fired` flag never reset after tap-to-undo
    → the sheet deadlocked (no later commit could fire); reset when a
    result is removed.
  · P2 custom check-in editor: `autofill()` ran during composition, so a
    cleared label refilled itself under the cursor; now fills on activity
    focus-exit (+ debounced fallback), iOS behavior.
  · P2 CheckInLogger.handleLastSmoked: used the raw logged timestamp (whose
    DAY is the logging day) instead of iOS's smoked-day-at-timestamp-time
    (`withTimeFrom`) — a backdated smoked catch-up collapsed days of clear
    time to hours on the next recompute.
  · P2 Claire overlay: consumed no input in its dead zones (gutters/header)
    so taps fell through to the hidden screen beneath; now consumes. Also:
    deep-linking Claire while Claire is open replaces instead of stacking,
    and a new prompt re-keys the chat.
  · P2 messages library: system back skipped the rev bump (stale Favorites
    after unfavoriting in the viewer); tab selection no longer resets to
    default on every viewer close (name-keyed).
  · P3 batch: journal feed card ordered before catch-up (iOS index
    min(1,·)); latent assessment bug where an affirmation-only completion
    truncated the whole pending queue (now splices like iOS
    insertAffirmationSlide); journal-entry card body capped at 12 lines so
    "Tap to see more" can't clip; pinch-zoom pan now scales with zoom;
    MultiCheckInSheet scrolls on short phones; TextIconButton icon 12dp +
    Dimens-based subtext spacing (iOS sizes); OptionPill `dimmed` actually
    dims; appScope launches in break actions wrapped (an exception crashed
    the app + stuck `busy`); MessageDetail kdoc updated for the removed
    heart (W35 is a deliberate divergence).
  Known-accepted leftovers from the review (not bugs today): remote-question
  dedup matches on strippedPrompt (only matters if the backend ever shadows
  a built-in id); slider haptic can double-fire within one frame;
  feed-end "N unread messages" counts the DAY's unvisited messages while
  iOS counts non-started days program-wide.)
  **W58 · P1/P2 — Adversarial review fixes across Waves 10/11.**

- [x] (done 2026-07-21 late — **W33 sleep timer VERIFIED ON-DEVICE**: played a
  sleep meditation (Body Scan), armed the 5-min timer, pressed HOME — media3
  session stayed `PLAYING` in the background (foreground service held it
  through Android 15's background-audio hardening) and playback stopped
  right at timer expiry (~5 min later, session released). Also re-verified
  W31: the play disc correctly flips Play→Pause while playing in the
  rebuilt player.) **W59 · P1 — Sleep-timer background flow on-device.**
- [x] (done — **D5 markdown rendering closed against real content** (surveyed
  the live DB): message bodies use **bold** in 411/435 rows + a few
  *italics*, zero links/headers/lists → the inline renderer gained italics;
  GUIDES use FULL markdown (31/32 have links + # headers, 26 bullets) which
  rendered as raw syntax → new `markdownBlocks`/`SmallTextMarkdownBlocks`
  (bold headers, • bullets, tappable underlined links via LinkAnnotation)
  now used by the guide card + guide sheet. ALSO: the main lesson body
  rendered with PLAIN SmallText — raw `**` in nearly every lesson — while
  iOS uses markdown-interpreting `SmallTextWithLinks`; MessageContentCard
  now renders inline markdown + links.) **W60 · P2 — Markdown parity for
  lesson/guide content (closes backlog D5).**

### 1a-vi. Wave 13 — deep iOS↔Android edge-case audit (2026-07-21 late)

*(Four line-by-line audit agents: program-timeline math, Life program,
onboarding end-to-end, and every day-0/start-soon UI surface. Slide flow,
break math, badges, notifications, reward rigging etc. verified identical in
the reports' long "verified identical" lists — the transcripts have the full
detail. Everything below compiles; not yet exercised on-device.)*

**Fixed this wave:**

- [x] **W61 · P1 — per-break stats window wrong** (`dayInfoIn`): iOS counts
  offsets 1..30 from startDate ONLY — day 0 excluded, endDateOverride
  ignored. Android used [start, end): the seeded day-0 sober flag inflated
  every post-assessment sober/checked-in count by one, and ended-early
  breaks under-counted. `getDeltaSmokingFrequency` now uses the same window
  (it also counted post-break days).
- [x] **W62 · P2 — timeline mutators** now route lastSmoked through
  `CheckInLogger.resetLastSmoked` (sober SPANS were silently lost — the
  span-based check-in reward never fired after restart/day-0-start/forward
  moves), reschedule CONTENT notifications + refresh the widget in
  `finish()` (stale pushes for moved/deleted lessons), drop the
  Android-only health-setback recompute in adjustBreakTime, surface the
  forward-move fetch failure, and prefix endBreak's error copy like iOS.
- [x] **W63 · P2 — onboarding retry could stack duplicate breaks**: `start()`
  now REPLACES program.breaks (iOS AssessmentSubmissionHandler:137) so a
  failed-submit retry is idempotent. Also: post-verify failures now retry
  the restore/submit directly instead of stranding the user on a consumed
  OTP; fresh signups log `signed_up` + set signUpReturning=false.
- [x] **W64 · P2 — start-soon/Life copy + content fixes**:
  `programDescription`/`detailSheetInfo` no longer Elvis-collapse (the
  break card showed the LIFE info sheet during the start-soon bridge; ⓘ now
  hides when the break has no sheet, like iOS); scheduleStartSoon's day-0
  override was INVERTED (now replaces the bridge's first-day bucket, drops
  when absent — iOS exact); mergeStartSoon keeps the new bucket's stage
  unconditionally; the empty-content repair path anchors at the MAIN
  break's start (was scheduling the curriculum from the prep break's day).
- [x] **W65 · P2 — library tabs**: half-open [start, end) break windows (the
  main break's Day-0 lesson double-listed in the Preparation tab, first
  Life topic leaked into the Clear30 tab) + newest-break-first tab order
  (iOS .reversed()).
- [x] **W66 · P2 — profile program card**: "Day N" badge hidden past day 30
  (dayValid) + the iOS "Jul 22nd to Aug 20th" date-range badge; Life
  post-assessment card now REPLACES the program card in the section (was
  double-shown at page top); switchCore moved to appScope (a tab switch
  mid-switch left backend flipped / local not).
- [x] **W67 · P2 — Today community carousel scoped like iOS**: journal-prompt
  title search (min 2 comments, 2 months) with the "Day N" + program-tag
  fallback (both RPC helpers ported; the existing get_posts_by_titles
  helper also decoded the wrong response shape — every prompt search
  silently returned empty). Post-assessment popup card gated on
  daysSinceAppOpen > 0 (iOS PopUps).
- [x] **W68 · P3 — assessment payload/flow alignment**: dream-outcome now
  auto-writes the Start-Date response (= tomorrow) like iOS so submitted
  rows carry the same keys; the WBYH affirmation slides' id raw is now
  `goals_affirmation` (was Android-invented `plan_path` — analytics + remote
  question matching now line up; view dispatch discriminates on
  affirmationCards); system back steps back a slide (was exiting the app);
  abandoned-onboarding reminders re-anchor on permission grant + after a
  successful submit (iOS's three scheduling points); referral-code group
  join is finally consumed (AllTabs fallback — was set and never read);
  "breakdown" deep link route opens the pending post-assessment.

**Still open from the audit (not yet implemented):**

- [x] (done 2026-07-22, uncommitted — full iOS decision ladder ported to
  TodayTab (`LaunchedEffect(selectedDay)`): multi-sheet first (≥2 missed,
  unchanged), then forced-YESTERDAY (gated `program.getDay(now()) >= 1`,
  iOS CheckInViewModel:53-65), then forced non-today unlogged selected day
  (:68-79, unguarded like iOS), then first-load auto-present for an unlogged
  today — the today-scoped presents guarded once per calendar day via
  `TodayTabUiState.autoCheckInShownOn`. CheckInSheet now takes the day
  (`checkInSheetFor: PlainDate?` replaced the bool) — see W73a. VERIFIED
  on-emulator: fresh app load auto-presented "Check in for Today".)
  **W69 · P2 — forced check-in flows** (iOS CheckInViewModel:52-93): on
  load iOS forces an unlogged YESTERDAY's check-in (gated program day ≥ 1)
  and auto-presents the check-in on first load of an unlogged day. Android
  only auto-opens the multi sheet at ≥2 missed days. Port with the day-0
  gate + a once-per-day guard (TodayTabUiState).
- [x] (done 2026-07-22, uncommitted — iOS TagModel.swift ported exactly:
  `extractDayCount` (:76-85), `getDayTag` (:179-184, nil during start-soon),
  and the full `filterTags` (:115-151 — hidden + lobby drop, day tag only
  when == current break day, program-tag → day → general → other-programs
  sort) as shared extensions in CommunityTab; the old strip-all-day-tags
  filter removed; CreatePostScreen seeds `selectedTagIds` with the day tag
  (visible + removable, iOS CreatePostView:227-230); SlippedActivities now
  reuses the shared helper; `PostTag.Tag` gained the `hidden` column.
  VERIFIED on-emulator: "Day 4" offered 2nd in the filter sheet and
  pre-attached as a removable pill on create-post. NB the local "4/21 Lobby"
  tag still lists because its DB `type` is 'general', not 'lobby' — data,
  not app.) **W70 · P3 — community day tags**: iOS auto-attaches the "Day N"
  tag to new posts and offers it in the filter (hidden during start-soon);
  Android strips day tags from the filter and never attaches them on create.
  SlippedActivities already ports `getDayTag` correctly — reuse it in
  CommunityTab + CreatePostScreen.
- [x] (done 2026-07-22, uncommitted — `NotificationHandler.scheduleSlipped`:
  iOS's 10-entry copy pool verbatim, fire = now + random 90–180 min
  (NotificationHandlerSlipped.swift:96-101), gated on the check-in
  notification toggle + an active break, unique WorkManager job w/ REPLACE so
  a repeat slip swaps not stacks, CHANNEL_CHECK_IN; CheckInLogger ports the
  latest-check-in gate (iOS CheckInLogger.swift:87-95) then smoked →
  schedule / sober → cancel (:202-209). Divergence: taps deep-link to the
  Support tab (no dedicated Slipped route on Android).)
  **W71 · P3 — slipped-nudge notification** (iOS NotificationHandlerSlipped
  via CheckInLogger): scheduled after a smoked check-in, cancelled on sober.
  No Android port.
- [ ] ~~**W72 · P3 — remote pop-ups** (`program.popUps`)~~ **CLOSED — won't
  fix ("we don't care about popups", Thatcher 2026-07-21; consistent with
  §17-Q13's pop-in deferral).**
- [x] (done 2026-07-22, uncommitted — full iOS spec extracted first (key
  facts: RC entitlements are **"Core"/"Plus"** (Plus wins), appUserID =
  Supabase userID, loggingID only an attribute; iOS renders the paywall via
  Helium with RC underneath, so Android's native paywall + RC SDK covers the
  same contract; iOS has NO explicit restore call — it's a paywall-template
  button). Landed: `getUserParams` now emits the full iOS trait set incl.
  per-assessment-response `"{id}-{i}"`/`"{id}-display-{i}"` keys + QA
  (PaywallController.swift:327-385) pushed as RC subscriber attributes
  (closes W73d); launch-time trait refresh (iOS ContentView.initHelium) +
  referral-code re-push; AllTabs ported `checkSubscription` → hard-paywall
  force-show for lapsed unpaid users, `checkEntitlementChanged` →
  `handleUserPaid` (entitlement set, content notifications rescheduled) /
  `handleUserUnsubscribed` (entitlement cleared, content pushes dropped,
  toggles off, `unsubscribed` logged, re-gate) + full-screen popup Paywall
  (hard = undismissable); paywall analytics parity (`openedPaywall` w/
  placement + `currentPaywallID` stamp, `subscribed` w/ popup/product/
  restored extras, free-code path) + failed-restore alert;
  `AppState.requestPaywall(hard)` hook for future upsell/deep-link points.
  Blank-`REVENUECAT_API_KEY` no-op guard preserved everywhere (verified
  quiet on-emulator). Deliberately not ported: Helium/Stripe/Shopify/
  Superwall + one-time-offer downsell (§6), StoreKit messages (Play handles),
  the "you're upgraded" OpeningAnimation popup. RC purchases SDK 8.10.5 was
  already a dependency.) **W75 · P1 — RevenueCat + paywall logic, COMPLETE**
  (Thatcher 2026-07-21): port the full iOS RC/paywall behavior — offerings
  fetch, paywall presentation in onboarding (+ any re-present points),
  purchase + restore flows, entitlement→isPaid gating, subscriber attributes
  (incl. the assessment-trait attributes from W73), and graceful no-op while
  `REVENUECAT_API_KEY` is blank (no Play app yet, §1c). The
  Helium→Stripe→Shopify chain stays out of scope (§6).
- [x] (done 2026-07-22, uncommitted — all four: (a) CheckInSheet logs the
  SELECTED day at current time-of-day (new `selectedDay` param; reward +
  title keyed off it — "Today"/"Yesterday"/weekday like iOS `dayOfWeek()`);
  (b) post-verify loading is now an iOS `AccountSetupView` port (new SETUP
  step in AllSignUp: rotating 4-segment gradient ring around a person glyph,
  segments pop w/ light haptics, "Compiling your cannabis snapshot..." /
  "Setting up your account..." / "Welcome back!" titles, school-vs-AI callout
  card, Done button for new users, returning users auto-forward, back
  swallowed mid-flight); (c) `where_you_going` extra title/body removed —
  only the centered custom view renders (iOS AssessmentSlides3.swift:833-853);
  (d) RC assessment-trait subscriber attributes — closed with W75.)
  **W73 · P3 — misc small deltas**: check-in sheet logs `now()` even when
  a past day is selected (iOS logs the selected day); Android's post-verify
  loading screen is a bare spinner vs iOS AccountSetupView; `where_you_going`
  has an extra title; RC paywall subscriber attributes missing the
  assessment-response traits (PaywallController TODO).
- [x] (decided 2026-07-22 per the standing "iOS is the behavior spec" rule —
  **MIRROR the iOS quirk**, logged as §17-Q23; both dream-outcome parse sites
  now use `take(2)` like iOS `prefix(2)` with a comment citing the decision.
  Flip both if Thatcher prefers the correct math — iOS would need the same
  change.) **Decision needed (Q23?): custom weekly-spend ≥$100 dream-outcome
  math** — iOS truncates to the first 2 digits ("100" → $10 → +$40/mo, a
  quirk); Android uses the full amount (+$400/mo, arguably correct). Pick one.

- [x] (done 2026-07-21 late — **D2 old-Android-app restore implemented** as
  `OldAppMigrationHandler`, wired after a successful onboarding submit.
  Findings that shaped it: prod has 3,235 old `android` rows, and **1,654
  carry real `day_info` check-in history in the SAME positional wire format
  this app reads** (verified against prod; content_info/breaks/last_smoked
  were never synced — the old RN app kept those AsyncStorage-only). The
  migration (a) merges the server day_info into the freshly-onboarded
  program (fresh days win on collision) restoring streaks/calendar/sober
  counts + re-evaluating achievements, and (b) reads the old app's
  AsyncStorage SQLite (`databases/RKStorage` · `catalystLocalStorage`, still
  on disk since the new app ships as an update to the same applicationId):
  `last_smoked` (most-recent-wins vs the onboarding estimate) and
  `journal_entries` (appended). Program/break state is NOT migrated — the
  old app's local program can't map to a server-backed break, so users
  still onboard fresh (§17-Q7 routing unchanged). Once-only via a cached
  flag; best-effort throughout. NEEDS an on-device pass: fresh signup with
  a seeded old-style row + a fabricated RKStorage file — fold into the §1b
  fresh-signup item.) **W74 · P2 — Old-app data restore (closes the
  recoverable half of backlog D2).**

- [x] (done 2026-07-21, uncommitted) **W22 · P3 — Achievements list tweaks**
  (Thatcher, 2026-07-21): the name chip on earned+opened cards is now the
  RARITY gradient at 0.25 opacity (was neutral gray), and non-earned
  LEGENDARY achievements are hidden from the list (section disappears when
  empty; no "X of Y" count for Legendary so the total can't leak).

### 1a-vii. Wave 15 — Thatcher's follow-up list (2026-07-22)

- [x] (done 2026-07-22, uncommitted — root cause: the `List<ProgramMessage>.unlocked`
  extension re-sorts ascending (`.sorted` = compareBy(isSchoolMessage, unlockOn)),
  so within-section items were oldest-first despite `buildLibraryTabs`' newest-first
  grouping. `MessagesLibraryScreen` now reverses the DAY-GROUP order only (iOS
  AllMessagesView.swift:148 `(0..<count).reversed()`), keeping each day's
  core-message-first order so the assessment message stays the badge. VERIFIED
  on-emulator: Physical Withdrawal section renders Day 4→3→2→1.)
  **W76 · P3 — Messages library: reverse message order WITHIN sections.**
- [x] (done 2026-07-22, uncommitted — removed the header heart + `favoritesMode`
  state + the entire favorites branch from `MessagesLibraryScreen`; header is now
  just back-chevron + "Messages" (+ the >1-option filter button). Follows W35.
  VERIFIED on-emulator: no heart in the header.)
  **W77 · P3 — Remove favorites from the Messages library.**
- [x] (done 2026-07-22, uncommitted — `DailyTopicsSection` now uses the iOS
  `recentUnlockedMessageGroups(limit:3)` shape (ProgramContent.swift:134-147): the
  3 most-recent fully-unlocked DAY groups, newest-first, each day's second message
  folded into the primary card as a badge pill (iOS MessageCard badge). Opening
  passes the primary; SupportTab's OpenMessage route already rebuilds the whole
  day group from it. VERIFIED on-emulator: "Finding Your Why" shows the
  "Motivation as Your Why" badge, "Riding Out Cravings" shows its sub-badge.)
  **W78 · P2 — Daily Topics section must group assessment messages with
  core ones.**

### 1a-viii. Wave 16 — full iOS↔Android edge-case audit (agent, 2026-07-22 night)

*(8 parallel domain-auditor agents diffed the whole app line-by-line — data
models/serialization, check-in/rewards/timers, community/groups, notifications,
Supabase RPC contracts, paywall/RevenueCat/routing, support hub/library, plus a
405-file coverage/gap scan. Each agent read PARITY §4/§5/§6 first to skip
intentional divergences. Full findings: session `scratchpad/audit/*.md`.)*

**FIXED this wave (all compile; app relaunches clean, no regressions on the
day_info decode / all-tabs smoke test):**

- [x] **W79 · P1 — `PopInType` broke `day_info` cross-platform (X1 blocker).**
  Swift `Codable` writes the enum externally-tagged (`{"day0":{}}`); the backend
  `day_info` column (on `public.users`) stores exactly that (verified in the
  local DB — user rows carry `{"day0":{}}`/`{"day1":{}}`/`{"day2":{}}`). Android's
  kotlinx sealed class emitted/expected `{"type":"day0"}`, and the per-day decode
  in `DayInfoArraySerializer` isn't wrapped — so ONE iOS-written popInType threw
  and dropped the WHOLE day_info map → an iOS user restoring on Android loses all
  check-in history. **Bidirectional**: Android writes `PopInType.RestartedBreak`
  (`ProgramTimelineHandler.kt:404`), so an Android→iOS restore broke too. Fix:
  new `PopInTypeSerializer` — always ENCODES the Swift external-tag form, DECODES
  both that and the legacy kotlinx `{"type":…}` form (so on-device history from
  earlier builds still loads). `A/data/model/PopInType.kt`.
- [x] **W80 · P1 — Group member stats counted the wrong days.**
  iOS `Clear30GroupMember` filters `dayInfo` by `joinDate` (`filteredDayInfo`) and
  counts `daysCheckedIn` as `sober != nil`; Android counted the member's ENTIRE
  dayInfo and used `dayInfo.size` (so pre-join days + symptom-only days inflated
  every total). Feeds the group-card totals and the `daysSoberCount`-ordered
  leaderboard. Fixed to mirror iOS (Clear30Group.swift:120-138). ISO date-string
  keys sort chronologically so no parsing needed. `A/data/model/Clear30Group.kt`.
- [x] **W81 · P1 — DB experiments never loaded on Android.**
  `getExperiments()` called `get_user_experiments` on the PUBLIC schema, no params,
  decoding `flag_key`/`experiment_key`. Real fn: `experiments.get_user_experiments(
  p_user_id text) RETURNS (experiment_id, variant, payload)` (verified in DB; iOS
  `ExperimentControllerAbstracted.swift:99-103`). Three defects → the RPC always
  threw → every DB-driven experiment silently fell back (e.g. the `feedback-method`
  card). Fixed schema + `p_user_id` + fields, plus iOS's "only log exposure on a
  NEW variant" guard. `A/data/supabase/SupabaseExperiments.kt`. *(Assessment
  branches stay hardcoded per Q22 — unaffected.)*
- [x] **W82 · P2 — Notification-tap deep links were silently dropped.**
  `MainActivity.handleDeepLink` read only `intent.data`, but the local worker +
  FCM service deliver the route via `putExtra("deep_link", …)` — never read. So
  W71's slipped-nudge (and any FCM/content tap) didn't navigate. Fixed to honor
  both. `A/MainActivity.kt`.
- [x] **W83 · P2 — Peer-support read-marker hit the wrong schema.**
  `markPeerMessagesRead` called `mark_peer_messages_read` with no schema (→ public);
  the fn is `comms.mark_peer_messages_read` (iOS `.schema("comms")`). Best-effort/
  swallowed, so Gerad's unread badge never cleared server-side. Fixed.
  `A/data/supabase/SupabasePeerSupport.kt`.
- [x] **W84 · P2 — Android→iOS group invites never joined.**
  Android shared `clear30.org/group/<id>` (path form); iOS joins ONLY off the
  `group_id` query param, so iOS recipients silently didn't join. Now emits iOS's
  exact URL `clear30.org/join-a-group/?group_id=<id>&name=<name>` (Clear30Group
  .swift:267); Android's parser accepts both. `A/views/.../groups/GroupsTab.kt`.
- [x] **W85 · P3 — RC `rc_entitlement` trait used `.name` ("PLUS") not iOS
  rawValue ("Plus")** → RevenueCat targeting rules keyed on iOS casing wouldn't
  match. Added `EntitlementType.rawValue`; `A/data/PaywallController.kt:284`.

**OPEN — diagnosed, NOT changed (need Thatcher's call or a device test the
agent couldn't run tonight). Ranked:**

- [ ] **W86 · P1 — Offline paywall bypass (fresh W75 code, PROD-ONLY).**
  `PaywallController.checkEntitlementChanged` has no network guard; offline with an
  RC key set, `activeEntitlement` throws → catch returns `DEFAULT`(=PLUS) for a
  never-paid user → grants PLUS + dismisses the hard wall + persists it (airplane
  mode = clean bypass; self-corrects online). iOS returns `(false,nil)` when
  offline (PaywallController.swift:165-169). No-ops with the blank dev key, so
  untested here — recommend adding the same offline guard. `A/data/PaywallController.kt`.
- [ ] **W87 · P2 — Sober spans not banked on the NORMAL check-in path.**
  iOS `handleLastSmoked` routes through `resetLastSmoked` (banks a `DateSpan` into
  `lastSmokedSpans`, the `personalBest` reward's only feed); Android sets
  `program.lastSmoked` directly (`CheckInLogger.kt:160`), so `personalBest` never
  fires from ordinary check-ins and no personal-best history accrues. W62 fixed the
  timeline-mutator paths but not this one. **Fix needs care**: iOS gates the reset
  on `originalLatestSmokedCheckIn != latestSmokedCheckIn`; without that guard,
  routing through `resetLastSmoked` would bank bogus intra-day spans on every sober
  check-in (worse than today). Thread the pre-mutation latest-smoked through the
  caller, then route + guard. Same gap in the profile timer edit (`DopamineTimer.kt:139`).
- [ ] **W88 · P2 — Onboarding paywall never silently auto-restores.** iOS
  `checkPaidInitial` auto-closes the onboarding wall for a user with an existing
  entitlement; Android only restores on a manual tap, so a reinstalling subscriber
  is stuck at the hard onboarding wall. `A/views/newuser/payment/Paywall.kt:64-97`.
- [ ] **W89 · P2 — Support-tab surfaces aren't paywall-gated.** iOS gates Dr. Fred/
  Gerad chats, symptom-card taps, and slip "Talk" rows behind the entitlement (+dims
  + upsell); Android opens them all free. Android has the W75 primitives but these
  surfaces don't call them. *(Product decision — confirm you want them gated.)*
- [ ] **W90 · P2 — Community feed: no pagination + no reacting from the feed.**
  One `getCommunityFeed(end=20)` with no load-more and no "Other suggested posts"
  cross-program append (`CommunityTab.kt:94-113`); feed pills are inert, no "+" chip
  (`:576-599`). Both are the unfinished half of W19's spec (iOS pages infinitely;
  feed pills are interactive).
- [ ] **W91 · P2 — Check-in reward headline is hardcoded.** iOS
  `CheckInRewardTextGenerator` (7 scenarios, name/date substitution, seeded per-day)
  wasn't ported — `CheckInSheet.kt:583-591` shows 2 fixed strings for every check-in
  (the sober one is literally iOS's "smoked yesterday, clean today" copy shown for
  ALL sober days). All personalization lost.
- [ ] **W92 · P2 — Notification depth gaps.** (a) check-in reminder is one hardcoded
  string on a 24h worker — iOS fetches remote personalized copy, schedules 3 at day
  offsets + a 2h trial nudge, reschedules per check-in; (b) content/check-in/health
  local notifs carry no deep-link/category, so even post-W82 a tap won't scroll to
  the lesson day / open the check-in sheet / open the health category; (c)
  abandoned-onboarding fires hardcoded 1h/24h/72h copy vs iOS remote copy at DAY
  offsets. `A/data/NotificationHandler.kt`, `NotificationPostWorker.kt`.
- [ ] **W93 · P2 — Remote "Extras" support-items section not ported.** iOS renders
  remote-configured `GradientActionButton`s (existing-sheet / direct-URL / generic
  dispatch); no Android `RemoteSupportItem` model/fetch/section.
- [ ] **W94 · P2 — Post-onboarding Tutorial/Tutorial2 walkthrough (18 iOS files)
  entirely cut.** iOS shows it to every new user (`AllTabs.swift:610`); Android only
  extracted the start-date step. §17-Q4 only covers *tab* tutorial popups, so this
  isn't clearly documented as out-of-scope. *(Confirm intended-out or port it.)*
- [ ] **W95 · P3 batch — smaller deltas:** F3/F4/F6/F7 paywall analytics/trait
  drift (placement hardcoded; restore omits product `.type`; signIn attrs pushed
  pre-alias; expiry guard keys off `currentEntitlementType`); E-P3 achievement sync
  `insert`→`upsert`+`is_visited`; symptom-carousel Insomnia bias + ignores
  `SymptomInfo.selected`; human-support rows drop unread badge/preview; hub rails
  "Coming soon" even with ≥5 items; meditation scrubber shown pre-first-play;
  craving-game analytics events unemitted; forced yesterday/past check-ins
  skippable (iOS mandatory); post-reward "Today's Focus" screen + auto-advance bar
  not ported; catch-up capped at 30 days; SocialUserView (tap post author = no-op,
  `PostDetail.kt:204` TODO); Zoom "Support Group" card; create-post tag picker uses
  `filterTags` not `postingTags`; deep-link group-join lacks the "leave current
  group" alert; `ClaireMessage` history loses real timestamp. Details per domain in
  `scratchpad/audit/{A..H}-*.md`.

### 1a-x. Wave 17 — UI standardization pass (Thatcher, 2026-07-22)

*(Thatcher: "standardize and clean up" — sheets/popups, buttons, padding,
scroll-clipped card shadows. 4 parallel audit agents mapped the app, then 4
parallel migration agents on disjoint file sets; 57 files touched.)*

- [x] **W79 · P2 — One standard set of presentation containers.** New
  `views/components/defaults/Popups.kt`: `Clear30Sheet` (bottom sheet — brand
  background, `Dimens.sheetCornerRadius` = 20dp per iOS `presentationCornerRadius(20)`,
  brand drag handle, standard content padding, `canDismiss` = iOS
  `interactiveDismissDisabled`), `Clear30FullScreenCover` (iOS `.fullScreenCover`),
  `Clear30CardDialog` (centered card), `Clear30Alert` (brand card + Lexend +
  `pressScale` actions). Migrated ALL 10 `ModalBottomSheet`, ~20 full-screen
  `Dialog` covers, 4 ad-hoc card dialogs (GameSheet/UserEmojiPicker/SendNotePopup/
  break-date picker), and all 19 Material `AlertDialog`s. Zero
  `ModalBottomSheet`/`AlertDialog`/raw `window.Dialog` left outside Popups.kt
  (the one exception: `ZoomableImageOverlay`'s translucent viewer, deliberate).
  Fixed en route: the Community "Filter by Tag" sheet was the only one missing
  `containerColor`, so it rendered the default M3 surface instead of the brand
  background. VERIFIED on-emulator: Your Why alert + tag-filter sheet.
- [x] **W80 · P3 — No stock Material buttons.** Audit found the app already had
  a full custom system (`DefaultButton`/`StretchedButton`/`IconButton`/
  `TextIconButton`/`GradientActionButton` + `pressScale`) and ZERO stock
  `Button`/`OutlinedButton`/`FAB` anywhere. Remaining Material usage was ~35
  `TextButton`s + 1 `IconButton`, all dialog/menu actions — removed with the
  W79 alert migration. Also converted 5 button-like `.clickable` boxes to
  `pressScale` so they get the app's press-shrink + haptic (calendar close/
  month-nav, community circle-icon + add-tag, check-in clear chip).
- [x] **W81 · P2 — Padding standardization.** Root cause of the "ton of top
  padding" on the cravings + health-timeline screens: they called
  `statusBarsPadding()` while rendering INSIDE AllTabs' Scaffold content, which
  is already inset below the status bar — double top inset. Removed from
  `HealthTimelinePage`, `CravingHub` (+ its game/breathing sub-routes),
  `SymptomDetailScreen`'s tip overlay, `PreviousBreaksSection`, `JournalSection`,
  `ProfileSettingsOverlay`, `TextEntryEditor`. New `Dimens.chipHorizontalPadding`
  /`chipVerticalPadding` (10/5) replaces the ~20 hard-coded badge-chip insets.
  VERIFIED on-emulator: both screens' headings now sit tight under the status bar.
- [x] **W82 · P2 — Card shadows no longer clipped in scroll containers.** New
  `Modifier.scrollShadowBleed()` in CardStyle.kt: the Compose equivalent of iOS's
  `+scrollShadowFix inside / -scrollShadowFix outside` trick — widens a scroll
  node past its parent's inset so the ~7dp `softShadow` survives the clip; re-pad
  `Dimens.scrollShadowFix` after the scroll modifier to keep content in place.
  Applied where horizontal padding sat on an ANCESTOR of the scroll (the three
  AssessmentInfoSlide-hosted slides, NormativeFeedback, both PostAssessment
  slides, ReviewsSlide, CommunityHeader's two lists, the achievements rail).
  Lazy lists that padded via an outer modifier switched to `contentPadding`
  (CommunityTab, the three chat screens, FeatureWishlist).

- [x] **W83 · P2 — Thatcher's follow-ups to the standardization pass.**
  (a) **Sleep/craving meditation rail + Groups benefit carousel shadows** — both
  `HorizontalPager`s clipped their cards' soft shadows; fixed with
  `scrollShadowBleed()` + `contentPadding = scrollShadowFix` (same as W82).
  (b) **Page dots under the Groups carousel** — DELIBERATE DIVERGENCE (iOS
  GroupCreation has no dots). While adding them, the feed's `FeedPagerDots` was
  promoted to the shared `PagerDots` (`components/defaults/PagerDots.kt`) and all
  6 call sites moved to it.
  (c) **Community create-post description field** — was a stock Material
  `OutlinedTextField` (off-brand box + Roboto); now the new shared
  `SmallTextEditor` in Inputs.kt (borderless Lexend 17 + faint placeholder), the
  1:1 port of iOS `SmallTextEditor`. The journal body editor (which had
  hand-rolled the same thing inline) now uses it too.
  (d) **Post / Share buttons → `TextIconButton`** — iOS CreatePostView uses
  `TextIconButton(text: "Post", gradient:)` (Android had a pill `DefaultButton`)
  and TextEntry uses `TextIconButton(text: "Share to Community", imageName:)`
  (Android had `GradientActionButton`). Both now match iOS.
  (e) **7 unmapped SF symbols rendered as a black circle** (the `else ->
  Icons.Rounded.Circle` fallback in SfSymbols.kt) — found while verifying (d):
  the journal delete button was a filled dot. Mapped `trash`, `lock.fill`,
  `photo`, `play.rectangle.fill`, `arrow.up.right.square`,
  `arrow.up.left.and.arrow.down.right`, `circle.fill`. Swept every `sfSymbol(...)`
  call in the app — 54 used, all now mapped.
  VERIFIED on-emulator: sleep card shadow, Groups dots+shadow, create-post editor
  + Post button, journal Share button + trash icon + delete alert.

- [x] **W84 · P2 — Feed card shadows clipped (guides, community posts, message
  viewer).** Three separate clips, all the same root cause as W82:
  (a) `MessageDetail`'s `VerticalPager` had NO horizontal contentPadding while its
  parent Column applied the full `horizontalPadding`, so EVERY card in the
  specific-message viewer lost its side shadows — now on the TodayTab T9b pattern
  (column pads `horizontalPadding − scrollShadowFix`, header re-pads the
  remainder, pager `contentPadding` horizontal = `scrollShadowFix`).
  (b) `GuidesFeedCard`'s INNER `HorizontalPager` clips at the feed page's own
  width, cutting the guide cards' shadows independently of the parent fix —
  `scrollShadowBleed()` + `contentPadding`.
  (c) `CommunityCarousel`'s inner `HorizontalPager` — same fix (this is the
  "community posts" case; Community-TAB text posts are flat rows with no card
  per W19, and its list was already fixed in W82).
  VERIFIED on-emulator: message/guide/reddit/youtube cards all keep their soft
  shadows, with the next guide page peeking correctly.

### 1a-xi. Wave 18 — full check-in flow audit vs iOS (Thatcher, 2026-07-22)

*(3 parallel auditor agents diffed the Android check-in against the iOS source:
gating/when-shown, forced + multi catch-up, and post-check-in side effects. Two
agents independently found the same P1 data-loss bug, which is what prompted
fixing the whole ladder rather than patching pieces.)*

**Fixed (W85):**
- [x] **P1 — Re-opening a logged day WIPED that day's check-ins.** `CheckInSheet`
  took no initial values, so `OnScreenCheckIn` always started empty and
  `logCheckIns` did `copy(loggedCheckIns = checkIns)` — replacing the day. The
  reachable path (day card "+" to add a custom check-in created after the day was
  logged) forced the user to redo the weed slider and destroyed every existing
  slip timestamp/amount/method for that day, which then fed `handleLastSmoked`.
  iOS seeds `initialCheckInValues` and blocks re-answering an existing weed
  check-in (CheckIn.swift:84-88). Now ported: `SlideToCheckIn` takes
  `initialCompletion`/`initialAmount` and renders pre-committed.
- [x] **P1 — The whole post-check-in pipeline died on a tab switch.**
  `CheckInLogger.persist` ran on TodayTab's `rememberCoroutineScope`, which is
  disposed on every tab switch — cancelling the Supabase `day_info` push,
  achievement evaluation, group activity and health-notification reschedule
  mid-flight. Now on `Clear30Application.appScope`, same rule as the break
  mutations (§3). iOS uses detached completion handlers.
- [x] **P1 — Streak achievements survived a slip.** `currentSoberStreak` treated
  a SMOKED today the same as an unlogged today (stepped past it), so slipping on
  day 7/14/30 still awarded the streak — and achievements are permanent + pushed
  to the backend, so the bad award wasn't reversible. Replaced with a 1:1 port of
  iOS `calculateConsecutiveDays` (newest-first, break on first non-sober or gap).
- [x] **P1 — Forced check-ins were skippable.** iOS branches 2/3 render the
  check-in INLINE in place of the whole feed (TodayFeedView.swift:32-33) with no
  skip and no dismiss; Android showed a back-dismissible cover with a Skip pill,
  so the enforcement mechanism didn't exist. Added `CheckInSheet(blocking=)`:
  forced days get `canDismiss=false` and no Skip. Skip is now also today-only
  (iOS CheckInFullscreen.swift:343).
- [x] **P1 — Multi catch-up was skippable.** iOS presents it through
  PopupManager's bare overlay — no X, no scrim tap, no swipe. Removed the X and
  set `canDismiss=false`.
- [x] **P2 — Manual "Check In" tap bypassed the ladder.** It assigned
  `checkInSheetFor = selectedDay` directly, so with a backlog it opened a single
  day instead of the multi sheet, and with yesterday unlogged it logged today and
  left the backlog. Now routes through `runCheckInLadder(manual = true)`
  (iOS `forceShowForSelectedDay`).
- [x] **P2 — Multi catch-up capped at ~31 days + bailed on empty `dayInfo`.** Both
  were Android-only. Days older than the window could never be caught up (staying
  `sober == null` forever and corrupting numDaysSober / numDaysCheckedIn / the
  health-setback math), and a never-checked-in user (fresh install + restore) got
  no catch-up at all since the ≥2 threshold reads this list. Now scans the whole
  program like iOS `datesWithoutCheckIn`.
- [x] **P2 — Reward screen shown when there is no reward** (iOS
  CheckInFullscreen.swift:224-231) — now skipped, fixing the empty reward screen.
  NOTE: this bullet originally ALSO removed the `isToday` gate so the
  forced-yesterday flow got its reward (iOS has no such gate). **Thatcher
  reversed that in W89** — non-today check-ins must NOT show a reward. The
  today-only gate is back and is a deliberate iOS divergence.
- [x] **P2 — The yesterday guard consumed today's auto-present slot** (one shared
  `autoCheckInShownOn`), so completing a forced yesterday check-in swallowed
  today's. Split into `forcedYesterdayShownOn` + `autoCheckInShownOn`.
- [x] **P2 — Auto check-in fired immediately after onboarding.** iOS gates the
  on-load ladder behind `showCheckInOnLoad = !firstLaunch` (Home.swift:39-41);
  now gated on `completedOnboarding`.
- [x] **P2 — Clearing a check-in produced backend side effects iOS never
  produces**, including a phantom group-activity row (teammates saw "checked in"
  for a deletion) plus a spurious analytics event and day_info push. iOS bails on
  `guard let sober else { return }` (CheckInLogger.swift:83-84); now ported.
- [x] **P2 — The variable reward was re-rolled on EVERY write path**, including
  day-card edits (amount / timestamp / remove / smoked-again), re-stamping
  `dayInfo.variableRewardType` (which the calendar renders) and dirtying day_info
  each time. iOS only generates it in `CheckInFullscreen.handleCheckedIn`; now
  behind `logCheckIns(generateReward =)`, true only from the check-in screen.

**Open (W86 — audited, NOT fixed; needs Thatcher's call or is larger work):**
- [ ] **P2 — No midnight-rollover / app-resume re-evaluation.** `today` is
  computed once per composition and the ladder is `LaunchedEffect(selectedDay)`;
  iOS re-runs it on `scenePhase == .active` and has a 00:00:01 timer
  (Home.swift:156-160, TodayFeedView.swift:88-108). An app left open across
  midnight keeps treating yesterday as today.
- [ ] **P2 — No red-dot badge** on calendar days still needing a check-in
  (iOS CalendarView.swift:576-581). Not covered by W41 (that was node FILL).
- [ ] **P2 — Week strip can't be paged** to previous weeks (iOS pages every week
  back to program start), so older missed days are unreachable from week mode.
- [ ] **P2 — Check-in analytics don't match.** iOS emits one `checked_in` per
  LoggedCheckIn with method/amount/custom-id/`type`, plus a full funnel
  (`opened_check_in_screen`, `completed_check_in`, `skipped_check_in`, reward +
  topic events) all stamped with `day_number`. Android emits one batch event with
  an Android-invented name and `{sober}` only, so the iOS funnel is empty for
  Android.
- [ ] **P2 — Group not refreshed** after the check-in activity posts (iOS calls
  `groupController.refresh()`), so the leaderboard shows stale state.
- [ ] **P2 — Check-in reminder not rescheduled** by a check-in (overlaps W92a).
- [ ] **P2 — No achievement notification / `miniDisplayAchievementIDs`** is never
  written (declared but unused).
- [ ] **P2 — Most COUNT achievements unreachable** (only `check_in` is mapped)
  and `customData` is always empty, so detail cards lose their numbers.
- [ ] **P2 — Amount picker is dead code**: `awaitingAmount` is never set true, so
  no amount can be recorded at check-in time. Needs a product call — restore the
  iOS hold-to-open interaction, or delete the picker.
- [ ] **P3 — `lastSmoked` recomputed unconditionally** → clear-timer drift; iOS
  guards on the latest smoked entry actually changing. Other half of W87.
- [ ] **P3 — No `onNeedReShowCheckIn`**: clearing a day's last check-in leaves it
  half-logged instead of re-presenting.
- [ ] **P3 — Skip doesn't clear a pre-existing check-in** (iOS
  `handleSkip(removeCheckIn: true)`). Divergent but arguably safer — decide.
- [ ] **P3 — Month-mode day preview** is fully editable on Android; iOS is
  read-only there and its inline check-in is `showAll: false` (weed slider only).
- [ ] **P3 — "Custom Check In" upsell row** on the day card not ported.
- [ ] **P3 — Dead code**: `today/calendar/WeekCalendar.kt`, `MonthCalendar.kt`,
  `CalendarCard.kt`, `checkin/DayDetailSheet.kt` are unreferenced and contain a
  different, older check-in rendering — they will mislead future audits.
- Multi-sheet weekday order (Mon-first) is left as-is: PARITY W1 records
  Mon-start as the intended Android spec, though iOS is locale/Sunday-first.

**Verified matching** (so coverage is known): ladder order + all four branch
conditions, multi threshold (2) and `sober == null` missed-day definition, today
excluded from catch-up, day-0 gate (`absoluteDay >= 1`), no break-state gating
anywhere (iOS never reads `currentBreak` in the ladder), future days never
checkable, timezone/day-boundary handling, sheet step order + undo, slide-to-
confirm mechanics, `handleMultiCheckIn` semantics, both reward generators, the
slipped-nudge gate + window + copy, health-setback math, and the wire format.

- [x] **W87b · P2 — Community post from the Today feed: open in place + the
  "anon / Post" placeholder.** (Thatcher, 2026-07-22)
  (a) Tapping a post in the feed carousel fired a `DeepLinkRoute.Post` AND
  `requestTab("COMMUNITY")`, yanking the user out of the feed. iOS just sets
  `activeSheet = .communityPostDetail` and shows it over the current tab
  (TodayFeedCommunity.swift:73-75). Now opens in place in a
  `Clear30FullScreenCover` on the Today tab; back returns to the same feed page.
  (`PostDetail` is built as a full screen with its own back chevron and ime /
  navbar padding, so a cover fits it better than a bottom sheet — iOS uses
  `.sheet`; deliberate, noted.) Edit from here hands off to the Community tab.
  (b) **The "anon / Post" bug**: `CommunityTab`'s deep-link drain substituted a
  placeholder `Post(userId = "", title = "Post", body = "")` when the post wasn't
  on the cached feed page, with a comment claiming the detail view would fill it
  in later — nothing ever did. `UserDirectory.lookup("")` returns "anon"
  (UserDirectory.kt:52), so the post rendered permanently as anon/"Post" with an
  empty body while its comments (fetched by id) loaded fine. Now fetches the real
  post via `getCommunityPostById`, with an error alert on failure.
  (c) `PostDetail` now also primes the POST author in `UserDirectory` (it only
  primed commenters), so a post opened directly — feed carousel or deep link,
  neither of which goes through the feed's batch priming — resolves its author
  instead of showing the cold-miss "User #abc123" placeholder.
  VERIFIED on-emulator: opens in place showing "Alex" + real title/body/tags and
  both comment authors; back returns to the same feed card.

### 1a-xii. Wave 19 — Thatcher's UI/UX batch (2026-07-22)

- [x] **W88 · Ten-item polish batch** (4 parallel agents on disjoint files + 2 done inline):
  1. **Slice game circles animate in.** iOS `SliceGame.swift:277,508` appends the
     target inside `withAnimation(.spring(response: 0.26, damping: 0.7))` with a
     `.scale(0.5) + .opacity` transition — matched exactly. Each orb is wrapped in
     `key(t.id)` so it animates independently on spawn and survivors keep state
     across the engine's `clear()/addAll(survivors)`. Engine, hit-testing,
     lifetimes and scoring untouched.
  2. **2-up resource cards stretch to equal height.** `SymptomDetailScreen`'s
     `TwoColumnGrid` and `AllPromptsScreen`'s `PromptGrid` now use
     `height(IntrinsicSize.Min)` + `fillMaxHeight()` cells, with the Claire
     "Start chat" pill bottom-pinned via `Spacer(weight(1f))`. (`ResourcesScreen`
     + `LibraryRails` were already correct — rails are 1-up.)
  3. **Feedback monster card rebuilt.** Ported the iOS `BrowseButton`
     (Support.swift:559-635) the card is supposed to use: art sized by WIDTH with
     aspect preserved (was a 72dp square for 446x268 art → rendered 72x43 floating
     in dead space), `bottomImage` path drops the card padding so the cutout sits
     flush to the bottom edge, title bottom-leading with `maxLines = 2`.
     `FeedbackConfigCard` now honors the previously-dead `card_bottom_image` field
     and iOS's `defaultImageWidth = 40`. Send + link buttons → `TextIconButton`.
  4. **Journal prompt vs entry — TWO root causes.** (a) The prompt page never knew
     about answered entries: iOS renders it through `JournalFeedView` with
     `.onlyJournalsWithPrompts` (TodayFeedViews.swift:1133-1140) where matching
     entries REPLACE the prompt card; Android had no such path. (b) **Matching
     never matched**: `TextEntryEditor` saves `title.trim()` but the live prompt
     data isn't trimmed — 174 of 427 `journal_prompts` rows (41%) carry trailing
     whitespace — so a prompt-seeded entry never equalled its prompt and was
     classified free-form, producing the extra top-of-feed card. Added
     `JournalEntries.answersPrompt/answersAnyPrompt` (trim + ignoreCase) shared by
     both feeds, and a `JournalFeedCard` port. Also: MessageDetail now saves new
     entries under the MESSAGE's day (iOS `newJournalEntry(date:)`) instead of
     `now()`, without which journaling on a caught-up past message filed under
     today and could never replace the prompt.
  5. **Bottom-pinned card footers.** Root cause was two weighted siblings — the
     body had `weight(1f, fill = false)` AND a trailing `Spacer(weight(1f))`, so
     leftover height split 50/50 and the footer floated mid-card. Fixed in
     `RedditFeedCard`, `JournalEntryFeedCard`, `GuidesFeedCard`; audited every
     other card in FeedContentCards.kt (the rest were already correct).
  6. **Removed the share-your-experience page** from the Today community carousel
     (deliberate iOS divergence — the feed-end card carries the CTA).
  7. **Achievement detail nav buttons span the full width** (`Row` + `weight(1f)`
     each). Ends now dim-and-disable like iOS `navButtons` instead of vanishing,
     so the pair always fills the row; the whole pill is tappable now, not just
     the 20dp icon.
  8. Covered by (5).
  9. **MessageDetail header**: topic title centered above the progress bar.
  10. **Previous Breaks removed** — card, page overlay and
      `previousbreaks/PreviousBreaksSection.kt` deleted.

  Flagged, NOT changed: `CommunityCarousel.DayPostCard`'s body is unbounded so a
  very long post could still push its footer off; `TodayTab`'s message-less-day
  card still shows a bare empty prompt card where iOS uses
  `.allJournalsAndPrompts`; answered-entry titles render raw `**markdown**`
  (matching is whitespace-insensitive, not markdown-insensitive, and titles carry
  the same markdown so they still match).

### 1a-xiii. Wave 20 — Thatcher's second UI batch (2026-07-22)

*(Implemented without on-device verification at Thatcher's request — build-verified only.)*

- [x] **W89 · Batch** (5 parallel agents on disjoint files + 1 inline):
  1. **Confetti rendered as a single blob** in the three slip activities (plan /
     why / community). Root cause: `ConfettiOverlay` computes
     `radius = minOf(w, h) * 0.9f`, and those call sites passed
     `Modifier.fillMaxWidth()` inside `SlippedSheet`'s unbounded
     `verticalScroll` Column — height resolved to the min constraint (0), so
     radius, every velocity and gravity were all 0 and all 100 rects drew at one
     point (the `y > h + 60` cull didn't even remove them). Call sites → 
     `matchParentSize()`; the component now derives its radius from whichever
     dimension is real with a floor, so it can't collapse again. Other six call
     sites audited — all bounded, unchanged.
  2. **Chat/comment composers floated too high** (Gerad, Dr Fred, Claire, and the
     community comment row; Claire's agree/disagree too). Root cause is the W81
     rule on the BOTTOM edge: `AllTabs`' Scaffold bottom padding already includes
     the nav-bar inset (CustomTabBar applies
     `windowInsetsPadding(navigationBars)` itself) and M3 1.3.1's Scaffold does
     NOT consume insets for its content — so each screen's
     `navigationBarsPadding()` counted the gesture bar twice, and the chained
     `imePadding()` stacked it again with the keyboard up. Swapped for
     `consumeWindowInsets(WindowInsets.navigationBars)`, keeping `imePadding()`
     so the composer still clears the IME.
  3/8. **Two more shadow clips — both VERTICAL edges.** Compose only inflates a
     scroll container's clip on the CROSS axis, so a vertical scroller is tight
     top/bottom: the community Activity lists sliced the first/last card's
     shadow (insets lived on the parent Column, outside the clip → moved to
     `contentPadding`), and Group Settings' name card sat flush at the scroll's
     top edge (padding moved after `.verticalScroll(...)`).
  5. **No reward for a non-today check-in** (Thatcher). REVERSES the W85 bullet
     that removed the `isToday` gate for iOS parity — iOS rewards whatever day
     was logged (CheckInFullscreen.swift:189-241). Recorded as a deliberate
     divergence; the empty-reward skip from W85 stays.
  6. **Topic card emoji+title now centered as a pair on 2 lines.** iOS
     `ProgramMessageTopicCard` is an `HStack` and SwiftUI HStacks HUG a wrapped
     `Text`; a Compose `Text` takes the full offered width the moment it wraps,
     so the Row grew to card width and pinned the emoji left. Replaced with a
     custom `Layout` that measures the title's hug width (binary search on
     `minIntrinsicHeight`) and places the pair as one centered unit.
  7. **Meditation scrubber hidden until playback** — gated on `everPlayed`, the
     port of iOS `audioSessionSetup` (`.opacity(audioSessionSetup ? 1 : 0)`,
     MeditationPage.swift:69-71), seeded from the shared controller so
     re-entering a mid-playback card doesn't re-hide it. Faded via `alpha` so the
     space stays reserved and the card doesn't jump. Bar inset by
     `Dimens.horizontalPadding` (iOS MeditationPage.swift:77). NOTE: the scrubber
     lives in `MeditationPage.kt` (`MeditationPlayerCore`), shared by the feed
     card AND the full-screen sheet — so the sheet hides its bar until play too,
     which is iOS's behavior for both.
  10. **Health timeline no longer scrolls the new item to the top** — removed the
      `rememberLazyListState(initialFirstVisibleItemIndex)` anchoring (the old
      "W2" behavior). Reveal animation + confetti untouched.
  11. **Achievement gyro removed** — the SensorManager tilt listener is gone,
      replaced by a finger-drag tilt (`detectDragGestures` → the same
      pitch/roll pair, clamped to the previous ±35°) that springs back to
      neutral on release. rotationX/Y, cameraDistance, parallax and shine are
      unchanged, so it looks identical. Drag is on the card only; close and nav
      buttons are later siblings so they still hit-test above it.

  Flagged: the meditation gate is shared with the full-screen sheet (revert to
  inline-only if the sheet should keep showing its bar immediately).

### 1a-ix. Helium paywall SDK integration (2026-07-22)

- [x] (done 2026-07-22, uncommitted — **Helium Android SDK wired as the primary
  paywall**, mirroring iOS `HeliumPaywallView` + `HeliumRevenueCatDelegate`.
  Landed: deps `com.tryhelium.paywall:core:4.0.0` + `:revenue-cat:4.0.0`
  (resolve from Maven Central); **RevenueCat bumped 8.10.5 → 9.7.0** — FORCED by
  Helium's revenue-cat module (it depends on RC 9.7.0), the existing
  PaywallController compiles unchanged against 9.x (verified); `HELIUM_API_KEY`
  BuildConfig from local.properties (blank-safe like the RC key);
  `PaywallController.initHeliumSDK` (`Helium.initialize`, called in
  `Clear30Application.onCreate` after RC) + filled `initHelium` (pushes
  `Helium.identity` userId/revenueCatAppUserId + the `getUserParams` traits as
  typed `HeliumUserTraits`) + `heliumEnabled()`; new `HeliumPaywall` composable
  (`Helium.presentPaywall(trigger, PaywallPresentationConfig(...), onEntitled,
  HeliumEventListener, onPaywallNotShown)` with the `RevenueCatDelegate(activity)`
  purchase bridge attached per-presentation) that maps Helium events onto the
  existing `PaywallController`/`onCompleted` flow — `PaywallWebViewRendered` →
  openedPaywall + currentPaywallID, `PurchaseSucceeded`/`PurchaseRestored` →
  resolve entitlement + complete, soft `PaywallDismissed` → free-close; `Paywall`
  now branches Helium-vs-native on `heliumEnabled()`, and `onPaywallNotShown`
  (holdout/error) or a blank key falls back to the untouched native RevenueCat
  paywall (renamed `NativePaywall`). API verified by introspecting the resolved
  AARs (javap), compiles clean, installs + boots with no crash (blank key →
  no-op → native path, unchanged). Free codes honored; §6 extras
  (OTO downsell / Stripe / sale checks) deliberately not ported. NEEDS Thatcher's
  external config to actually render — see §1c.)
  **Helium paywall SDK — integrated, gated on `HELIUM_API_KEY`.**

### 1b. Verification debt (landed, but not fully exercised on-device)

- [ ] X1 — iOS-simulator cross-check: sign up on Android, sign in on iOS,
  restore must succeed.
- [ ] T3 — slip-side reward rendering; T4 — slip timestamp rows + detail editor.
- [ ] T9c — YouTube autoplay on page focus (no YT card in test account's day).
- [ ] N3 — actual WorkManager fire times (day-scale wait; logic is a
  line-for-line port).
- [ ] C1 — pinned hide/return flow. *(Backend half VERIFIED 2026-07-21: the
  `sort_by` overload Android calls — exclude_pinned defaults false — returns
  pinned rows first; confirmed against the local DB with 3 seeded pinned
  rows. Remaining: the client hide → return UI flow.)*
- [ ] C3 — post delete + report flows.
- [ ] G3 — multi-member badge taps / ping RPC, hue-picker round-trip.
- [ ] P2 — snake-calendar connector highlight rules with a mid-break account.
- [ ] P6 — video-entry naming + share-to-community (needs camera + auth).
- [ ] S12 — talk/why/testimonial/community slip activities individually
  (testimonials are tap-to-play; iOS autoplays).
- [ ] O11 / F1 / F2 / EXP2 / O12 — fresh-signup on-device pass (start-date
  step, school feed cards, mid-pilot assessment, the hardcoded short
  onboarding flow: slide order incl. moderation path → Trigger → Commitment,
  and the O12 additions: Planned-Usage appears after the triggers affirmation,
  Money-Spent "Custom amount" entry, and 0-based slider indices in the
  submitted `program_assessment_responses` row).
- [ ] T12 — Today-feed pass: pinch-zoom on a carousel image (zoom + pan +
  spring-back + dismiss), catch-up card with a ≥3-missed-days account (rows
  open the day, dismiss persists across restart), journal entry card round
  trip (create on empty day, edit from feed), message-card badge on an
  assessment-personalized and a school message.

### 1c. Prod readiness (external — Thatcher only)

*(Decided 2026-07-13: ships as an UPDATE to the old RN app's Play listing —
`applicationId` = `org.clear30.Clear30v1` (debug: `.debug`). Verify the old
app's signing key still exists or the listing is on Play App Signing.)*

- [ ] RevenueCat: create the Play Store app in the dashboard → `goog_` key into
  `local.properties`. *(NB the SDK was bumped 8.10.5 → 9.7.0 to match Helium's
  `revenue-cat` module — smoke-test a real purchase/restore once the key is live.)*
- [ ] **Helium paywall (SDK now integrated, needs external config to turn on):**
  (1) grab the Android API key at app.tryhelium.com → `HELIUM_API_KEY=...` in
  `local.properties`; (2) in the Helium dashboard, build the paywalls + create the
  triggers whose keys match `PaywallTrigger` raws — `clear30_onboarding` and
  `clear30_popup` are the two the Android app requests today; (3) with both the
  Helium and RevenueCat keys set, exercise onboarding + the existing-user popup.
  Blank `HELIUM_API_KEY` → the app uses the native RevenueCat paywall (§1a-ix).
- [ ] Firebase: add Android apps `org.clear30.Clear30v1` +
  `org.clear30.Clear30v1.debug` to project `clear30-24f18` → real
  `google-services.json`.
- [ ] Play Console: signing keystore (must be the OLD app's key), update the
  existing listing, internal-testing track.
- [ ] Launcher icons + SVG vector import (25 brand SVGs in
  `android/svg-import/`; polish).
- [ ] **Re-encode the 8 testimonial videos to H.264** (W17 diagnosis): all of
  `https://m.clear30.org/testimonials/1..8.mp4` are HEVC/H.265 with
  moov-at-end — iPhones hw-decode them but many Android devices can't, so
  they silently fail on Android. Per file:
  `ffmpeg -i in.mp4 -c:v libx264 -crf 22 -c:a aac -movflags +faststart out.mp4`
  then re-upload to the same URLs (no app change needed on either platform;
  H.264 also plays fine on iOS). The other site videos are already fine —
  `videos/testimonial_intro_2.mp4` verified H.264+faststart.

### 1d. Deferred / backlog

- **D2 — Old-Android-app user migration**: the recoverable data (server
  day_info history, device last_smoked + journals) is now migrated — see W74.
  Remaining by design: the old app's device-local program/break state is not
  reconstructed (users onboard fresh, per §17-Q7).
- **D3 — Widgets beyond StatsWidget** (Snake/Roman calendar, Health, Timer).
- **D4 — Navigation Compose migration** (full back-stack/sheets/toasts rework).
- ~~D5 — Markdown rendering~~ **CLOSED by W60** (inline + block renderers
  matched to the real content's syntax).
- ~~D6 — AchievementEngine criteria parity~~ **CLOSED by W55/W57** (criteria
  rewritten 1:1 against iOS AchievementManager + live definitions; only
  COUNT-type activity counters remain unimplemented on both platforms' data).
- **Pop-in notifications** — deferred entirely per §17-Q13 (no PopInGenerator,
  no silent-push consumption, no post-check-in schedulePopInRequest).

---

## 2. Completed work (Waves 1–8 + follow-ups)

All original items X1–X10, A1/A3, O1–O11, B1–B6, T2–T11, S1–S13, P1–P9, C1–C3,
G1/G3, N1–N3, F1/F1b/F2/F3, E1–E4 are **done and ticked**; full notes in git
history (`d62f296` and earlier).

| Wave | Items | Landed |
|------|-------|--------|
| 1 — P0 data integrity | X1, X2+B4, X5, B1, B2, X3, X4 | 2026-07-13, 3a63877 |
| 2 — onboarding correctness | O1, O2, O3, O4+X7, O6, O7, O8, O10 | 2026-07-13, 3a63877 + 6d33fe8 |
| 3 — environment | E1–E4, N1 | 2026-07-13 |
| 4 — content & viewers | T5–T7, S1–S11 | 2026-07-13, 8c944dd |
| 5 — check-in/profile/community/groups | T2–T4, P1–P6, C1–C3, G1 | 2026-07-13, c6b3055 |
| 6 — pilot features | F1–F3, O11, B3, X6, S12, N2 (health half) | 2026-07-13, 72b9af9 |
| 7 — post-Wave-5 feedback | T8, P7, G3, F1b | 2026-07-13, 89941d2 |
| 8 — post-Wave-7 feedback | X10, P9, B6, B5+P8, T9, O5, S13, A1 (verified no-code), N3, X9, A3, O9-styling | 2026-07-14, 3f171e9 |
| follow-ups | T10 (day-scoped feed + full-height layouts) · T11 (meditation/YouTube/Claire cards) | 2026-07-14, c81c05b · d62f296 |
| 14 — remaining-items closeout | W18 remainder, W69, W70, W71, W73(a-d), W75, EXP4(d), Q23 decision | 2026-07-22, uncommitted |

Closed / won't fix: X8 (loggingID device-ID, §17-Q15) · A4 (appstack, §17-Q16)
· A5 (§17-Q16) · G2 (remainders, §17-Q19) · O9's reviews fetch (§17-Q16).

Backend artifacts landed in the iOS repo: seeds 38–42 (sleep resources,
feature ideas, health-step notification copy, test-school messages/activities),
`BE/scripts/seed_reddit_threads.mjs`; commits 3d986e12, 1e035597.

---

## 3. Implementation gotchas (keep in mind for future work)

- **Compose staleness with in-place-mutated models:** `program` etc. are
  mutated in place (SwiftData parity), so strong skipping keeps stale leaves.
  Thread a `revision`/`refresh` counter that is *genuinely read* by every leaf
  rendering derived values (unused params are excluded from the skip
  comparison). Applied in T4 (check-in card / day pills) and P9 (ProfileTab).
- **Break mutations must run on `Clear30Application.appScope`**, not
  `rememberCoroutineScope` — `endBreak` nulls `currentBreak` in place before
  its network work finishes, so the composable leaves composition and cancels
  its own scope mid-flight (B6 hardening).
- **Material DatePicker returns UTC-midnight millis** — convert via local TZ or
  night-time picks store the previous day (found in B3).
- **Local edge functions need `supabase functions serve --no-verify-jwt`**
  (local runtime rejects even the local anon key) — `android/run.sh` starts it
  automatically, logging to `/tmp/clear30-functions-serve.log` (E1). Required
  for Claire, `reddit_proxy`, normative feedback.
- **Reddit locally:** reddit.com 403s the local function's own fetch and
  `library.reddit_threads` is empty — seed threads
  (`BE/scripts/seed_reddit_threads.mjs`) or the viewer falls back to WebView.
  Prod has the cache + OAuth secrets.
- **Health model:** persisted under key `healthProgressV2` (v1 was a flattened
  dev-only shape). Local seed-data bug: `library.health_categories` has Heart's
  `long_name='Brain'` — data, not app. iOS's own health pushes never fire
  (decode bug on their side); Android decodes properly and SENDS them.
- **FCM:** token is fetched in `Clear30Application.onCreate` (`onNewToken`
  only fires on creation) and synced via `AppRootViewModel.observeFcmToken`.
  Local `notification_send` runs need the service-account key added to
  `BE/supabase/functions/.env` (prod-only secret
  `FIREBASE_SERVICE_ACCOUNT_JSON_B64_ENC`).
- **sfSymbol fallbacks:** unmapped names silently fall back to a circle glyph —
  when adding icons, verify the mapping exists (`arrow.down`,
  `calendar.badge.checkmark` were missing until Wave 8).
- **Scroll containers clip soft shadows:** use the `scrollShadowFix` pattern
  (column keeps `horizontalPadding − scrollShadowFix`, children pad the
  difference) — see T9b in TodayTab.

## 4. Verified non-issues (don't "fix" these)

- User-ID model matches iOS cross-platform (`auth_id → users.id`); no client
  IDs minted.
- Core scheduling math in `schedule()`/`scheduleStartSoon()` is byte-faithful;
  restore pipeline matches iOS; `endDate`/`endDateOverride` ±1 semantics
  round-trip identically.
- No data from this new app has ever reached prod (verified 2026-07-13); the
  3,116 prod `android` rows are the OLD Android app's users.
- Prod `create_user` handles `platform` correctly — X1 was client-side only.
- The smoked-check-in crash was SOLVED (D1, 2026-07-13): `StackOverflowError`
  from `CheckInMethod.getAmountString(index)` overload recursion; fixed +
  verified in c6b3055.
- Email OTP verify: Android's single `OtpType.Email.EMAIL` covers signup +
  magiclink — iOS's `signup`→`email` retry is unnecessary (A1, verified
  on-device 2026-07-14).

## 5. Decision log (Thatcher)

**2026-07-13:**
- **Q1 (O1):** Most recent What-brings-you-here choice wins.
- **Q2 (O3):** Only "Moderation" routes to Life; do NOT ask mod-vs-weed-free
  afterward. *Intentional Android divergence; iOS unchanged.*
- **Q3 (O7):** Pain-point target = iOS `AssessmentPainPoint.swift`
  (consumption-method + monthly-spend variant).
- **Q4 (O10, C2):** Tab tutorial popups removed on all tabs; community prompt
  card removed.
- **Q5 (B2):** endBreak lands users in weed-free (mirror iOS).
- **Q7 (X4):** Old-Android-app migration deferred (D2); iOS-style detection
  (row + non-empty content_info) is correct interim.
- **Q8 (A2):** Phone/email OTP is enough — no Google Sign-In.
- **Q9 (O11):** Start-date step in scope, after the notification-permission
  popup.
- **Q10 (S11):** Unfed feedback monster = orange; fed = yellow.
- **Q11 (S12):** All six slip activities ported.
- **Q12 (F3):** SKIP the 3D-coin achievement slide entirely.
- **Q13 (N2):** Pop-in notifications deferred entirely; health half only.

**2026-07-14 (post-Wave-7 review):**
- **Q14 (A1):** Verify email-OTP fallback before writing code → verified
  unnecessary.
- **Q15 (X8):** loggingID stable-device-ID — won't fix.
- **Q16 (A4, A5, O9):** Appstack, adolescent-mode-on-restore, real-reviews
  fetch all dropped; O9 styling stayed as polish (done).
- **Q17 (O12):** Assessment script depth deferred.
- **Q18 (B5, P8):** Settings start-date picker removed; settings page mirrors
  iOS (or simpler).
- **Q19 (G2):** Chat pagination + intermediate invite share sheet not needed.
- **Q20 (N3):** Content-notification fire time re-derived from iOS source
  (−1h..0 window before the assessment smoke time, on the unlock day).

**2026-07-21:**
- **Q21 (EXP1):** Experiments are now driven by the DB `experiments` schema
  (overrides the old Amplitude experiment usage); verify assessment parity
  against the prod experiment states + iOS client fallbacks.
- **Q22 (EXP2/EXP3/EXP4a):** Mirror iOS, no divergence — and for these
  differences, don't listen for the experiments, hardcode the prod variant:
  short onboarding flow coded in (no `assessment-short-flow` read), moderation
  users share the main path, post-assessment interview branch removed.

**2026-07-22 (agent decision under the standing rules, not Thatcher):**
- **Q23 (W73):** Custom weekly-spend ≥$100 dream-outcome math — mirrored the
  iOS `prefix(2)` truncation quirk ("100" → $10/wk) per "iOS is the behavior
  spec"; both platforms would need changing together to use the full amount.
- **Q24 (free-unlock deep link):** The `?code=` deep-link branch (AppRoot) was
  a misport — it called `check_referral_code_json` (`payment.referral_codes`,
  which INSERTs unknown codes as non-free), so referral-site promo codes never
  unlocked the app. Re-pointed to iOS's actual path (`ReferralCodeHandler.
  handleURL` → `payment_check_code` / `payment.promo_codes`): guarded on
  `freeCode == null`, sets `freeCode` + iOS success/"not active" alerts +
  `opened_from_link(title=referral)`. The group-join-by-referral-code behavior
  the old branch had is NOT an iOS deep-link behavior (it belongs to manual
  onboarding entry, ReferralSlide) and was removed; group joins via link use
  `clear30://group/<code>` / `?group_id=` as before.

## 6. Out of scope (per Thatcher, 2026-07-13)

Experiments setup work · ShortcutHandler depth · Welcome-back popup ·
Three-day encouragement · Claire voice mode · Counselor/B2B/NYS modes ·
Supplements · Video testimonials · Influencer mode · School leaderboard ·
Google Sign-In · SMS/Twilio · Stripe/Shopify/Superwall payment fallback chain +
one-time-offer downsell (still out). **Helium is now integrated** (§1a-ix,
2026-07-22) as the primary paywall with the native RevenueCat paywall as the
holdout/error/unconfigured fallback — it no longer belongs on this list.
