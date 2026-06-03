package org.clear30.views.newuser

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import org.clear30.data.model.EntitlementType
import org.clear30.data.model.ExperimentController
import org.clear30.data.model.OnboardingSetup
import org.clear30.data.model.Program
import org.clear30.data.model.UserInfo
import org.clear30.views.newuser.assessment.AllAssessment
import org.clear30.views.components.DefaultButton
import org.clear30.views.components.Heading1
import org.clear30.views.components.SmallText
import org.clear30.views.theme.Dimens

/**
 * Onboarding host — ported from `AllNewUser` (AllNewUser.swift). Switches on the
 * flow state; each step is a placeholder composable that advances the flow
 * (filled out as IntroScreenHandler / AllAssessment / AllSignUp / Paywall etc.
 * are ported).
 */
@Composable
fun AllNewUser(
    userInfo: UserInfo,
    program: Program,
    onboardingSetup: OnboardingSetup,
    experimentController: ExperimentController,
    scope: CoroutineScope,
    onCompletedOnboarding: () -> Unit,
) {
    val vm = remember {
        AllNewUserViewModel(userInfo, program, onboardingSetup, experimentController, scope, onCompletedOnboarding)
    }
    val screen by vm.screen.collectAsStateWithLifecycle()

    Crossfade(screen, modifier = Modifier.fillMaxSize(), label = "newUser") { s ->
        when (s) {
            is NewUserScreen.Intro -> IntroScreenHandler(onGetStarted = vm::handleNextScreen, onSignIn = vm::handleSignIn)
            is NewUserScreen.Assessment -> AllAssessment(
                userInfo = userInfo,
                onboardingSetup = onboardingSetup,
                experimentController = experimentController,
                scope = scope,
                onComplete = vm::handleNextScreen,
                onBackFromFirst = { vm.back(NewUserScreen.Intro) },
            )
            is NewUserScreen.SignUp -> AllSignUp(
                userInfo = userInfo,
                scope = scope,
                onComplete = vm::handleNextScreen,
                onBack = { vm.back(NewUserScreen.Intro) },
            )
            is NewUserScreen.Feedback -> StepPlaceholder("Your feedback", "feedback", onNext = vm::handleNextScreen)
            is NewUserScreen.Notifications -> OnboardingNotificationRequest(onComplete = vm::handleNextScreen)
            is NewUserScreen.Reviews -> StepPlaceholder("Loved by thousands", "reviews", onNext = vm::handleNextScreen)
            is NewUserScreen.Commitment -> StepPlaceholder("Your commitment", "commitment", onNext = vm::handleNextScreen)
            is NewUserScreen.Referral -> StepPlaceholder("Referral code", "referral", onNext = vm::handleNextScreen)
            is NewUserScreen.Payment -> Paywall(popup = false) { entitlement -> vm.handlePayment(entitlement) }
        }
    }
}

/** Temporary step scaffold — replaced by the real screen as each is ported. */
@Composable
private fun StepPlaceholder(
    title: String,
    step: String,
    onNext: () -> Unit,
    nextLabel: String = "Next",
    onSecondary: (() -> Unit)? = null,
    secondaryLabel: String = "",
) {
    Column(
        Modifier.fillMaxSize().padding(Dimens.horizontalPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Heading1(title)
        SmallText("[$step screen — TODO]", Modifier.padding(top = 8.dp))
        DefaultButton(nextLabel, gradient = org.clear30.views.theme.Clear30Gradients.clear30, onClick = onNext)
        if (onSecondary != null) SmallText(secondaryLabel, Modifier.padding(top = 4.dp).then(Modifier), color = androidx.compose.ui.graphics.Color.Gray)
    }
}
