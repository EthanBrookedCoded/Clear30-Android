package org.clear30.views.existinguser.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import kotlinx.coroutines.launch
import org.clear30.data.Clear30Store
import org.clear30.data.LogEventExtraDataType
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramMessage
import org.clear30.data.model.ProgramResource
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
 * MessageDetail — opens a single [ProgramMessage] full-bleed. Renders the body,
 * the page-info pages (intro / how-to / why-it-matters style — flattened to a
 * vertical stack rather than the iOS pager since vertical reading is the
 * dominant Android idiom), and any Reddit/YouTube/Meditation/Claire-prompt
 * cross-links from `allResources` — each opens in the system browser.
 *
 * Marks the message visited on first open and fires `openedMessage`.
 */
@Composable
fun MessageDetail(
    program: Program,
    message: ProgramMessage,
    userInfo: UserInfo,
    onBack: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(message) {
        if (!message.visited) {
            message.visited = true
            scope.launch { Clear30Store.save(program) }
        }
        Logger.logEvent(
            userInfo.loggingID,
            LogEventType.openedMessage,
            mapOf(LogEventExtraDataType.MESSAGE_TITLE to message.title),
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            IconButton("chevron.backward", onClick = onBack)
            Heading1(message.topicTitle)
        }
        if (message.subtitle.isNotBlank()) {
            SmallText(message.subtitle, color = Clear30Colors.text.copy(alpha = 0.5f))
        }
        if (message.message.isNotBlank()) {
            Clear30Card(modifier = Modifier.fillMaxWidth()) { SmallText(message.message) }
        }

        // Page info — collapsed to a vertical sequence of cards (iOS used a
        // horizontal pager; vertical is the better fit for read-then-scroll
        // on Android).
        message.programPageInfo.forEach { page ->
            Clear30Card(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4)) {
                    Heading3(page.title)
                    SmallText(page.body)
                }
            }
        }

        if (message.hasReddits) {
            Heading3("Stories on Reddit")
            message.reddits.forEach { res -> LinkRow(res, uriHandler, userInfo, LogEventType.openedRedditThread) }
        }
        if (message.hasYouTubes) {
            Heading3("Watch")
            message.youTubes.forEach { res -> LinkRow(res, uriHandler, userInfo, LogEventType.openedYouTubeVideo) }
        }
        message.meditation?.let { med ->
            Heading3("Meditation")
            Clear30Card(
                modifier = Modifier.fillMaxWidth().clickable {
                    Logger.logEvent(
                        userInfo.loggingID,
                        LogEventType.listenedToMeditation,
                        mapOf(LogEventExtraDataType.MEDITATION_NAME to med.name),
                    )
                    uriHandler.openUri(med.url)
                },
                gradient = Clear30Gradients.meditation,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                    Icon(sfSymbol("play.fill"), contentDescription = null)
                    SmallText(med.name)
                }
            }
        }
        if (message.hasOnlyResources) {
            Heading3("Resources")
            message.onlyResources.forEach { res -> LinkRow(res, uriHandler, userInfo, LogEventType.openedContent) }
        }
    }
}

@Composable
private fun LinkRow(
    res: ProgramResource,
    uri: androidx.compose.ui.platform.UriHandler,
    userInfo: UserInfo,
    event: LogEventType,
) {
    Clear30Card(modifier = Modifier.fillMaxWidth().clickable {
        Logger.logEvent(
            userInfo.loggingID, event,
            mapOf(LogEventExtraDataType.URL to res.url),
        )
        uri.openUri(res.url)
    }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            Icon(sfSymbol("arrow.up.right"), contentDescription = null)
            Column(Modifier.weight(1f)) {
                SmallText(res.title)
                TinyText(res.url, color = Clear30Colors.text.copy(alpha = 0.5f))
            }
        }
    }
}
