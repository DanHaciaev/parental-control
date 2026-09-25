package com.teo.parent.requests

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream

private const val MAX_DIMENSION_PX = 1000
private const val INITIAL_JPEG_QUALITY = 75
private const val MIN_JPEG_QUALITY = 35
private const val QUALITY_STEP = 15

// The photo is embedded directly in the Firestore task document as base64 (no Firebase Storage —
// that now requires a billing account), and Firestore caps a document at 1 MiB total. Base64
// adds ~33% overhead, so raw JPEG bytes are kept well under that with margin for the rest of the
// document's fields.
private const val TARGET_MAX_BYTES = 650_000

/** Downscaled + re-encoded so a phone photo (often several MB straight off the camera) becomes
 *  small enough to embed in the task document — a task photo just needs to be legible, not full
 *  resolution. Quality is stepped down further if the first pass is still over budget. */
fun compressImageForUpload(context: Context, uri: Uri): ByteArray? {
    val original = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
    val scale = MAX_DIMENSION_PX.toFloat() / maxOf(original.width, original.height)
    val scaled = if (scale < 1f) {
        Bitmap.createScaledBitmap(original, (original.width * scale).toInt(), (original.height * scale).toInt(), true)
    } else {
        original
    }
    var quality = INITIAL_JPEG_QUALITY
    var bytes: ByteArray
    do {
        bytes = ByteArrayOutputStream().use { output ->
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, output)
            output.toByteArray()
        }
        quality -= QUALITY_STEP
    } while (bytes.size > TARGET_MAX_BYTES && quality >= MIN_JPEG_QUALITY)
    return bytes
}
