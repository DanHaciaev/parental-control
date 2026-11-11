package com.teo.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardElevation
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.teo.core.ui.theme.NestColors

/**
 * Drop-in replacement for Material3's Card, same parameters, drawn the A3 way: white (or the
 * passed container colour), no border unless one is passed, soft navy-tinted shadow instead of
 * Material's flat tonal elevation. Screens import it as `Card`
 * (`import com.teo.core.ui.components.NestStyledCard as Card`), so existing call sites restyle
 * without being rewritten. [elevation] is accepted for signature compatibility and ignored.
 */
@Composable
fun NestStyledCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    colors: CardColors? = null,
    elevation: CardElevation? = null,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
) = StyledCardImpl(null, true, modifier, shape, colors, border, content)

@Composable
fun NestStyledCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(22.dp),
    colors: CardColors? = null,
    elevation: CardElevation? = null,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
) = StyledCardImpl(onClick, enabled, modifier, shape, colors, border, content)

@Composable
private fun StyledCardImpl(
    onClick: (() -> Unit)?,
    enabled: Boolean,
    modifier: Modifier,
    shape: Shape,
    colors: CardColors?,
    border: BorderStroke?,
    content: @Composable ColumnScope.() -> Unit
) {
    val container = colors?.containerColor ?: NestColors.Surface
    val contentColor = colors?.contentColor ?: NestColors.Ink
    Column(
        modifier = modifier
            .shadow(8.dp, shape, ambientColor = NestColors.Shadow.copy(alpha = 0.08f), spotColor = NestColors.Shadow.copy(alpha = 0.10f))
            .clip(shape)
            .background(container)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) { content() }
    }
}
