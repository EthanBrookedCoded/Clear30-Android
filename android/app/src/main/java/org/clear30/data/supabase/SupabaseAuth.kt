package org.clear30.data.supabase

import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.OTP
import org.clear30.data.supabase.SupabaseController.toError

/**
 * Supabase Auth (phone/email OTP) — ported from the auth methods in
 * SupabaseFunctions.swift (`signInWithOTP` / `verifyOTP`).
 *
 * Uses supabase-kt Auth. "Sign in with Apple" is iOS-only; the Android sign-up
 * offers phone/email OTP (a Google provider can be added in the auth-views
 * follow-up). API shapes may need minor reconciliation with the installed
 * supabase-kt version.
 */

suspend fun SupabaseController.signInWithOtpPhone(phoneNumber: String): SupabaseFunctionError? =
    runCatching { client.auth.signInWith(OTP) { phone = phoneNumber } }.fold({ null }, { it.toError() })

suspend fun SupabaseController.signInWithOtpEmail(email: String): SupabaseFunctionError? =
    runCatching { client.auth.signInWith(OTP) { this.email = email } }.fold({ null }, { it.toError() })

suspend fun SupabaseController.verifyOtpPhone(phoneNumber: String, token: String): SupabaseFunctionError? =
    runCatching { client.auth.verifyPhoneOtp(type = OtpType.Phone.SMS, phone = phoneNumber, token = token) }
        .fold({ null }, { it.toError() })

suspend fun SupabaseController.verifyOtpEmail(email: String, token: String): SupabaseFunctionError? =
    runCatching { client.auth.verifyEmailOtp(type = OtpType.Email.EMAIL, email = email, token = token) }
        .fold({ null }, { it.toError() })
