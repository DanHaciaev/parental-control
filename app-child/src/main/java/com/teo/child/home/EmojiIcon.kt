package com.teo.child.home

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.teo.core.ui.components.NestIcon
import com.teo.core.ui.theme.NestColors

/** Line icon from the shared A3 set (core nest_ic_*), navy by default. Kept under its old name so
 *  existing call sites didn't all have to change when the emoji artwork was replaced. */
@Composable
fun EmojiIcon(@DrawableRes id: Int, size: Dp = 22.dp, modifier: Modifier = Modifier, tint: Color = NestColors.Navy) {
    NestIcon(id, modifier = modifier, tint = tint, size = size)
}
