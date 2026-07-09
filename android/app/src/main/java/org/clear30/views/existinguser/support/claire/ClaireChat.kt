package org.clear30.views.existinguser.support

import androidx.compose.runtime.Composable
import org.clear30.data.model.Program
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.getClaireMessages
import org.clear30.data.supabase.getOrCreateClaireThread
import org.clear30.data.supabase.sendClaireMessage
import org.clear30.views.components.AiChatScreen
import org.clear30.views.theme.Clear30Gradients

/** Claire AI coach chat — delegates to the shared [AiChatScreen]. [initialInput]
 *  pre-fills the composer when opened from a Claire prompt card / feeling emoji.
 *  Each send creates/reuses the user's Claire thread, then streams her reply. */
@Composable
fun ClaireChat(onBack: () -> Unit, userInfo: UserInfo, program: Program, initialInput: String = "") = AiChatScreen(
    title = "Claire",
    greeting = "Hey, I'm Claire. How are you feeling today?",
    userBubble = Clear30Gradients.claire,
    onBack = onBack,
    send = { history ->
        val msg = history.lastOrNull { it.role == "user" }?.content
        val thread = if (!msg.isNullOrBlank()) SupabaseController.getOrCreateClaireThread(userInfo, program) else null
        if (!msg.isNullOrBlank() && thread != null) SupabaseController.sendClaireMessage(msg, thread) else null
    },
    initialInput = initialInput,
    loadHistory = { SupabaseController.getClaireMessages(userInfo.userID) },
)
// DrFredChat moved to drfred/DrFredChat.kt — Dr. Fred is a persistent two-way
// `comms.dr_fred` table thread, not a request/response AI chat like Claire.
