package com.teo.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teo.core.R

/**
 * Design "A3 · Компас крупный" — the one visual style for every screen of both apps (navy
 * header with the key figure, white shadowed cards, thin line icons, Manrope). Light only, by
 * the owner's choice. Mockup: claude.ai/artifact/8FTjyxrT7afDE5snxKXTsZ.
 */
object NestColors {
    val Background = Color(0xFFF2F4F8)
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF111A2E)
    val InkSecondary = Color(0xFF6A7387)
    val Divider = Color(0xFFEEF1F6)

    val Navy = Color(0xFF1F3B73)
    val NavyHeader = Color(0xFF172E5C)
    val NavyTint = Color(0xFFEAEFF7)
    val Silver = Color(0xFFC9D3E3)
    val SilverLight = Color(0xFFD3DCEB)
    val OnNavyMuted = Color(0xFFAFC0DD)

    val Danger = Color(0xFFA8473C)
    val DangerTint = Color(0xFFF6E7E5)
    val Success = Color(0xFF2F8F62)
    val SuccessTint = Color(0xFFE3F0E8)
    val Warning = Color(0xFF8A6417)
    val WarningTint = Color(0xFFF5EDDC)

    /** Shadow tint for cards — a navy-tinted shadow reads softer than plain black. */
    val Shadow = Color(0xFF16294F)
}

@OptIn(ExperimentalTextApi::class)
private fun manrope(weight: Int) = Font(
    R.font.manrope,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

val Manrope = FontFamily(manrope(400), manrope(500), manrope(600), manrope(700), manrope(800))

private fun style(size: Int, weight: Int, lineHeight: Int, letterSpacing: Double = 0.0) = TextStyle(
    fontFamily = Manrope,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp
)

val NestTypography = Typography(
    displayLarge = style(52, 800, 56, -2.0),
    displayMedium = style(40, 800, 44, -1.5),
    displaySmall = style(32, 800, 38, -1.0),
    headlineLarge = style(28, 800, 34, -0.6),
    headlineMedium = style(24, 800, 30, -0.4),
    headlineSmall = style(21, 800, 27, -0.3),
    titleLarge = style(19, 800, 25, -0.2),
    titleMedium = style(16, 700, 22),
    titleSmall = style(14, 800, 20),
    bodyLarge = style(16, 500, 23),
    bodyMedium = style(14, 500, 20),
    bodySmall = style(12, 600, 17),
    labelLarge = style(15, 700, 20),
    labelMedium = style(12, 700, 16),
    labelSmall = style(11, 700, 14, 0.4)
)

val NestShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(13.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

private val NestColorScheme = lightColorScheme(
    primary = NestColors.Navy,
    onPrimary = Color.White,
    primaryContainer = NestColors.NavyTint,
    onPrimaryContainer = NestColors.Navy,
    secondary = NestColors.Navy,
    onSecondary = Color.White,
    secondaryContainer = NestColors.NavyTint,
    onSecondaryContainer = NestColors.Navy,
    tertiary = NestColors.Success,
    onTertiary = Color.White,
    tertiaryContainer = NestColors.SuccessTint,
    onTertiaryContainer = NestColors.Success,
    background = NestColors.Background,
    onBackground = NestColors.Ink,
    surface = NestColors.Surface,
    onSurface = NestColors.Ink,
    surfaceVariant = NestColors.NavyTint,
    onSurfaceVariant = NestColors.InkSecondary,
    surfaceContainerLowest = NestColors.Surface,
    surfaceContainerLow = NestColors.Surface,
    surfaceContainer = NestColors.Surface,
    surfaceContainerHigh = NestColors.Surface,
    surfaceContainerHighest = NestColors.NavyTint,
    outline = NestColors.Silver,
    outlineVariant = NestColors.Divider,
    error = NestColors.Danger,
    onError = Color.White,
    errorContainer = NestColors.DangerTint,
    onErrorContainer = NestColors.Danger
)

@Composable
fun NestTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NestColorScheme,
        typography = NestTypography,
        shapes = NestShapes,
        content = content
    )
}
