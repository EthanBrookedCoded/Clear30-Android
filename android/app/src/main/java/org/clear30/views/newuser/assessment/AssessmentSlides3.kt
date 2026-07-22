package org.clear30.views.newuser.assessment

import kotlinx.coroutines.launch
import org.clear30.data.model.AffirmationCard
import org.clear30.data.model.AssessmentInfoData
import org.clear30.data.model.AssessmentInfoDataID
import org.clear30.data.model.AssessmentQuestionID
import org.clear30.data.model.AssessmentQuestions
import org.clear30.data.model.BreakReasonType
import org.clear30.data.model.ExperimentKey
import org.clear30.data.model.NormativeData
import org.clear30.data.model.PlainDate
import org.clear30.data.model.ProgramAssessmentResponse
import org.clear30.data.model.RemoteAssessmentQuestion
import org.clear30.data.model.getSingleOption
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.getNormativeData
import org.clear30.data.supabase.getRemoteAssessmentQuestions

/**
 * AssessmentSlides3 — the NEW-onboarding slide script, ported from
 * AssessmentSlides3.swift. Builds the slide sequence and the branching follow-ups.
 *
 * Hardcodes iOS's SHORT flow (`assessment-short-flow` = show 100% in prod, coded
 * in rather than experiment-read per §17-Q22): no consumption-method, biological
 * sex, previous-break, symptoms, program-confirmation, credibility, referral, or
 * fair-trial slides — `clear30Recommendation` routes straight to the trigger
 * question for everyone, and moderation users diverge only at submission
 * (choseClear30/LO-Use-State are auto-set on the What-brings-you-here answer,
 * exactly like iOS). Remote questions (`programs.remote_assessment_questions`)
 * and the live normative-data fetch are ported; only the guardian/adolescent
 * branches remain unported (out of scope).
 */
object AssessmentSlides3 {

    var name: String = ""
    private var normativeData: List<NormativeData> = NormativeData.defaultData
    private var remoteAssessmentQuestions: List<RemoteAssessmentQuestion> = emptyList()

    fun addInitialSlides(viewModel: AssessmentViewModel) {
        name = ""
        normativeData = NormativeData.defaultData
        remoteAssessmentQuestions = emptyList()
        viewModel.slidesWithCompletions.clear()
        viewModel.slidesWithCompletions.addAll(
            listOf(welcomeTypingSlide(), whatBringsYouHereQuestion()),
        )
        // Best-effort background fetches, like iOS: live normative data + the
        // backend-defined injected questions. Slides built before a response
        // lands just use the defaults / no injection.
        viewModel.scope.launch {
            SupabaseController.getNormativeData()?.let { normativeData = it }
        }
        viewModel.scope.launch {
            remoteAssessmentQuestions = SupabaseController.getRemoteAssessmentQuestions()
        }
        // (iOS also checks a guardian code here — adolescent mode, out of scope.)
    }

