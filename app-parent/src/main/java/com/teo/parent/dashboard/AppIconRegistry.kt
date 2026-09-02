package com.teo.parent.dashboard

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.teo.parent.R

/**
 * Recognizable apps get a hand-drawn glyph in a brand-ish color instead of a plain letter —
 * not real app icons (that would need uploading them from the child's phone to paid Storage),
 * but enough visual variety to actually look designed. Unknown apps fall back to a letter badge.
 */
data class AppIconSpec(@DrawableRes val glyph: Int, val bg: Color, val glyphTint: Color = Color.White)

object AppIconRegistry {

    private val mapping: Map<String, AppIconSpec> = mapOf(
        "com.android.chrome" to AppIconSpec(R.drawable.ic_glyph_browser, Color(0xFF4285F4)),
        "com.chrome.beta" to AppIconSpec(R.drawable.ic_glyph_browser, Color(0xFF4285F4)),
        "org.mozilla.firefox" to AppIconSpec(R.drawable.ic_glyph_browser, Color(0xFFFF7139)),
        "com.opera.browser" to AppIconSpec(R.drawable.ic_glyph_browser, Color(0xFFFF1B2D)),
        "com.microsoft.emmx" to AppIconSpec(R.drawable.ic_glyph_browser, Color(0xFF0078D7)),
        "com.google.android.youtube" to AppIconSpec(R.drawable.ic_glyph_video, Color(0xFFFF0000)),
        "com.instagram.android" to AppIconSpec(R.drawable.ic_glyph_camera, Color(0xFFC13584)),
        "com.zhiliaoapp.musically" to AppIconSpec(R.drawable.ic_glyph_video, Color(0xFF1D1D1D)),
        "com.ss.android.ugc.trill" to AppIconSpec(R.drawable.ic_glyph_video, Color(0xFF1D1D1D)),
        "com.whatsapp" to AppIconSpec(R.drawable.ic_glyph_chat, Color(0xFF25D366)),
        "com.facebook.katana" to AppIconSpec(R.drawable.ic_glyph_person, Color(0xFF1877F2)),
        "com.facebook.lite" to AppIconSpec(R.drawable.ic_glyph_person, Color(0xFF1877F2)),
        "org.telegram.messenger" to AppIconSpec(R.drawable.ic_glyph_chat, Color(0xFF29A9EB)),
        "com.google.android.gm" to AppIconSpec(R.drawable.ic_glyph_mail, Color(0xFFEA4335)),
        "com.microsoft.office.outlook" to AppIconSpec(R.drawable.ic_glyph_mail, Color(0xFF0072C6)),
        "com.google.android.apps.maps" to AppIconSpec(R.drawable.ic_glyph_pin, Color(0xFF34A853)),
        "com.android.vending" to AppIconSpec(R.drawable.ic_glyph_cart, Color(0xFF01875F)),
        "com.spotify.music" to AppIconSpec(R.drawable.ic_glyph_music, Color(0xFF1DB954)),
        "com.netflix.mediaclient" to AppIconSpec(R.drawable.ic_glyph_video, Color(0xFFE50914)),
        "com.snapchat.android" to AppIconSpec(R.drawable.ic_glyph_camera, Color(0xFFFFFC00), glyphTint = Color(0xFF2B231D)),
        "com.twitter.android" to AppIconSpec(R.drawable.ic_glyph_chat, Color(0xFF1DA1F2)),
        "com.discord" to AppIconSpec(R.drawable.ic_glyph_chat, Color(0xFF5865F2)),
        "com.roblox.client" to AppIconSpec(R.drawable.ic_glyph_game, Color(0xFFE2231A)),
        "com.mojang.minecraftpe" to AppIconSpec(R.drawable.ic_glyph_game, Color(0xFF62B132)),
        "com.microsoft.office.excel" to AppIconSpec(R.drawable.ic_glyph_document, Color(0xFF217346)),
        "com.microsoft.office.word" to AppIconSpec(R.drawable.ic_glyph_document, Color(0xFF2B579A)),
        "host.exp.exponent" to AppIconSpec(R.drawable.ic_glyph_document, Color(0xFF000020)),
        "com.google.android.apps.photos" to AppIconSpec(R.drawable.ic_glyph_camera, Color(0xFFFBBC05)),
        "com.pinterest" to AppIconSpec(R.drawable.ic_glyph_heart, Color(0xFFE60023)),
        "com.linkedin.android" to AppIconSpec(R.drawable.ic_glyph_person, Color(0xFF0A66C2)),
        "com.reddit.frontpage" to AppIconSpec(R.drawable.ic_glyph_chat, Color(0xFFFF4500))
    )

    fun forPackage(packageName: String): AppIconSpec? = mapping[packageName]
}
