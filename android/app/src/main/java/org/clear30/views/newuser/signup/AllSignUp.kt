package org.clear30.views.newuser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.clear30.data.AssessmentSubmissionHandler
import org.clear30.data.Clear30Store
import org.clear30.data.model.OnboardingSetup
import org.clear30.data.model.Program
import org.clear30.data.model.SignUpType
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.fetchUserData
import org.clear30.data.supabase.getUserAuthID
import org.clear30.data.supabase.getUserID
import org.clear30.data.supabase.signInWithOtpEmail
import org.clear30.data.supabase.signInWithOtpPhone
import org.clear30.data.supabase.verifyOtpEmail
import org.clear30.data.supabase.verifyOtpPhone
import org.clear30.views.components.Heading3
import org.clear30.views.components.IconButton
import org.clear30.views.components.MiniText
import org.clear30.views.components.OffWhiteInput
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.pressScale
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

private enum class SignUpStep { INTRO, CONTACT, VERIFICATION, LOADING }

/**
 * AllSignUp — ported from AllSignUp.swift + AllSignUpViewModel.swift. Phone/email
 * OTP sign-up via Supabase Auth: pick a method -> enter contact -> verify code ->
 * create account.
 *
 * Apple sign-in is iOS-only; Android offers phone/email OTP (a Google provider
 * can be added later). Account-creation side effects beyond the auth + userInfo
 * patch (createUser RPC details) are a marked TODO.
 *
 * Visual structure mirrors the iOS SignUpView + PhoneEmailSheetView + VerificationView:
 *   - INTRO: leading Heading3 + dim subheading, a centered brand glyph, then the two
 *     distinct method affordances ("Continue with Phone Number" / "Continue with Email")
 *     split by an "or" divider — matching iOS's `signUpButtons`.
 *   - CONTACT: the chosen-method entry field (with a phone region prefix when on phone),
 *     the gradient "Let's Go!" CTA, the confidentiality copy + Terms of Use line, and a
 *     toggle to the other method — matching iOS's PhoneEmailSheetView.
 *   - VERIFICATION: 1:1 port of VerificationView (code field + "Verify" CTA + resend).
 * A back chevron (IconButton) sits top-leading like the iOS PagingView.
 */
