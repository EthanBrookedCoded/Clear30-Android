package org.clear30.views.newuser.assessment

import org.clear30.data.model.AffirmationCard
import org.clear30.data.model.AssessmentInfoData
import org.clear30.data.model.AssessmentInfoDataID
import org.clear30.data.model.AssessmentQuestionID
import org.clear30.data.model.AssessmentQuestions
import org.clear30.data.model.BreakReasonType
import org.clear30.data.model.ExperimentKey
import org.clear30.data.model.NormativeData
import org.clear30.data.model.ProgramAssessmentResponse
import org.clear30.data.model.getSingleOption

/**
 * AssessmentSlides3 — the NEW-onboarding slide script, ported from
 * AssessmentSlides3.swift. Builds the slide sequence and the branching follow-ups.
 *
 * Pragmatic port for visual parity: the full **clear30** and **moderation/life**
 * paths are wired faithfully (question order, auto-affirmations, the credibility →
 * referral → fair-trial → commitment tail). Simplified vs iOS: guardian/adolescent
 * branches, remote A/B questions, and the bespoke custom views (pain-point chart,
 * dream-outcome, affirmation-cards) — those render via their [AssessmentInfoDataID]
 * presentation in AssessmentInfoSlide until the chart renderers land (L2b).
 */
object AssessmentSlides3 {

    var name: String = ""

    fun addInitialSlides(viewModel: AssessmentViewModel) {
        name = ""
        viewModel.slidesWithCompletions.clear()
        viewModel.slidesWithCompletions.addAll(
            listOf(welcomeTypingSlide(), whatBringsYouHereQuestion()),
        )
        // (iOS also fetches normative + remote questions + guardian here — backend, deferred)
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
                        if (viewModel.experimentController.showFeature(ExperimentKey.assessmentBiologicalSex, false)) {
                            slidesToAdd.add(biologicalSexQuestion())
                        }
                        slidesToAdd.add(whereYouAre())
                        slidesToAdd.add(painPoint(viewModel))
                    }

                    AssessmentQuestionID.TRIGGER.raw -> {
                        slidesToAdd.add(triggersAffirmationSlide())
                        slidesToAdd.add(assessmentCredibility())
                        slidesToAdd.add(referral())
                        slidesToAdd.add(fairTrialSlide())
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
                        slidesToAdd.add(if (viewModel.choseClear30) clear30ProgramConfirmation() else lifeProgramConfirmation())
                    AssessmentInfoDataID.clear30Context -> {
                        slidesToAdd.add(previousBreakQuestion())
                        if (viewModel.experimentController.showFeature(ExperimentKey.onboardingSymptoms, false)) {
                            slidesToAdd.add(symptomsQuestion())
                        }
                        slidesToAdd.add(triggersQuestion())
                    }
                    AssessmentInfoDataID.lifeContext -> {
                        // §17-Q2: the ONLY route into Life is "Moderation" on
                        // What-brings-you-here, which auto-sets LO_USE_STATE=1 —
                        // don't re-ask moderation-vs-weed-free (deliberate
                        // divergence from iOS, which still shows modAbsQuestion).
                        slidesToAdd.add(assessmentCredibility())
                        slidesToAdd.add(referral())
                        slidesToAdd.add(fairTrialSlide())
                        slidesToAdd.add(commitmentQuestion())
                    }
                    else -> Unit
                }
            }
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
        into.add(consumptionMethodQuestion())
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

    private fun assessmentCredibility() = info(
        AssessmentInfoData(id = AssessmentInfoDataID.credibility, title = "", subtitle = "", body = "", primaryButtonText = "Looks Solid"),
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
    private fun consumptionMethodQuestion() = question(AssessmentQuestions.consumptionMethod.copy(prompt1 = "On the days you use,"))
    private fun usageDurationQuestion() = question(AssessmentQuestions.usageDuration)
    private fun moneySpentQuestion() = question(AssessmentQuestions.moneySpent)
    private fun helpHarmQuestion() = question(AssessmentQuestions.helpHarm)
    private fun ageQuestion() = question(AssessmentQuestions.age)
    private fun biologicalSexQuestion() = question(AssessmentQuestions.biologicalSex)
    private fun previousBreakQuestion() = question(AssessmentQuestions.previousBreak)
    private fun symptomsQuestion() = question(AssessmentQuestions.symptoms)
    private fun triggersQuestion() = question(AssessmentQuestions.triggers)
    private fun commitmentQuestion() = question(AssessmentQuestions.commitment)
    private fun referral() = question(AssessmentQuestions.referral)

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
            (NormativeData.defaultData.firstOrNull { it.frequency == key }
                ?: NormativeData.defaultData.lastOrNull())?.percentile_more_than
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
        )
    }

    private fun clear30ProgramConfirmation() = info(
        AssessmentInfoData(
            id = AssessmentInfoDataID.clear30Context,
            title = "Now let's fit Clear30 to you.",
            subtitle = "",
            body = "Now we'll shape Clear30 around how you actually live, matching your pace, your rhythm, your day-to-day.",
            primaryButtonText = "Make It Mine",
        ),
    )

    private fun lifeProgramConfirmation() = info(
        AssessmentInfoData(
            id = AssessmentInfoDataID.lifeContext,
            title = "Your Program:\nThe Better Life Program",
            subtitle = "",
            body = "We built this app to be more than a break.\n\nWith the Better Life Program, get daily content designed to help you grow as a person, track your use, and start a structured break at any point.",
            systemImageName = "hand.thumbsup.fill",
        ),
    )

    private fun triggersAffirmationSlide() = info(
        AssessmentInfoData(
            id = AssessmentInfoDataID.triggersAffirmation,
            title = "Clear30 was made for you.",
            subtitle = "",
            body = "We've helped others __just like you__ overcome their triggers and __take back control__.",
            primaryButtonText = "Finish Up",
        ),
    )

    private fun fairTrialSlide() = info(
        AssessmentInfoData(
            id = null,
            title = "Clear30 is Free for You to Try",
            subtitle = "",
            body = "After your trial, we depend on your support to keep delivering the best evidence-backed tools so you can stay committed to change.",
            primaryButtonText = "That's fair",
            imageName = "fair_trial_comparison",
        ),
    )
}
