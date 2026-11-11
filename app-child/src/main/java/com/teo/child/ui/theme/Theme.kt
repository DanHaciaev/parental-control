package com.teo.child.ui.theme

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.teo.core.ui.theme.NestTheme

/**
 * The app's theme is the shared A3 design (com.teo.core.ui.theme.NestTheme) — light only. This
 * wrapper just keeps the status-bar icons dark, since screens draw edge to edge on a light
 * background; screens that open with the navy header switch them to light themselves
 * (see LightStatusBarIcons).
 */
@Composable
fun ParentalControlTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        (view.context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }
    NestTheme(content = content)
}
