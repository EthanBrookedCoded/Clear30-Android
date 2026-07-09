package org.clear30.views.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.clear30.views.theme.Clear30Colors

/**
 * IconLogo — the app icon, rounded (radius = maxHeight / 4) with a soft shadow.
 * Matches IconLogo.swift.
 *
 * iOS reads the bundle app icon (`Image(.icon)`); on Android the caller passes the
 * drawable/mipmap so this stays compile-safe before launcher icons are wired
 * (TODO §0). Use `R.mipmap.ic_launcher` or the brand `clear30_logo` once imported.
 */
@Composable
fun IconLogo(
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 150.dp,
    shadowColor: Color = Clear30Colors.shadow,
) {
    val shape = RoundedCornerShape(maxHeight / 4)
    Image(
        painter = painterResource(iconRes),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .heightIn(max = maxHeight)
            .shadow(10.dp, shape, ambientColor = shadowColor, spotColor = shadowColor)
            .clip(shape),
    )
}