@Composable
fun AllSignUp(
    userInfo: UserInfo,
    program: Program,
    onboardingSetup: OnboardingSetup,
    signInOnly: Boolean,
    scope: CoroutineScope,
    onComplete: () -> Unit,
    onBack: () -> Unit,
) {
    var step by remember { mutableStateOf(SignUpStep.INTRO) }
    var contact by remember { mutableStateOf("") }
    var isEmail by remember { mutableStateOf(false) }
    // iOS PhoneNumberRegionView default; the picker offers PHONE_PREFIXES.
    var phonePrefix by remember { mutableStateOf("+1") }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    // Set once the OTP verify succeeded — post-verify failures (restore/submit)
    // retry that part directly instead of burning the consumed code.
    var authVerified by remember { mutableStateOf(false) }

    // Full contact string sent to Supabase: phone prefixes the region code (iOS
    // sends "\(phoneNumberPrefix)\(input)"); email is sent verbatim.
    fun fullContact(): String = if (isEmail) contact.trim() else "$phonePrefix${contact.filter { it.isDigit() }}"

    fun sendCode() {
        if (contact.isBlank()) return
        error = null
        step = SignUpStep.LOADING
        scope.launch {
            val target = fullContact()
            val err = if (isEmail) SupabaseController.signInWithOtpEmail(target)
            else SupabaseController.signInWithOtpPhone(target)
            if (err == null) step = SignUpStep.VERIFICATION
            else { error = err.message; step = SignUpStep.CONTACT }
        }
    }

    org.clear30.views.components.StatusBarStyle(forceLightIcons = false)
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        // Back affordance — mirrors the iOS PagingView back chevron. It walks the
        // flow back one step at a time; from intro it pops the sign-up screen.
        IconButton(
            icon = "chevron.backward",
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = Dimens.horizontalPadding / 2, vertical = Dimens.headingTopPadding),
        ) {
            error = null
            when (step) {
                SignUpStep.VERIFICATION -> { code = ""; step = SignUpStep.CONTACT }
                SignUpStep.CONTACT -> { contact = ""; step = SignUpStep.INTRO }
                else -> onBack()
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (step) {
                SignUpStep.INTRO -> IntroStep(
                    onPickPhone = { isEmail = false; contact = ""; step = SignUpStep.CONTACT },
                    onPickEmail = { isEmail = true; contact = ""; step = SignUpStep.CONTACT },
                )

                SignUpStep.CONTACT -> ContactStep(
                    contact = contact,
                    onContactChange = { contact = it },
                    isEmail = isEmail,
                    phonePrefix = phonePrefix,
                    onPhonePrefixChange = { phonePrefix = it },
                    onContinue = ::sendCode,
                    onToggleMethod = { isEmail = !isEmail; contact = ""; error = null },
                )

                SignUpStep.VERIFICATION -> VerificationStep(
                    isEmail = isEmail,
                    code = code,
                    onCodeChange = { code = it },
                    onVerify = {
                        if (code.isBlank() && !authVerified) return@VerificationStep
                        error = null
                        step = SignUpStep.LOADING
                        scope.launch {
                            val target = fullContact()
                            // A previous attempt may have consumed the OTP and
                            // failed AFTER auth (restore/submit). Retry that
                            // part directly — the session already exists, and
                            // re-verifying a consumed code can only fail.
                            val err = if (authVerified) null
                            else if (isEmail) SupabaseController.verifyOtpEmail(target, code)
                            else SupabaseController.verifyOtpPhone(target, code)
                            if (err == null) {
                                if (!authVerified) {
                                    authVerified = true
                                    patchUserAfterAuth(userInfo, target, isEmail)
                                    // Email path only (iOS AllSignUpViewModel.swift:144-146):
                                    // domain-allowlist unlock + school-mode hydration,
                                    // fire-and-forget alongside the restore/submit flow.
                                    if (isEmail) {
                                        scope.launch {
                                            org.clear30.views.newuser.ReferralCodeHandler.handleEmail(userInfo)
                                        }
                                    }
                                }
                                // DB-level returning-user detection after EVERY verify
                                // (iOS checkIfReturningUser): a users row with non-empty
                                // content_info must RESTORE — even when the user came in
                                // through sign-up — or submitting a fresh assessment
                                // would clobber their server-side program state.
                                // Old-Android-app accounts (row present, EMPTY program
                                // state) fall through to fresh onboarding, keeping
                                // their users row/ID (§17-Q7). Stay on LOADING.
                                val submitError = when {
                                    checkIfReturningUser() -> restoreAccount(userInfo, program)
                                    signInOnly -> "No account found.\nPlease sign up first."
                                    else -> {
                                        val err = AssessmentSubmissionHandler.submitAssessment(userInfo, program, onboardingSetup)
                                        if (err == null) {
                                            // D2: an OLD-Android-app account (users row with
                                            // empty content_info) lands here — merge back its
                                            // server check-in history + on-device journals /
                                            // last-smoked after the fresh program is created.
                                            runCatching {
                                                org.clear30.data.OldAppMigrationHandler
                                                    .migrateAfterOnboarding(userInfo, program)
                                            }
                                        }
                                        err
                                    }
                                }
                                if (submitError != null) {
                                    error = submitError
                                    step = SignUpStep.VERIFICATION
                                } else {
                                    Clear30Store.save(userInfo)
                                    onComplete()
                                }
                            } else { error = err.message; step = SignUpStep.VERIFICATION }
                        }
                    },
                    onResend = ::sendCode,
                )

                SignUpStep.LOADING -> {
                    Spacer(Modifier.weight(1f))
                    CircularProgressIndicator(color = Clear30Colors.green)
                    Spacer(Modifier.weight(1f))
                }
            }

            error?.let {
                SmallText(
                    it,
                    Modifier.fillMaxWidth().padding(top = Dimens.cardSpacing),
                    color = Clear30Colors.red1,
                )
            }
        }
    }
}

/**
 * Intro step — leading Heading3 + dim subheading (SignUpView), a centered brand
 * glyph, then the two method affordances split by an "or" divider, exactly like
 * iOS's `signUpButtons` (Apple Sign In omitted — iOS-only).
 */
