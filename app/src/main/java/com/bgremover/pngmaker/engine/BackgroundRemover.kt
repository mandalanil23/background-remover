package com.bgremover.pngmaker.engine

import android.graphics.Bitmap
import com.bgremover.pngmaker.util.AppError

/**
 * A source of foreground/background segmentation.
 *
 * Implementations return a **confidence mask** rather than a finished bitmap so the app can
 * do its own edge shaping and composite onto the full-resolution original. Adding a new
 * engine — including a remote one — means implementing this interface and registering it in
 * [EngineFactory]; nothing else in the app needs to change.
 */
interface BackgroundRemover {

    /** Stable identifier used in logs and settings. */
    val id: String

    /** True when this engine can run right now (model present, service reachable, ...). */
    suspend fun isAvailable(): Boolean

    /**
     * @param bitmap working-resolution copy of the photo
     * @return foreground confidence per pixel, row-major, values in 0..1
     * @throws SegmentationException with a user-facing [AppError] on any failure
     */
    suspend fun segment(bitmap: Bitmap): SegmentationResult

    /** Releases native resources. Safe to call more than once. */
    fun close()
}

data class SegmentationResult(
    val mask: FloatArray,
    val width: Int,
    val height: Int
) {
    val size: Int get() = width * height

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SegmentationResult) return false
        return width == other.width && height == other.height && mask.contentEquals(other.mask)
    }

    override fun hashCode(): Int =
        (mask.contentHashCode() * 31 + width) * 31 + height
}

class SegmentationException(val error: AppError, cause: Throwable? = null) :
    Exception(cause?.message, cause)
