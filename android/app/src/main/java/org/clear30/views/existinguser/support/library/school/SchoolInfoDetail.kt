package org.clear30.views.existinguser.support

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import org.clear30.views.components.Heading1
import org.clear30.views.components.Heading2
import org.clear30.views.components.IconButton
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.pressScale
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Dimens

/**
 * Detail page shared by school activities and school resources — ported from
 * SchoolInfoDetail.swift. Sections appear only when their field is present:
 * date (+ "Repeats weekly"), location, the "Basics"/"More info" link block, and
 * the description. All links open EXTERNALLY (dialer / mail app / browser),
 * matching iOS `UIApplication.shared.open`.
 */
@Composable
internal fun SchoolInfoDetailScreen(
    title: String,
    gradient: Brush,
    orgName: String? = null,
    subtitle: String? = null,
    date: Instant? = null,
    repeats: String? = null,
    location: String? = null,
    phoneNumber: String? = null,
    email: String? = null,
    link: String? = null,
    facilitatedBy: String? = null,
    subLinks: Map<String, String>? = null,
    description: String? = null,
    badge: String? = null,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    fun open(intent: Intent) = runCatching { context.startActivity(intent) }
    fun openUrl(url: String) = open(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    // iOS `sectionSpacing` (20pt) between the info blocks.
    val sectionSpacing = Dimens.cardSpacing * 1.5f

    Column(
        Modifier.fillMaxSize()
            .padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = Dimens.headingTopPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton("chevron.backward", onClick = onBack)
            Spacer(Modifier.weight(1f))
            badge?.let {
                TinyText(it, color = Clear30Colors.text.copy(alpha = 0.5f))
            }
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(top = Dimens.headingTopPadding),
        ) {
            orgName?.let {
                TinyText(it, color = Clear30Colors.text.copy(alpha = 0.75f))
                Spacer(Modifier.height(3.dp)) // iOS literal: .padding(.bottom, 3)
            }

            Heading1(title)
            subtitle?.let { Heading2(it) }
            Spacer(Modifier.height(sectionSpacing))

            // Date (+ repeats-weekly note)
            date?.let {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                    // iOS "calendar.badge.clock" — closest mapped Material glyph.
                    SchoolGradientIcon(sfSymbol("calendar"), gradient, 19.dp, Modifier.padding(top = 4.dp))
                    Column {
                        SmallText(it.activityDateString())
                        if (repeats == "weekly") {
                            TinyText("Repeats weekly", color = Clear30Colors.text.copy(alpha = 0.5f))
                        }
                    }
                }
                Spacer(Modifier.height(sectionSpacing))
            }

            // Location
            location?.let {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                    SchoolGradientIcon(Icons.Rounded.LocationOn, gradient, 20.dp, Modifier.padding(top = 2.dp))
                    SmallText(it)
                }
                Spacer(Modifier.height(sectionSpacing))
            }

            // Info block — Basics (phone / email / link) + More info (facilitator / sub-links)
            if (phoneNumber != null || email != null || link != null || subLinks != null || facilitatedBy != null) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                    SchoolGradientIcon(sfSymbol("info.circle"), gradient, 20.dp)
                    Column {
                        TinyText("Basics", color = Clear30Colors.text.copy(alpha = 0.5f))

                        phoneNumber?.let { number ->
                            SchoolInfoDetailButton(sfSymbol("phone"), number) {
                                open(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
                            }
                        }
                        email?.let { address ->
                            SchoolInfoDetailButton(sfSymbol("envelope"), address) {
                                open(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address")))
                            }
                        }
                        link?.let { url ->
                            SchoolInfoDetailButton(Icons.Rounded.Link, shortenLink(url)) { openUrl(url) }
                        }

                        if (subLinks != null || facilitatedBy != null) {
                            Spacer(Modifier.height(10.dp)) // iOS literal: .padding(.top, 10)
                            TinyText("More info", color = Clear30Colors.text.copy(alpha = 0.5f))
                            facilitatedBy?.let {
                                SchoolInfoDetailButton(sfSymbol("person"), it, enabled = false)
                            }
                            subLinks?.forEach { (label, url) ->
                                SchoolInfoDetailButton(Icons.Rounded.Link, label) { openUrl(url) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(sectionSpacing))
            }

            // Description
            description?.let {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                    SchoolGradientIcon(sfSymbol("note.text"), gradient, 20.dp)
                    SmallText(it)
                }
                Spacer(Modifier.height(sectionSpacing))
            }
        }
    }
}

/** One tappable info row (iOS `SchoolInfoDetailButton`): dimmed 16pt icon + text. */
@Composable
private fun SchoolInfoDetailButton(
    image: ImageVector,
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    Row(
        Modifier
            .then(if (enabled) Modifier.pressScale(onClick = onClick) else Modifier)
            .padding(bottom = Dimens.cardSpacing / 3),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(11.dp), // iOS literal spacing: 11
    ) {
        Icon(
            image,
            contentDescription = null,
            tint = Clear30Colors.text.copy(alpha = 0.75f),
            modifier = Modifier.padding(top = 4.dp).size(16.dp),
        )
        SmallText(text)
    }
}

/** iOS `shortenLink` — a URL's host, or the raw string when unparsable. */
internal fun shortenLink(link: String): String =
    runCatching { Uri.parse(link).host }.getOrNull() ?: link