@Composable
private fun ColumnScope.IntroStep(
    onPickPhone: () -> Unit,
    onPickEmail: () -> Unit,
) {
    HeadingBlock(
        title = "Let's Get You In There!",
        subtitle = "Create your Clear30 account and get ready to improve your relationship with cannabis.",
    )

    Spacer(Modifier.weight(1f))

    BrandGlyph("person.fill.badge.plus")

    Spacer(Modifier.weight(1f))

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
    ) {
        MethodButton(text = "Continue with Phone Number", icon = "phone.fill", onClick = onPickPhone)
        OrDivider()
        MethodButton(text = "Continue with Email", icon = "envelope.fill", onClick = onPickEmail)
    }
}

/**
 * Contact step — the chosen-method entry, mirroring iOS PhoneEmailSheetView: a
 * phone region prefix (phone mode only) beside the OffWhite field, the gradient
 * "Let's Go!" CTA, the confidentiality + Terms of Use MiniText, and a toggle to
 * the other method.
 */
@Composable
private fun ColumnScope.ContactStep(
    contact: String,
    onContactChange: (String) -> Unit,
    isEmail: Boolean,
    phonePrefix: String,
    onPhonePrefixChange: (String) -> Unit,
    onContinue: () -> Unit,
    onToggleMethod: () -> Unit,
) {
    HeadingBlock(
        title = if (isEmail) "What's your email?" else "What's your number?",
        subtitle = "We'll ${if (isEmail) "email" else "text"} you a 6-digit code to verify it's really you.",
    )

    Spacer(Modifier.weight(1f))

    BrandGlyph(if (isEmail) "envelope.fill" else "phone.fill")

    Spacer(Modifier.weight(1f))

    Row(
        Modifier.fillMaxWidth().padding(bottom = Dimens.cardSpacing / 2),
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Region prefix — iOS PhoneNumberRegionView: a menu over the NANP
        // dialing codes; the selected prefix renders in a card-style chip (A3).
        if (!isEmail) {
            var prefixMenuOpen by remember { mutableStateOf(false) }
            Box {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(Dimens.cornerRadius))
                        .background(Clear30Colors.opacityGray)
                        .pressScale { prefixMenuOpen = true }
                        .padding(horizontal = Dimens.cardSpacing, vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    SmallText(phonePrefix)
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = prefixMenuOpen,
                    onDismissRequest = { prefixMenuOpen = false },
                ) {
                    PHONE_PREFIXES.sorted().forEach { prefix ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { SmallText(prefix) },
                            onClick = {
                                onPhonePrefixChange(prefix)
                                prefixMenuOpen = false
                            },
                        )
                    }
                }
            }
        }
        OffWhiteInput(
            value = contact,
            onValueChange = onContactChange,
            placeholder = if (isEmail) "Email" else "Phone Number",
            numsOnly = !isEmail,
            modifier = Modifier.weight(1f),
        )
    }

    TextIconButton(
        text = "Let's Go!",
        icon = "arrow.right",
        onClick = onContinue,
    )

    // Confidentiality + Terms of Use copy (iOS MiniTextWithLinks). Markdown link
    // rendering isn't wired here, so the Terms line is shown as plain copy.
    MiniText(
        "Your ${if (isEmail) "email" else "phone number"} authenticates you as a user.\n" +
            "It is confidential and will not be shared with anyone.\n" +
            "By continuing you agree to the Terms of Use.", // TODO(port): MiniTextWithLinks → clear30.org/terms-and-conditions
        Modifier
            .fillMaxWidth()
            .padding(top = Dimens.cardSpacing / 2),
        color = Clear30Colors.text.copy(alpha = 0.25f),
    )

    // "Use Email / Phone Number Instead" toggle (iOS TinyTextButton).
    TinyText(
        "Use ${if (isEmail) "Phone Number" else "Email"} Instead",
        Modifier
            .padding(top = Dimens.cardSpacing / 2)
            .pressScale(onClick = onToggleMethod),
        color = Clear30Colors.text.copy(alpha = 0.5f),
    )
}

