package org.clear30.views.existinguser.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.clear30.Clear30Application
import org.clear30.data.Clear30Store
import org.clear30.data.model.Program
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.getClaireMessages
import org.clear30.data.supabase.getOrCreateClaireThread
import org.clear30.data.supabase.sendClaireMessage
import org.clear30.views.components.AiChatScreen
import org.clear30.views.components.DefaultText
import org.clear30.views.components.Heading1
import org.clear30.views.components.Heading3
import org.clear30.views.components.IconButton
import org.clear30.views.components.SmallText
import org.clear30.views.components.TextIconButton
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import kotlinx.coroutines.launch

/** Claire AI coach chat. First open shows the one-time consent screen (iOS
 *  `AllClaire.agreement`, gated on `userInfo.agreedToClaire`); after Agree it
 *  delegates to the shared [AiChatScreen]. [initialInput] pre-fills the composer
 *  when opened from a Claire prompt card / feeling emoji. */
@Composable
fun ClaireChat(onBack: () -> Unit, userInfo: UserInfo, program: Program, initialInput: String = "") {
    var agreed by remember { mutableStateOf(userInfo.agreedToClaire == true) }
    if (!agreed) {
        ClaireAgreement(
            userInfo = userInfo,
            onBack = onBack,
            onAgree = {
                userInfo.agreedToClaire = true
                agreed = true
                Clear30Application.appScope.launch { Clear30Store.save(userInfo) }
            },
        )
        return
    }
    ClaireChatCore(onBack, userInfo, program, initialInput)
}

@Composable
private fun ClaireChatCore(onBack: () -> Unit, userInfo: UserInfo, program: Program, initialInput: String) = AiChatScreen(
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

/** One-time Claire consent screen — port of iOS `AllClaire.agreement`. */
@Composable
private fun ClaireAgreement(userInfo: UserInfo, onBack: () -> Unit, onAgree: () -> Unit) {
    Column(
        Modifier.fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = Dimens.horizontalPadding),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = Dimens.headingTopPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton("chevron.backward", onClick = onBack)
            Spacer(Modifier.width(Dimens.cardSpacing / 2))
            Heading1("Claire")
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        ) {
            Heading3("Hey ${userInfo.name}!", Modifier.padding(bottom = Dimens.cardSpacing / 2))
            DefaultText("Meet Claire ✨", Modifier.padding(bottom = Dimens.cardSpacing))
            SmallText(
                "Claire is your AI weed-break expert, here to support you through Clear30. She can " +
                    "answer questions, guide you through challenges, and help you stay on track during " +
                    "your break.\n\n" +
                    "✅ Great for: Tips, strategies, and Clear30 support.\n\n" +
                    "❗ Important: Claire is an AI, not a human. Don't use Claire for emergencies or " +
                    "emotional crises. If you need immediate help, contact 988 for urgent support.",
                color = Clear30Colors.text.copy(alpha = 0.75f),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = Dimens.cardSpacing),
            horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
        ) {
            TextIconButton("Back", icon = "xmark", modifier = Modifier.weight(1f), onClick = onBack)
            TextIconButton(
                "Agree",
                icon = "checkmark",
                gradient = Clear30Gradients.claire,
                modifier = Modifier.weight(1f),
                onClick = onAgree,
            )
        }
    }
}