    fun addNextSlides(
        currentSlide: AssessmentSlideWithCompletion,
        currentIndex: Int,
        viewModel: AssessmentViewModel,
        chosePrimaryOption: Boolean,
    ) {
        currentSlide.completion?.invoke(viewModel, chosePrimaryOption)

        val slidesToAdd = mutableListOf<AssessmentSlideWithCompletion>()
        var affirmationSlide: AssessmentSlide.Information? = null

        when (val slide = currentSlide.slide) {
            is AssessmentSlide.Question -> {
                val question = slide.question
                val response = viewModel.responses[question.strippedPrompt]
                when (question.strippedPrompt) {
                    AssessmentQuestionID.WHAT_BRINGS_YOU_HERE.raw ->
                        slidesToAdd.add(nameQuestion(firstQuestion = false))

                    AssessmentQuestionID.NAME.raw -> {
                        slidesToAdd.add(assessmentSocialProof())
                        slidesToAdd.add(breakReasonQuestion())
                    }

                    AssessmentQuestionID.BREAK_REASON.raw -> {
                        val wbyh = viewModel.responses[AssessmentQuestionID.WHAT_BRINGS_YOU_HERE.raw]
                        val isTBreak = wbyh?.responses?.firstOrNull() == 1
                        if (isTBreak) {
                            slidesToAdd.add(thenWhatQuestion())
                        } else {
                            slidesToAdd.add(goalsAffirmationSlide(viewModel))
                            addUseQuestions(slidesToAdd, viewModel)
                        }
                    }

                    AssessmentQuestionID.THEN_WHAT.raw -> {
                        slidesToAdd.add(goalsAffirmationSlide(viewModel))
                        addUseQuestions(slidesToAdd, viewModel)
                    }

                    AssessmentQuestionID.HELP_HARM.raw -> slidesToAdd.add(ageQuestion())

                    AssessmentQuestionID.AGE.raw -> {
                        slidesToAdd.add(whereYouAre())
                        slidesToAdd.add(painPoint(viewModel))
                    }

                    AssessmentQuestionID.TRIGGER.raw -> {
                        slidesToAdd.add(triggersAffirmationSlide(viewModel))
                        slidesToAdd.add(commitmentQuestion())
                    }
                }

                // Auto-add the question's affirmation for the chosen option.
                val affirmations = question.affirmations ?: emptyList()
                if (question.autoAddAffirmation != false) {
                    val idx = response?.responses?.firstOrNull()
                    if (idx != null && idx < affirmations.size) {
                        affirmationSlide = AssessmentSlide.Information(affirmations[idx])
                    }
                }
            }

            is AssessmentSlide.Information -> {
                when (slide.data.id) {
                    AssessmentInfoDataID.currentUseSummary -> {
                        slidesToAdd.add(whereYouCouldBe())
                        slidesToAdd.add(dreamOutcome(viewModel))
                    }
                    AssessmentInfoDataID.clear30Recommendation ->
                        // Short flow: everyone (incl. moderation/Life users, whose
                        // LO_USE_STATE was auto-set at What-brings-you-here) goes
                        // straight to the trigger question; Life routing happens
                        // at submission only, like iOS.
                        slidesToAdd.add(triggersQuestion())
                    else -> Unit
                }
            }
        }

        // Remote assessment questions (iOS AssessmentSlides3.swift:297-342):
        // backend-defined questions are injected right after the slide whose id
        // matches after_question_id — question slides match on strippedPrompt,
        // info slides on the id raw (e.g. "triggers_affirmation").
        val afterId = when (val s = currentSlide.slide) {
            is AssessmentSlide.Question -> s.question.strippedPrompt
            is AssessmentSlide.Information -> s.data.id?.raw
        }
        val remoteSlides = remoteAssessmentQuestions
            .filter { it.enabled && it.afterQuestionId == afterId }
            .map { AssessmentSlideWithCompletion(AssessmentSlide.Question(it.toProgramAssessmentQuestion())) }
        if (remoteSlides.isNotEmpty()) {
            if (slidesToAdd.isEmpty()) {
                // No branch slides of our own: re-append the already-queued
                // pending slides after the remote questions (minus a duplicate
                // of the affirmation we're inserting, and minus copies of the
                // remote questions themselves — going back and forward re-runs
                // this builder, and without that filter every round-trip
                // prepends the remote questions on top of the copies already
                // sitting in the pending list, duplicating them), since the
                // append below replaces everything past the current slide.
                val affirmationId = affirmationSlide?.data?.id
                val remotePrompts = remoteSlides
                    .mapNotNull { (it.slide as? AssessmentSlide.Question)?.question?.strippedPrompt }
                    .toSet()
                slidesToAdd.addAll(
                    viewModel.slidesWithCompletions.drop(currentIndex + 1).filterNot { swc ->
                        val info = (swc.slide as? AssessmentSlide.Information)?.data
                        val prompt = (swc.slide as? AssessmentSlide.Question)?.question?.strippedPrompt
                        (affirmationId != null && info?.id == affirmationId) || prompt in remotePrompts
                    },
                )
            }
            slidesToAdd.addAll(0, remoteSlides)
        }

        // Affirmation-ONLY completion (no branch slides, no remote questions):
        // iOS `insertAffirmationSlide` SPLICES the affirmation in after the
        // current slide without clearing the pending queue — the truncate-and-
        // append below would instead wipe every queued follow-up slide.
        val affirmation = affirmationSlide
        if (slidesToAdd.isEmpty() && affirmation != null) {
            val insert = AssessmentSlideWithCompletion(affirmation)
            val next = viewModel.slidesWithCompletions.getOrNull(currentIndex + 1)
            val nextInfo = (next?.slide as? AssessmentSlide.Information)?.data
            val questionAffirmations =
                (currentSlide.slide as? AssessmentSlide.Question)?.question?.affirmations.orEmpty()
            if (nextInfo != null && nextInfo in questionAffirmations) {
                // Re-answering replaced the choice — swap the stale affirmation.
                viewModel.slidesWithCompletions[currentIndex + 1] = insert
            } else {
                viewModel.slidesWithCompletions.add(currentIndex + 1, insert)
            }
            return
        }

        // Affirmation comes right after the current slide, then the branch slides.
        val ordered = (affirmationSlide?.let { listOf(AssessmentSlideWithCompletion(it)) } ?: emptyList()) + slidesToAdd

        // Substitute the client's name into prompts (_CLIENTNAME_).
        val named = ordered.map { swc ->
            val s = swc.slide
            if (s is AssessmentSlide.Question) {
                AssessmentSlideWithCompletion(
                    AssessmentSlide.Question(
                        s.question.copy(
                            prompt1 = s.question.prompt1.replace(CLIENT_NAME, name),
                            prompt2 = s.question.prompt2.replace(CLIENT_NAME, name),
                        ),
                    ),
                    swc.completion,
                )
            } else {
                swc
            }
        }

        // Before appending, drop anything already queued past the current slide
        // (iOS `removePendingSlides`). Without this, navigating back and forward
        // re-runs this builder and DUPLICATES the follow-up slides.
        if (named.isNotEmpty()) {
            while (viewModel.slidesWithCompletions.size > currentIndex + 1) {
                viewModel.slidesWithCompletions.removeAt(viewModel.slidesWithCompletions.lastIndex)
            }
            viewModel.slidesWithCompletions.addAll(named)
        }
    }

