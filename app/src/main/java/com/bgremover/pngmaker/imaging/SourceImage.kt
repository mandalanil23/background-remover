package com.bgremover.pngmaker.imaging

import android.net.Uri

/** Metadata about the image the user picked. The file itself is never modified. */
data class SourceImage(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int
) {
    val isLandscape: Boolean get() = width > height
    val pixelCount: Long get() = width.toLong() * height.toLong()
}
