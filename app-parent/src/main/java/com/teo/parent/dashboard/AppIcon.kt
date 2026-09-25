package com.teo.parent.dashboard

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Prefers the app's real icon (captured on the child's device, see InstalledApp.iconBase64),
 *  falls back to a hand-drawn glyph for recognized packages, then a plain letter badge. */
@Composable
fun AppIcon(packageName: String, label: String, fg: Color, bg: Color, iconBase64: String? = null, modifier: Modifier = Modifier) {
    val realIcon = remember(iconBase64) {
        iconBase64?.let {
            runCatching {
                val bytes = Base64.decode(it, Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }
    val spec = AppIconRegistry.forPackage(packageName)

    Box(
        modifier = modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
            .background(if (realIcon != null) MaterialTheme.colorScheme.surfaceVariant else spec?.bg ?: bg),
        contentAlignment = Alignment.Center
    ) {
        when {
            realIcon != null -> Image(
                bitmap = realIcon,
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
            spec != null -> Image(
                painter = painterResource(spec.glyph),
                contentDescription = null,
                colorFilter = ColorFilter.tint(spec.glyphTint),
                modifier = Modifier.size(20.dp)
            )
            else -> Text(
                text = label.trim().take(1).uppercase().ifBlank { "?" },
                color = fg,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
