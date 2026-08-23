package com.bgremover.pngmaker.imaging

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A crop window expressed as fractions of the image, with (0,0) at the top-left and
 * (1,1) at the bottom-right.
 *
 * Normalised rather than pixel-based on purpose: the crop screen works on a downscaled
 * preview, and the same rectangle has to apply unchanged to the full-resolution photo.
 */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/** A crop window in real pixels, ready for `Bitmap.createBitmap`. */
data class PixelRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int
)

/** Which corner handle the user grabbed. */
enum class CropCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/**
 * All crop maths, kept free of Android types so it can be unit-tested on the JVM.
 *
 * Two invariants hold for every function that returns a [NormalizedRect]:
 *  1. the result lies inside 0..1 on both axes;
 *  2. it is never smaller than [MIN_SIZE] unless the image itself leaves no room.
 *
 * Aspect ratios are given the way a user thinks of them — 16:9 means the *pixels* come out
 * 16:9 — which is not the same as the normalised rectangle being 16:9 unless the image is
 * square. [normalizedAspect] does that conversion, and every aspect-aware function goes
 * through it.
 */
object CropGeometry {

    /** Smallest crop window, as a fraction of the image. */
    const val MIN_SIZE = 0.08f

    /**
     * The floor [sanitize] enforces. Much smaller than [MIN_SIZE] on purpose: the
     * interactive minimum keeps handles grabbable, but the safety net must not silently
     * re-frame a small crop the user deliberately made — an aspect-locked window on an
     * extreme ratio can legitimately end up narrower than [MIN_SIZE].
     */
    const val MIN_SANE = 0.01f

    /** Smallest crop window in real pixels — below this a cut-out is meaningless. */
    const val MIN_PIXELS = 16

    private const val EPS = 1e-4f

    val FULL = NormalizedRect(0f, 0f, 1f, 1f)

    /** Maps any quarter-turn count onto 0..3. */
    fun normalizeQuarters(quarters: Int): Int = ((quarters % 4) + 4) % 4

    fun rotatedWidth(width: Int, height: Int, quarters: Int): Int =
        if (normalizeQuarters(quarters) % 2 == 0) width else height

    fun rotatedHeight(width: Int, height: Int, quarters: Int): Int =
        if (normalizeQuarters(quarters) % 2 == 0) height else width

    /**
     * Converts a pixel aspect (width ÷ height) into the width ÷ height a *normalised*
     * rectangle needs so the cropped pixels come out at that aspect.
     */
    fun normalizedAspect(pixelAspect: Float, imageWidth: Int, imageHeight: Int): Float {
        if (imageWidth <= 0 || imageHeight <= 0) return pixelAspect
        return pixelAspect * imageHeight / imageWidth
    }

    /** Pulls an arbitrary rectangle back inside the image, preserving its centre. */
    fun sanitize(rect: NormalizedRect): NormalizedRect {
        var left = rect.left.coerceIn(0f, 1f)
        var right = rect.right.coerceIn(0f, 1f)
        if (right - left < MIN_SANE) {
            val centre = ((left + right) / 2f).coerceIn(MIN_SANE / 2f, 1f - MIN_SANE / 2f)
            left = centre - MIN_SANE / 2f
            right = centre + MIN_SANE / 2f
        }
        var top = rect.top.coerceIn(0f, 1f)
        var bottom = rect.bottom.coerceIn(0f, 1f)
        if (bottom - top < MIN_SANE) {
            val centre = ((top + bottom) / 2f).coerceIn(MIN_SANE / 2f, 1f - MIN_SANE / 2f)
            top = centre - MIN_SANE / 2f
            bottom = centre + MIN_SANE / 2f
        }
        return NormalizedRect(left, top, right, bottom)
    }

    /**
     * The largest centred rectangle with the requested pixel aspect. A null aspect means
     * "free", which is simply the whole frame.
     */
    fun centeredWithAspect(
        pixelAspect: Float?,
        imageWidth: Int,
        imageHeight: Int
    ): NormalizedRect {
        if (pixelAspect == null || pixelAspect <= 0f) return FULL
        val aspect = normalizedAspect(pixelAspect, imageWidth, imageHeight)
        val width: Float
        val height: Float
        if (aspect >= 1f) {
            width = 1f
            height = 1f / aspect
        } else {
            width = aspect
            height = 1f
        }
        val left = (1f - width) / 2f
        val top = (1f - height) / 2f
        return NormalizedRect(left, top, left + width, top + height)
    }

