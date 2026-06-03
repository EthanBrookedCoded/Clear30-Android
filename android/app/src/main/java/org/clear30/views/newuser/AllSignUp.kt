package org.clear30.views.newuser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.clear30.data.Clear30Store
import org.clear30.data.model.SignUpType
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.getUserAuthID
import org.clear30.data.supabase.signInWithOtpEmail
import org.clear30.data.supabase.signInWithOtpPhone
import org.clear30.data.supabase.verifyOtpEmail
import org.clear30.data.supabase.verifyOtpPhone
import org.clear30.views.components.DefaultButton
import org.clear30.views.components.Heading1
import org.clear30.views.components.SmallText
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

private enum class SignUpStep { INTRO, VERIFICATION, LOADING }

/**
 * AllSignUp — ported from AllSignUp.swift + AllSignUpViewModel.swift. Phone/email
 * OTP sign-up via Supabase Auth: enter contact -> verify code -> create account.
 *
 * Apple sign-in is iOS-only; Android offers phone/email OTP (a Google provider
 * can be added later). Account-creation side effects beyond the auth + userInfo
 * patch (createUser RPC details) are a marked TODO.
 */
@Composable
fun AllSignUp(
    userInfo: UserInfo,
    scope: CoroutineScope,
    onComplete: () -> Unit,
    onBack: () -> Unit,
) {
    var step by remember { mutableStateOf(SignUpStep.INTRO) }
    var contact by remember { mutableStateOf("") }
    var isEmail by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().padding(Dimens.horizontalPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (step) {
            SignUpStep.INTRO -> {
                Heading1("Create your account")
                OutlinedTextField(
                    value = contact,
                    onValueChange = { contact = it; isEmail = it.contains("@") },
                    label = { Text("Phone number or email") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.cardSpacing),
                )
                DefaultButton("Continue", gradient = Clear30Gradients.clear30, modifier = Modifier.fillMaxWidth()) {
                    if (contact.isBlank()) return@DefaultButton
                    step = SignUpStep.LOADING
                    scope.launch {
                        val err = if (isEmail) SupabaseController.signInWithOtpEmail(contact)
                        else SupabaseController.signInWithOtpPhone(contact)
                        if (err == null) step = SignUpStep.VERIFICATION
                        else { error = err.message; step = SignUpStep.INTRO }
                    }
                }
            }

            SignUpStep.VERIFICATION -> {
                Heading1("Enter the code")
                SmallText("Sent to $contact", Modifier.padding(top = 4.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Verification code") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.cardSpacing),
                )
                DefaultButton("Verify", gradient = Clear30Gradients.clear30, modifier = Modifier.fillMaxWidth()) {
                    step = SignUpStep.LOADING
                    scope.launch {
                        val err = if (isEmail) SupabaseController.verifyOtpEmail(contact, code)
                        else SupabaseController.verifyOtpPhone(contact, code)
                        if (err == null) {
                            patchUserAfterAuth(userInfo, contact, isEmail)
                            Clear30Store.save(userInfo)
                            onComplete()
                        } else { error = err.message; step = SignUpStep.VERIFICATION }
                    }
                }
            }

            SignUpStep.LOADING -> CircularProgressIndicator()
        }

        error?.let { SmallText(it, Modifier.padding(top = Dimens.cardSpacing)) }
    }
}

/** Mirrors AllSignUpViewModel post-auth userInfo patch. */
private suspend fun patchUserAfterAuth(userInfo: UserInfo, contact: String, isEmail: Boolean) {
    userInfo.signUpID = contact
    userInfo._signUpType = if (isEmail) SignUpType.EMAIL else SignUpType.PHONE
    userInfo._userID = SupabaseController.getUserAuthID()
    // TODO(port): createUser RPC + program message fetch (AssessmentSubmissionHandler)
}