    private const val CLIENT_NAME = "_CLIENTNAME_"

    private fun addUseQuestions(into: MutableList<AssessmentSlideWithCompletion>, vm: AssessmentViewModel) {
        into.add(daysUsingQuestion())
        if (vm.experimentController.showFeature(ExperimentKey.assessmentUsageDuration, false)) {
            into.add(usageDurationQuestion())
        }
        into.add(moneySpentQuestion())
        into.add(helpHarmQuestion())
    }

    // ---- Builders --------------------------------------------------------------

    private fun info(data: AssessmentInfoData, completion: ((AssessmentViewModel, Boolean) -> Unit)? = null) =
        AssessmentSlideWithCompletion(AssessmentSlide.Information(data), completion)

    private fun question(
        q: org.clear30.data.model.ProgramAssessmentQuestion,
        completion: ((AssessmentViewModel, Boolean) -> Unit)? = null,
    ) = AssessmentSlideWithCompletion(AssessmentSlide.Question(q), completion)

    private fun welcomeTypingSlide() = info(
        AssessmentInfoData(
            id = AssessmentInfoDataID.welcomeTyping,
            title = "Welcome ☺️", subtitle = "", body = "We're glad you're here!",
            primaryButtonText = "Next",
        ),
    )

    private fun whatBringsYouHereQuestion() = question(AssessmentQuestions.whatBringsYouHere) { vm, _ ->
        val choiceIndex = vm.responses[AssessmentQuestionID.WHAT_BRINGS_YOU_HERE.raw]?.responses?.firstOrNull() ?: return@question
        // The MOST RECENT choice always wins (§17-Q1): every branch writes
        // choseClear30 explicitly, so touching Moderation and then backing out
        // to Quit/Break/Don't-know can't leave a stale `false` behind.
        when (choiceIndex) {
            0 -> {
                vm.choseClear30 = true
                vm.responses[AssessmentQuestionID.THEN_WHAT.raw] = ProgramAssessmentResponse(AssessmentQuestions.afterClear30, listOf(0))
            }
            1 -> vm.choseClear30 = true // T-Break: user answers Then-What
            2 -> {
                vm.responses[AssessmentQuestionID.THEN_WHAT.raw] = ProgramAssessmentResponse(AssessmentQuestions.afterClear30, listOf(1))
                vm.choseClear30 = false
                vm.responses[AssessmentQuestionID.LO_USE_STATE.raw] = ProgramAssessmentResponse(AssessmentQuestions.modAbs, listOf(1))
            }
            3 -> {
                vm.choseClear30 = true
                vm.responses[AssessmentQuestionID.THEN_WHAT.raw] = ProgramAssessmentResponse(AssessmentQuestions.afterClear30, listOf(3))
            }
        }
    }

