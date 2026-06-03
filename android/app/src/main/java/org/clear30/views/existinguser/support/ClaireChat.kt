package org.clear30.views.existinguser.support

import androidx.compose.runtime.Composable
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.sendClaireMessage
import org.clear30.data.supabase.sendDrFredMessage
import org.clear30.views.components.AiChatScreen
import org.clear30.views.theme.Clear30Gradients

/** Claire AI coach chat — delegates to the shared [AiChatScreen]. */
@Composable
fun ClaireChat(onBack: () -> Unit) = AiChatScreen(
    title = "Claire",
    greeting = "Hey, I'm Claire. How are you feeling today?",
    userBubble = Clear30Gradients.claire,
    onBack = onBack,
    send = { history -> SupabaseController.sendClaireMessage(history) },
)

/** Dr Fred consult chat — same UI, different backend function. */
@Composable
fun DrFredChat(onBack: () -> Unit) = AiChatScreen(
    title = "Dr. Fred",
    greeting = "Hi, I'm Dr. Fred. What would you like to talk through?",
    userBubble = Clear30Gradients.clear30,
    onBack = onBack,
    send = { history -> SupabaseController.sendDrFredMessage(history) },
)
