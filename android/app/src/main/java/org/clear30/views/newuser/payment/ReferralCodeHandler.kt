package org.clear30.views.newuser

import org.clear30.data.AlertHandler
import org.clear30.data.Clear30Store
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.checkEmail
import org.clear30.data.supabase.getAssessmentStatus
import org.clear30.data.supabase.getSchoolData

/**
 * ReferralCodeHandler — ported from ReferralCodeHandler.swift (`handleEmail`):
 * after an email OTP verify, check the email's domain against the backend
 * allowlist (`payment_check_email_json`). A match unlocks the app for free
 * (`freeCode = org`) and, when the domain maps to a school, hydrates school
 * mode: `schoolId`, the mid-pilot completion flag, and the school content
 * bundle. Fire-and-forget — an unmatched domain RAISEs server-side and
 * surfaces here as a null result.
 */
object ReferralCodeHandler {

    suspend fun handleEmail(userInfo: UserInfo) {
        val result = SupabaseController.checkEmail() ?: return

        userInfo.freeCode = result.org

        result.school_id?.let { schoolID ->
            userInfo.schoolId = schoolID
            // Rehydrate the mid-pilot flag so a re-signed-in school user isn't
            // re-prompted (iOS ReferralCodeHandler.swift:71-80).
            SupabaseController.getAssessmentStatus()?.let {
                userInfo.midPilotAssessmentCompleted = it.mid_pilot_completed
            }
            SupabaseController.getSchoolData(schoolID)?.let { userInfo.schoolData = it }
        }

        AlertHandler.info("Free 🤩", "${result.org} has gifted you Clear30 for free!!")
        runCatching { Clear30Store.save(userInfo) }
    }
}
