package org.clear30.views.existinguser.support

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.clear30.R
import org.clear30.data.Clear30Store
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.RemoteFeedbackConfig
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.submitTextFeedback
import org.clear30.views.components.Heading2
import org.clear30.views.components.IconButton
import org.clear30.views.components.SmallText
import org.clear30.views.components.TextIconButton
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * FeedbackScreen — the "Feedback Monster" target. With a [config] it is the iOS
 * GenericSubmitFeedback (experiment-driven placeholder + either an external link
 * button or the free-form composer, typed submission, `feedback_<type>` cache);
 * without one it is the iOS SubmitFeedback default (free-form, type "text").
 */
@Composable
fun FeedbackScreen(
    userInfo: UserInfo,
    config: RemoteFeedbackConfig? = null,
    forceFreeForm: Boolean = false,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val feedbackType = config?.type ?: "text"

    fun markSubmitted() {
        userInfo.setCacheBool("feedback_$feedbackType", true)
        if (feedbackType == "text") userInfo.gaveFeedback = true // legacy monster flag
        scope.launch { Clear30Store.save(userInfo) }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            IconButton("chevron.backward", onClick = onBack)
            Heading2("Send Feedback")
        }

        if (sent) {
            Column(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(painterResource(R.drawable.feedback_monster_full), null, Modifier.height(120.dp), contentScale = ContentScale.Fit)
                Spacer(Modifier.height(Dimens.cardSpacing))
                Heading2("Thanks! 💛")
                SmallText("The Feedback Monster loves you.", color = Clear30Colors.text.copy(alpha = 0.5f))
            }
        } else {
            Column(Modifier.weight(1f).fillMaxWidth().padding(top = Dimens.cardSpacing)) {
                // Placeholder — remote config image/emoji + copy when present
                // (iOS FeedbackPlaceholder), else the default monster intro.
                if (config != null) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = Dimens.cardSpacing),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val imageRes = config.image?.let { feedbackDrawableByName(context, it) } ?: 0
                        if (imageRes != 0) {
                            Image(painterResource(imageRes), null, Modifier.height(120.dp), contentScale = ContentScale.Fit)
                        } else if (config.emoji.isNotBlank()) {
                            org.clear30.views.components.HugeText(config.emoji)
                        }
                        Spacer(Modifier.height(Dimens.cardSpacing))
                        Heading2(config.title, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        SmallText(
                            config.subtitle,
                            color = Clear30Colors.text.copy(alpha = 0.5f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                        Image(painterResource(R.drawable.feedback_monster), null, Modifier.height(64.dp), contentScale = ContentScale.Fit)
                        SmallText(
                            "Tell us anything — bugs, ideas, or love. We read every message.",
                            color = Clear30Colors.text.copy(alpha = 0.5f),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (config?.link == null || forceFreeForm) {
                    Spacer(Modifier.height(Dimens.cardSpacing))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text("What's on your mind?") },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        enabled = !sending,
                    )
                }
            }
            val link = config?.link
            if (link != null && !forceFreeForm) {
                // Link mode (iOS GenericSubmitFeedback.openUrl): _USERID_ substitution,
                // or the id appended as ?user_id=.
                TextIconButton(config.linkButtonText ?: "Submit Feedback", icon = "link", gradient = Clear30Gradients.clear30) {
                    val url = if (link.contains(USER_ID_PLACEHOLDER)) {
                        link.replace(USER_ID_PLACEHOLDER, userInfo.userID)
                    } else {
                        "$link?user_id=${userInfo.userID}"
                    }
                    runCatching {
                        context.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)),
                        )
                    }
                    Logger.logEvent(userInfo.loggingID, LogEventType.startedSubmitFeedback)
                    markSubmitted()
                    onBack()
                }
            } else {
                // iOS sends through ChatComposeMessageView's arrow button; here the
                // composer is a plain field, so the send control is the standard
                // TextIconButton (same arrow.up glyph as the Claire composer).
                TextIconButton(
                    text = if (sending) "Sending…" else "Send",
                    icon = "arrow.up",
                    gradient = Clear30Gradients.clear30,
                ) {
                    val t = text.trim()
                    if (t.isEmpty() || sending) return@TextIconButton
                    sending = true
                    error = null
                    scope.launch {
                        val err = SupabaseController.submitTextFeedback(userInfo.userID, t, feedbackType)
                        sending = false
                        if (err == null) {
                            Logger.logEvent(userInfo.loggingID, LogEventType.submittedFeedback)
                            markSubmitted()
                            sent = true
                        } else {
                            error = "Couldn't send: ${err.message}"
                        }
                    }
                }
            }
            error?.let {
                Spacer(Modifier.height(Dimens.cardSpacing / 2))
                SmallText(it, color = Clear30Colors.red1)
            }
        }
    }
}

private const val USER_ID_PLACEHOLDER = "_USERID_"

/** iOS imageset name → Android drawable id ("Feedback Monster Full" → feedback_monster_full). */
private fun feedbackDrawableByName(context: android.content.Context, name: String): Int {
    val normalized = name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
    @Suppress("DiscouragedApi")
    return context.resources.getIdentifier(normalized, "drawable", context.packageName)
}
