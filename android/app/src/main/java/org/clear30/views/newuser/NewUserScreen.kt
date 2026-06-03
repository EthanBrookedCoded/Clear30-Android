package org.clear30.views.newuser

import org.clear30.data.model.ProgramNormativeFeedback

/**
 * Onboarding steps — ported from `NewUserScreen` (AllNewUser.swift). `type`
 * matches the iOS rawValue (used in the openedOnboardingScreen analytics).
 */
sealed class NewUserScreen(val type: String) {
    data object Intro : NewUserScreen("intro")
    data object Assessment : NewUserScreen("assessment")
    data object SignUp : NewUserScreen("sign_up")
    data class Feedback(val feedback: ProgramNormativeFeedback) : NewUserScreen("feedback")
    data object Notifications : NewUserScreen("notifications")
    data object Reviews : NewUserScreen("reviews")
    data object Commitment : NewUserScreen("commitment")
    data object Referral : NewUserScreen("referral")
    data object Payment : NewUserScreen("payment")
}
