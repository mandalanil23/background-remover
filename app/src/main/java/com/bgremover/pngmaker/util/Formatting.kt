package com.bgremover.pngmaker.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

/** "2.4 MB", "512 KB", "0 B" — never throws, never shows a negative size. */
fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroup = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
    val value = bytes / 1024.0.pow(digitGroup.toDouble())
    return if (digitGroup == 0) {
        "$bytes B"
    } else {
        String.format(Locale.US, "%.1f %s", value, units[digitGroup])
    }
}

fun formatDimensions(width: Int, height: Int): String = "$width × $height"

fun formatMegapixels(width: Int, height: Int): String {
    val mp = (width.toLong() * height.toLong()) / 1_000_000.0
    return String.format(Locale.US, "%.1f MP", mp)
}

fun formatTimestamp(millis: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(millis))

/** Strips any directory part and control characters from a user-supplied display name. */
fun sanitizeDisplayName(raw: String?): String {
    val name = raw?.substringAfterLast('/')?.trim().orEmpty()
    if (name.isEmpty()) return "image"
    return name.filter { it.isLetterOrDigit() || it in " ._-()[]" }
        .take(80)
        .ifEmpty { "image" }
}