/**
 * Verification step — 1:1 port of VerificationView: leading Heading3 + dim
 * subheading, centered gradient-tinted ellipsis.rectangle glyph, OffWhite code
 * field, a gradient "Verify" CTA with a checkmark, plus a resend affordance.
 */
@Composable
private fun ColumnScope.VerificationStep(
    isEmail: Boolean,
    code: String,
    onCodeChange: (String) -> Unit,
    onVerify: () -> Unit,
    onResend: () -> Unit,
) {
    HeadingBlock(
        title = "We sent you a code!",
        subtitle = "We've sent a 6 digit verification code to your ${if (isEmail) "email" else "phone"}. " +
            "Please enter it below.",
    )

    Spacer(Modifier.weight(1f))

    BrandGlyph("ellipsis.rectangle")

    Spacer(Modifier.weight(1f))

    OffWhiteInput(
        value = code,
        onValueChange = onCodeChange,
        placeholder = "Verification code",
        numsOnly = true,
        modifier = Modifier.padding(bottom = Dimens.cardSpacing / 2),
    )

    TextIconButton(
        text = "Verify",
        icon = "checkmark",
        onClick = onVerify,
    )

    // Resend affordance — re-triggers the same OTP send. Not a separate iOS view,
    // but a standard verification-screen need the single-field iOS flow implied.
    TinyText(
        "Didn't get it? Resend code",
        Modifier
            .padding(top = Dimens.cardSpacing / 2)
            .pressScale(onClick = onResend),
        color = Clear30Colors.text.copy(alpha = 0.5f),
    )
}

/**
 * HeadingBlock — leading Heading3 title + dim SmallText subheading, the shared
 * top-of-step header used by every iOS sign-up view.
 */
@Composable
private fun HeadingBlock(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth()) {
        Heading3(title, Modifier.padding(bottom = Dimens.cardSpacing / 2))
        SmallText(
            subtitle,
            Modifier.fillMaxWidth(),
            color = Clear30Colors.text.copy(alpha = 0.5f),
        )
    }
}

/**
 * BrandGlyph — centered SF symbol tinted with the brand gradient, matching the
 * `foregroundStyle(clear30Gradient)` icon iOS shows mid-screen on these steps.
 */
@Composable
private fun BrandGlyph(icon: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // TODO(port): swap for the imported clear30_logo brand SVG once available
        // in svg-import/ — iOS shows brand artwork (CustomizedFeedbackImage) here.
        GradientIcon(icon, Modifier.size(100.dp))
    }
}

/**
 * MethodButton — non-gradient OffWhite pill with a leading icon + centered label,
 * a 1:1 of iOS's `TextIconButton(gradient: nil)` used for the phone/email choices.
 */
@Composable
private fun MethodButton(text: String, icon: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(onClick = onClick)
            .clip(RoundedCornerShape(Dimens.cornerRadius))
            .background(Clear30Colors.opacityGray)
            .padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.cardSpacing),
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            sfSymbol(icon),
            contentDescription = null,
            tint = Clear30Colors.text,
            modifier = Modifier.size(16.dp),
        )
        SmallText(text)
    }
}

/**
 * OrDivider — capsule rule / "or" / capsule rule, a 1:1 of the iOS SignUpView
 * divider that separates the sign-up options.
 */
@Composable
private fun OrDivider() {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .clip(CircleShape)
                .background(Clear30Colors.opacityGray),
        )
        SmallText(
            "or",
            Modifier.padding(horizontal = Dimens.cardSpacing),
            color = Clear30Colors.text.copy(alpha = 0.5f),
        )
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .clip(CircleShape)
                .background(Clear30Colors.opacityGray),
        )
    }
}

/**
 * TextIconButton — 1:1 port of the iOS `TextIconButton` with the clear30 gradient:
 * a full-width gradient pill, centered white Lexend text + trailing white icon,
 * with the shared pressScale feedback.
 */