    /** Drags the whole window. The size never changes; the window stops at the edges. */
    fun move(rect: NormalizedRect, dx: Float, dy: Float): NormalizedRect {
        val width = rect.width.coerceIn(0f, 1f)
        val height = rect.height.coerceIn(0f, 1f)
        val left = (rect.left + dx).coerceIn(0f, (1f - width).coerceAtLeast(0f))
        val top = (rect.top + dy).coerceIn(0f, (1f - height).coerceAtLeast(0f))
        return NormalizedRect(left, top, left + width, top + height)
    }

    /**
     * Drags one corner. The opposite corner is the anchor and never moves, which is what
     * makes a locked aspect ratio feel predictable: the window grows out of a fixed point
     * instead of sliding around under the finger.
     */
    fun resize(
        rect: NormalizedRect,
        corner: CropCorner,
        dx: Float,
        dy: Float,
        pixelAspect: Float?,
        imageWidth: Int,
        imageHeight: Int
    ): NormalizedRect {
        val movingLeft = corner == CropCorner.TOP_LEFT || corner == CropCorner.BOTTOM_LEFT
        val movingTop = corner == CropCorner.TOP_LEFT || corner == CropCorner.TOP_RIGHT

        val anchorX = if (movingLeft) rect.right else rect.left
        val anchorY = if (movingTop) rect.bottom else rect.top
        val movingX = ((if (movingLeft) rect.left else rect.right) + dx).coerceIn(0f, 1f)
        val movingY = ((if (movingTop) rect.top else rect.bottom) + dy).coerceIn(0f, 1f)

        // How much room there is between the anchor and the edge it is growing towards.
        val roomX = (if (movingLeft) anchorX else 1f - anchorX).coerceAtLeast(0f)
        val roomY = (if (movingTop) anchorY else 1f - anchorY).coerceAtLeast(0f)
        // The anchor is already flat against the edge it would have to grow away from,
        // so there is nowhere for this corner to go.
        if (roomX <= EPS || roomY <= EPS) return rect

        var width = abs(movingX - anchorX)
        var height = abs(movingY - anchorY)

        if (pixelAspect != null && pixelAspect > 0f) {
            val aspect = normalizedAspect(pixelAspect, imageWidth, imageHeight)
                .coerceAtLeast(EPS)
            // Follow whichever axis the finger moved further on, then derive the other.
            if (width / aspect >= height) height = width / aspect else width = height * aspect
            // Grow up to the minimum, then shrink to whatever room is actually available.
            val grow = maxOf(
                1f,
                MIN_SIZE / width.coerceAtLeast(EPS),
                MIN_SIZE / height.coerceAtLeast(EPS)
            )
            width *= grow
            height *= grow
            val shrink = min(
                1f,
                min(roomX / width.coerceAtLeast(EPS), roomY / height.coerceAtLeast(EPS))
            )
            width *= shrink
            height *= shrink
        } else {
            width = width.coerceIn(min(MIN_SIZE, roomX), roomX.coerceAtLeast(EPS))
            height = height.coerceIn(min(MIN_SIZE, roomY), roomY.coerceAtLeast(EPS))
        }

        val left = if (movingLeft) anchorX - width else anchorX
        val top = if (movingTop) anchorY - height else anchorY
        return NormalizedRect(left, top, left + width, top + height)
    }

    /**
     * Projects the window onto real pixels. Rounds outward-safe: the result always fits
     * inside the bitmap and is always at least [MIN_PIXELS] on each side when the image
     * is big enough to allow it.
     */
    fun toPixels(rect: NormalizedRect, imageWidth: Int, imageHeight: Int): PixelRect {
        require(imageWidth > 0 && imageHeight > 0) { "Image must have a positive size" }
        val minWidth = min(MIN_PIXELS, imageWidth)
        val minHeight = min(MIN_PIXELS, imageHeight)

        val left = (rect.left * imageWidth).roundToInt().coerceIn(0, imageWidth - minWidth)
        val top = (rect.top * imageHeight).roundToInt().coerceIn(0, imageHeight - minHeight)
        val width = (rect.width * imageWidth).roundToInt().coerceIn(minWidth, imageWidth - left)
        val height =
            (rect.height * imageHeight).roundToInt().coerceIn(minHeight, imageHeight - top)

        return PixelRect(left = left, top = top, width = width, height = height)
    }

    /** True when the window still covers the whole image, i.e. cropping is a no-op. */
    fun isFullFrame(rect: NormalizedRect): Boolean =
        rect.left <= EPS && rect.top <= EPS && rect.right >= 1f - EPS && rect.bottom >= 1f - EPS
}
