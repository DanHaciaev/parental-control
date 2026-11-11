package com.teo.core.ui.components

import android.app.Activity
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.teo.core.R
import com.teo.core.ui.theme.NestColors
import kotlin.math.cos
import kotlin.math.sin

/** A line icon from the shared set (core/res/drawable/nest_ic_*), tinted like text by default. */
@Composable
fun NestIcon(
    @DrawableRes id: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = LocalContentColor.current,
    size: Dp = 22.dp
) {
    Icon(painter = painterResource(id), contentDescription = contentDescription, tint = tint, modifier = modifier.size(size))
}

/** Icon in a light-navy rounded square — the A3 way to lead a card, row or action. */
@Composable
fun IconTile(
    @DrawableRes id: Int,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tint: Color = NestColors.Navy,
    background: Color = NestColors.NavyTint
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.33f))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        NestIcon(id, tint = tint, size = size * 0.5f)
    }
}

/** White card with the soft navy-tinted shadow — no border (borders read as stock Material). */
@Composable
fun NestCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(22.dp),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .shadow(elevation = 10.dp, shape = shape, ambientColor = NestColors.Shadow.copy(alpha = 0.10f), spotColor = NestColors.Shadow.copy(alpha = 0.12f))
            .clip(shape)
            .background(NestColors.Surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content
    )
}

/**
 * The navy header that opens the main screens: rounded bottom corners, a faint compass-rose
 * watermark (nod to the Nest Parent icon), status-bar safe. Leave [bottomPadding] at ~64dp when
 * a card is meant to overlap it from below (offset that card by about -40dp).
 */
@Composable
fun NestHeader(
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 64.dp,
    compact: Boolean = false,
    showWatermark: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = if (compact) 28.dp else 36.dp, bottomEnd = if (compact) 28.dp else 36.dp))
            .background(NestColors.NavyHeader)
    ) {
        // The watermark only decorates: matchParentSize keeps it from setting the header's height
        // (a fixed 320dp rose used to stretch even a one-line header). Compact headers show just
        // the part that peeks in from the right.
        if (showWatermark) Box(Modifier.matchParentSize()) {
            CompassWatermark(
                if (compact) Modifier.align(Alignment.CenterEnd).offset(x = 110.dp).requiredSize(260.dp)
                else Modifier.align(Alignment.Center).requiredSize(320.dp)
            )
        }
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 22.dp, end = 22.dp, top = if (compact) 8.dp else 12.dp, bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp),
                content = content
            )
        }
    }
}

/** Compact navy header for inner screens: back arrow, title, optional actions. */
@Composable
fun NestScreenHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    NestHeader(bottomPadding = 18.dp, compact = true) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onBack != null) {
                HeaderIconButton(R.drawable.nest_ic_arrow_left, contentDescription = "Назад", onClick = onBack)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = NestColors.OnNavyMuted)
                }
            }
            actions()
        }
    }
}

/** Round translucent button that sits on the navy header. */
@Composable
fun HeaderIconButton(@DrawableRes id: Int, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)), CircleShape)
            .clickable(onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        NestIcon(id, contentDescription = contentDescription, tint = Color.White, size = 20.dp)
    }
}

/** Translucent pill on the header — the child switcher ("С  Саша ⌄"). */
@Composable
fun HeaderPill(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

/** Circle with the first letter — used for a child (or parent) without a photo. */
@Composable
fun InitialAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 32.dp, background: Color = NestColors.Silver, color: Color = NestColors.NavyHeader) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.trim().take(1).uppercase().ifEmpty { "?" },
            style = MaterialTheme.typography.titleSmall,
            color = color
        )
    }
}

/** Progress ring (0f..1f), content drawn in its middle — A3's "2:15 из 3:20". */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 210.dp,
    strokeWidth: Dp = 12.dp,
    trackColor: Color = Color.White.copy(alpha = 0.12f),
    color: Color = NestColors.SilverLight,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(trackColor, 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
            val sweep = 360f * progress.coerceIn(0f, 1f)
            if (sweep > 0f) {
                drawArc(color, -90f, sweep, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

/** Section heading above a group of cards, with an optional trailing link ("Все 24"). */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier = modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

/** One row inside a [NestCard] list: leading tile, title/subtitle, trailing content. */
@Composable
fun NestListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes icon: Int? = null,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {
        if (onClick != null) NestIcon(R.drawable.nest_ic_chevron_right, tint = NestColors.InkSecondary, size = 18.dp)
    }
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            leading != null -> leading()
            icon != null -> IconTile(icon)
        }
        if (leading != null || icon != null) Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = NestColors.InkSecondary)
            }
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