    private fun nameQuestion(firstQuestion: Boolean = true): AssessmentSlideWithCompletion {
        val q = if (firstQuestion) AssessmentQuestions.name else AssessmentQuestions.name.copy(prompt1 = "")
        return question(q) { vm, _ ->
            val chosen = vm.responses[AssessmentQuestionID.NAME.raw]?.getSingleOption() ?: return@question
            vm.name = chosen
            name = chosen
        }
    }

    private fun assessmentSocialProof() = info(
        AssessmentInfoData(id = AssessmentInfoDataID.socialProof, title = "", subtitle = "", body = "", primaryButtonText = "I'm Next"),
    )

    private fun breakReasonQuestion() = question(AssessmentQuestions.breakReason)

    private fun thenWhatQuestion() = question(
        AssessmentQuestions.afterClear30.copy(
            prompt1 = "Got it $name!",
            prompt2 = "Now, what are your **long-term** goals regarding cannabis?",
        ),
    )

    private fun goalsAffirmationSlide(vm: AssessmentViewModel): AssessmentSlideWithCompletion {
        // One card per chosen break reason (iOS goalsAffirmationSlide): emoji +
        // goal title + an encouraging line (the % stat when it's ≥80%, else the
        // generic extra-info copy). The bottom card echoes the long-term goal.
        val reasons = vm.responses[AssessmentQuestionID.BREAK_REASON.raw]?.let { r ->
            r.responses.mapNotNull { idx -> r.question.options.getOrNull(idx)?.let { BreakReasonType.from(it) } }
        } ?: emptyList()
        val cards = reasons.map { type ->
            val pct = type.percentage?.percentage ?: 0.0
            val subtitle = if (pct >= 80) type.percentage?.text ?: type.extraInfo else type.extraInfo
            AffirmationCard(
                emoji = type.displayText.substringBefore(' '),
                title = type.displayText.substringAfter(' ').trim(),
                subtitle = subtitle,
            )
        }
        val longTermGoal = vm.responses[AssessmentQuestionID.THEN_WHAT.raw]?.getSingleOption().orEmpty()
        return info(
            AssessmentInfoData(
                id = AssessmentInfoDataID.goalsAffirmation,
                title = "You're in the right place!",
                subtitle = "",
                body = "**Thousands** have started with the same goals, and **Clear30 got them there**.",
                primaryButtonText = "Understanding Use",
                affirmationCards = cards,
                affirmationBottomLabel = "Where You're Headed",
                affirmationBottomText = longTermGoal.ifEmpty { null },
            ),
        )
    }

    private fun daysUsingQuestion() = question(AssessmentQuestions.daysUsing.copy(prompt1 = "Now, let's understand your use a bit more."))
    private fun usageDurationQuestion() = question(AssessmentQuestions.usageDuration)
    private fun moneySpentQuestion() = question(AssessmentQuestions.moneySpent)
    private fun helpHarmQuestion() = question(AssessmentQuestions.helpHarm)
    private fun ageQuestion() = question(AssessmentQuestions.age)
    private fun triggersQuestion() = question(AssessmentQuestions.triggers)
    private fun commitmentQuestion() = question(AssessmentQuestions.commitment)

    private fun whereYouAre() = info(
        AssessmentInfoData(
            id = AssessmentInfoDataID.whereYouAre,
            title = "Thanks $name!",
            subtitle = "",
            body = "Based on data from millions of others, here's where you're at.",
            primaryButtonText = "Show Me",
        ),
    )

