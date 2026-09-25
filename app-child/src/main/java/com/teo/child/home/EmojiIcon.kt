package com.teo.child.home

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A colorful Twemoji-derived glyph bundled into the app, instead of a raw Unicode emoji
 *  character — those render inconsistently (and often poorly) across OEM emoji fonts, Samsung's
 *  in particular; this looks identical on every device. */
@Composable
fun EmojiIcon(@DrawableRes id: Int, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id),
        contentDescription = null,
        modifier = modifier.size(size)
    )
}
