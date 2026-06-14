package org.clear30.views.existinguser.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import org.clear30.data.LogEventExtraDataType
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.SymptomInfo
import org.clear30.data.model.UserInfo
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Heading1
import org.clear30.views.components.Heading3
import org.clear30.views.components.IconButton
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * SymptomDetailScreen — ported from the iOS Symptom Detail view. Surfaces the
 * symptom's tips (heading + body + optional example), the Reddit threads the
 * server pre-curated as relevant, and the journal prompts framed around that
 * symptom. Each Reddit row fires the system intent (resolves to the Reddit app
 * if installed, browser otherwise) and is logged as `openedRedditThread` so we
 * can correlate symptom selection to engagement.
 */
@Composable
fun SymptomDetailScreen(
    key: String,
    symptom: SymptomInfo,
    userInfo: UserInfo,
    onBack: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(key) {
        Logger.logEvent(
            userInfo.loggingID,
            LogEventType.loggedSymptom,
            mapOf(LogEventExtraDataType.SYMPTOM to key),
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            IconButton("chevron.backward", onClick = onBack)
            Column {
                Heading1(key.replaceFirstChar { it.uppercase() })
            }
        }

        // Hero — symptom's brand gradient + an empathetic line. We don't have
        // a server-side description per symptom, so we render a generic "you
        // are not alone" beat instead of a placeholder string.
        Clear30Card(modifier = Modifier.fillMaxWidth(), gradient = symptom.getGradient()) {
                SmallText(
                    "Lots of people feel this. Here's what's worked for others — pick what fits.",
                    color = Color.White,
                )
            }

        if (symptom.tips.isNotEmpty()) {
            Heading3("Quick tips")
            symptom.tips.forEachIndexed { i, tip ->
                Clear30Card(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Heading3(tip.title)
                            tip.sections.forEach { section ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    SmallText(section.heading, color = Clear30Colors.text)
                                    SmallText(section.content, color = Clear30Colors.text.copy(alpha = 0.75f))
                                    section.example?.let { ex ->
                                        TinyText("Example: $ex", color = Clear30Colors.text.copy(alpha = 0.5f))
                                    }
                                }
                            }
                        }
                    }
            }
        }

        if (symptom.reddits.isNotEmpty()) {
            Heading3("Stories on Reddit")
            symptom.reddits.entries.forEachIndexed { i, (title, url) ->
                Clear30Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            Logger.logEvent(
                                userInfo.loggingID,
                                LogEventType.openedRedditThread,
                                mapOf(LogEventExtraDataType.URL to url),
                            )
                            uriHandler.openUri(url)
                        },
                        gradient = Clear30Gradients.reddit,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                            Icon(sfSymbol("person.3"), contentDescription = null, tint = Color.White)
                            Column(Modifier.weight(1f)) {
                                SmallText(title, color = Color.White)
                                TinyText(url, color = Color.White.copy(alpha = 0.5f), maxLines = 1)
                            }
                            Icon(sfSymbol("arrow.up.right"), contentDescription = null, tint = Color.White)
                        }
                    }
            }
        }

        if (symptom.prompts.isNotEmpty()) {
            Heading3("Journal prompts")
            symptom.prompts.entries.forEachIndexed { i, (title, body) ->
                Clear30Card(modifier = Modifier.fillMaxWidth(), gradient = Clear30Gradients.journals) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Heading3(title, color = Color.White)
                            SmallText(body, color = Color.White.copy(alpha = 0.75f))
                        }
                    }
            }
        }

        Spacer(Modifier.padding(top = Dimens.cardSpacing))
    }
}