    private fun painPoint(vm: AssessmentViewModel): AssessmentSlideWithCompletion {
        // Percentile from the days-per-week answer → normative frequency → percentile.
        val daysIdx = vm.responses[AssessmentQuestionID.DAYS_USING.raw]?.responses?.firstOrNull()
        val percentile = daysIdx?.let { idx ->
            val key = NormativeData.weeklyUsageMapping[idx + 1] ?: "Daily (every day)"
            (normativeData.firstOrNull { it.frequency == key }
                ?: normativeData.lastOrNull())?.percentile_more_than
        }
        return info(
            // iOS renders the pain-point chart on a WHITE background (the red
            // card supplies the color), not the green gradient.
            AssessmentInfoData(
                id = AssessmentInfoDataID.currentUseSummary,
                title = "",
                subtitle = "",
                body = "",
                painPointPercentile = percentile,
                overrideBackgroundGradient = false,
            ),
        )
    }

    private fun whereYouCouldBe() = info(
        AssessmentInfoData(
            id = AssessmentInfoDataID.whereYouGoing,
            title = "Where you could be",
            subtitle = "",
            body = "Now, this is where we'll take you.",
            primaryButtonText = "Show Me!",
        ),
    )

    private fun dreamOutcome(vm: AssessmentViewModel): AssessmentSlideWithCompletion {
        // Outcome nouns from the chosen break reasons; savings = weekly spend × 4.
        val reasonResp = vm.responses[AssessmentQuestionID.BREAK_REASON.raw]
        val nouns = reasonResp?.responses?.mapNotNull { idx ->
            reasonResp.question.options.getOrNull(idx)?.let { BreakReasonType.from(it)?.asNoun }
        }?.takeIf { it.isNotEmpty() }
        val spendResp = vm.responses[AssessmentQuestionID.MONEY_SPENT.raw]
        val monthly = spendResp?.responses?.firstOrNull()?.let { idx ->
            spendResp.question.options.getOrNull(idx)?.filter(Char::isDigit)?.toIntOrNull()?.let { it * 4 }
        }
        return info(
            // iOS renders the "30 Days From Now" projection on a WHITE
            // background; the gradient cards inside supply the color.
            AssessmentInfoData(
                id = AssessmentInfoDataID.clear30Recommendation,
                title = "",
                subtitle = "",
                body = "",
                primaryButtonText = "Continue",
                dreamOutcomeNouns = nouns,
                dreamOutcomeSavings = monthly,
                overrideBackgroundGradient = false,
            ),
        ) { viewModel, _ ->
            // iOS AssessmentSlides3.swift:887-895: completing the dream-outcome
            // slide auto-writes a Start-Date response = tomorrow, so the
            // submitted program_assessment_responses row carries the same
            // "Start-Date" key prod iOS rows do (handleBreaks reads it too —
            // tomorrow's start ⇒ Day 0 = today, no bridge, same as the
            // fallback, so only the payload changes).
            viewModel.responses[AssessmentQuestionID.START_DATE.raw] = ProgramAssessmentResponse(
                AssessmentQuestions.clear30Start.copy(
                    options = listOf(PlainDate.from(org.clear30.util.now()).adding(days = 1).dateString),
                ),
                listOf(0),
            )
        }
    }

    private fun triggersAffirmationSlide(vm: AssessmentViewModel): AssessmentSlideWithCompletion {
        // One card per chosen trigger (iOS triggersAffirmationSlide): the
        // trigger's emoji + label with its affirmation line underneath,
        // rendered through AffirmationCardsView like the goals slide.
        val resp = vm.responses[AssessmentQuestionID.TRIGGER.raw]
        val options = resp?.question?.displayedOptions ?: resp?.question?.options ?: emptyList()
        val affirmations = resp?.question?.affirmations ?: emptyList()
        val cards = resp?.responses.orEmpty().mapNotNull { idx ->
            val option = options.getOrNull(idx) ?: return@mapNotNull null
            val affirmation = affirmations.getOrNull(idx) ?: return@mapNotNull null
            AffirmationCard(
                emoji = option.substringBefore(' '),
                title = option.substringAfter(' ').trim(),
                subtitle = affirmation.body,
            )
        }
        return info(
            AssessmentInfoData(
                id = AssessmentInfoDataID.triggersAffirmation,
                title = "Clear30 was made for you.",
                subtitle = "",
                body = "We've helped others __just like you__ overcome their triggers and __take back control__.",
                primaryButtonText = "Finish Up",
                affirmationCards = cards,
            ),
        )
    }

}