@Composable
private fun TextIconButton(text: String, icon: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(onClick = onClick)
            .clip(RoundedCornerShape(Dimens.cornerRadius))
            .background(Clear30Gradients.clear30)
            .padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.cardSpacing),
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SmallText(text, color = Color.White)
        Icon(
            sfSymbol(icon),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * GradientIcon — fills an SF symbol with the clear30 brand gradient, reproducing
 * iOS's `.foregroundStyle(clear30Gradient)` on the mid-screen glyphs. Uses the
 * same offscreen-compositing + SrcAtop overlay technique as `Loading.kt`.
 */
@Composable
private fun GradientIcon(icon: String, modifier: Modifier = Modifier, brush: Brush = Clear30Gradients.clear30) {
    Icon(
        sfSymbol(icon),
        contentDescription = null,
        tint = Color.White,
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(brush = brush, blendMode = BlendMode.SrcAtop)
            },
    )
}

/** Mirrors AllSignUpViewModel post-auth userInfo patch. */
private suspend fun patchUserAfterAuth(userInfo: UserInfo, contact: String, isEmail: Boolean) {
    userInfo.signUpID = contact
    userInfo._signUpType = if (isEmail) SignUpType.EMAIL else SignUpType.PHONE
    userInfo._userID = SupabaseController.getUserAuthID()
    userInfo.signUpReturning = false
    // iOS logs `signed_up` for FRESH signups in saveUserInfo (returning ones
    // log in restoreAccount) — Android only logged the restore path.
    org.clear30.data.Logger.logEvent(
        userInfo.loggingID,
        org.clear30.data.LogEventType.signedUp,
        mapOf(
            org.clear30.data.LogEventExtraDataType.TYPE to userInfo.signUpType.name.lowercase(),
            org.clear30.data.LogEventExtraDataType.RETURNING to "false",
        ),
    )
    // createUser RPC + assessment submission now run in AssessmentSubmissionHandler
    // (invoked from the verify path). The real users.id replaces this auth-id there.
    // TODO(port): program message fetch / normative feedback + break creation.
}

/**
 * Returning-user check — iOS AllSignUpViewModel.checkIfReturningUser: the verified
 * contact counts as an existing account only when its `users` row exists AND has a
 * non-empty `content_info` (enough program state to restore). Rows with empty
 * program state — notably the old Android app's ~3k users — are treated as new.
 */
private suspend fun checkIfReturningUser(): Boolean {
    val userID = SupabaseController.getUserID()?.takeIf { it.isNotEmpty() } ?: return false
    val userData = SupabaseController.fetchUserData(userID)
    return !userData?.content_info.isNullOrEmpty()
}

/**
 * Sign-in restore — iOS AllSignUpViewModel.restoreAccount: resolve users.id, pull
 * the whole program state back (breaks, timeline, check-ins), then identify the
 * user with RevenueCat and log the returning sign-in. Returns an error, or null.
 */
private suspend fun restoreAccount(userInfo: UserInfo, program: Program): String? {
    val userID = SupabaseController.getUserID()?.takeIf { it.isNotEmpty() }
        ?: return "No account found.\nPlease sign up first."
    val restoreError = org.clear30.data.ProgramRestoreHandler.restoreUserData(userID, userInfo, program)
    if (restoreError != null) return "Could not restore account.\n$restoreError"

    userInfo._userID = userID
    userInfo.signUpReturning = true

    org.clear30.data.PaywallController.signIn(
        userInfo,
        org.clear30.data.PaywallController.getUserParams(userInfo),
    )
    org.clear30.data.Logger.logEvent(
        userInfo.loggingID,
        org.clear30.data.LogEventType.signedUp,
        mapOf(
            org.clear30.data.LogEventExtraDataType.TYPE to userInfo.signUpType.name.lowercase(),
            org.clear30.data.LogEventExtraDataType.RETURNING to "true",
            org.clear30.data.LogEventExtraDataType.USER_ID to userID,
        ),
    )
    return null
}

/** iOS `PhoneNumberRegionView.prefixes` — the NANP dialing codes offered. */
private val PHONE_PREFIXES = listOf(
    "+1", "+1264", "+1268", "+1242", "+1246", "+1345", "+1767", "+1441",
    "+1849", "+1809", "+1473", "+1876", "+1658", "+1664", "+1869", "+1787",
    "+1758", "+1784", "+1868", "+1649", "+1340", "+1284",
)