/** Thin divider between rows inside a card. */
@Composable
fun RowDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(NestColors.Divider))
}

/** Status as a small icon + coloured text (A3 doesn't use coloured pill badges). */
@Composable
fun StatusText(text: String, color: Color, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        if (icon != null) NestIcon(icon, tint = color, size = 14.dp)
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Faint compass rose behind the header content. Public so a header with a centrepiece (the
 *  home screen's ring) can centre it on that instead — see NestHeader's showWatermark. */
@Composable
fun CompassWatermark(modifier: Modifier) {
    val line = Color.White.copy(alpha = 0.06f)
    Canvas(modifier = modifier) {
        val c = center
        val r = size.minDimension / 2
        val w = 0.8.dp.toPx() * (size.minDimension / 100f)
        drawCircle(line, radius = r * 0.96f, center = c, style = Stroke(w))
        drawCircle(line, radius = r * 0.80f, center = c, style = Stroke(w))
        for (i in 0 until 8) {
            val angle = Math.toRadians(i * 45.0)
            val inner = if (i % 2 == 0) r * 0.80f else r * 0.84f
            val outer = r * 0.96f + (if (i % 2 == 0) r * 0.04f else 0f)
            drawLine(
                line,
                Offset(c.x + (inner * cos(angle)).toFloat(), c.y + (inner * sin(angle)).toFloat()),
                Offset(c.x + (outer * cos(angle)).toFloat(), c.y + (outer * sin(angle)).toFloat()),
                strokeWidth = w
            )
        }
    }
}

/** Pulls a card up over the navy header above it (A3: the status card overlapping the header)
 *  without leaving the gap a plain offset would. */
fun Modifier.overlapAbove(amount: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = amount.roundToPx()
    layout(placeable.width, (placeable.height - shift).coerceAtLeast(0)) {
        placeable.place(0, -shift)
    }
}

/** While on screen, makes the status-bar icons white — for screens that start with the navy
 *  header (the app theme keeps them dark for the light background). */
@Composable
fun StatusBarOnNavy() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = false
        onDispose { controller?.isAppearanceLightStatusBars = true }
    }
}

/** One destination of [NestBottomBar]. */
data class NestNavItem(val label: String, @DrawableRes val icon: Int)

/** A3 bottom navigation: white bar, soft upward shadow, active item navy — no Material pill. */
@Composable
fun NestBottomBar(items: List<NestNavItem>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(16.dp, ambientColor = NestColors.Shadow.copy(alpha = 0.08f), spotColor = NestColors.Shadow.copy(alpha = 0.10f))
            .background(NestColors.Surface)
            .navigationBarsPadding()
            .padding(top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        items.forEachIndexed { index, item ->
            val active = index == selectedIndex
            val color = if (active) NestColors.Navy else NestColors.InkSecondary
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClickLabel = item.label) { onSelect(index) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                NestIcon(item.icon, tint = color, size = 23.dp)
                Text(
                    item.label,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                    color = color,
                    fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Full-screen layout for setup and gate screens (login, pairing, set code, app lock): navy header
 * with the compass mark, title and subtitle, then the screen's content on the light background.
 */
@Composable
fun NestOnboardingScreen(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes headerIcon: Int = R.drawable.nest_ic_compass,
    content: @Composable ColumnScope.() -> Unit
) {
    StatusBarOnNavy()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NestColors.Background)
            .verticalScroll(rememberScrollState())
            .imePadding()
    ) {
        NestHeader(bottomPadding = 36.dp) {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) { NestIcon(headerIcon, tint = Color.White, size = 30.dp) }
            Text(title, style = MaterialTheme.typography.headlineLarge, color = Color.White)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = NestColors.OnNavyMuted)
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 24.dp),
            content = content
        )
    }
}
