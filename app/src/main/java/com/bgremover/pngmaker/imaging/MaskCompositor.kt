package com.bgremover.pngmaker.imaging

import android.graphics.Bitmap
import com.bgremover.pngmaker.data.model.EdgeSoftness
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Turns a segmentation confidence mask into a real transparent-PNG bitmap.
 *
 * Two things matter here and both are done deliberately:
 *
 *  1. **Resolution.** The model runs on a downscaled working copy, but the mask is
 *     bilinearly resampled back onto the full-resolution photo, so the exported PNG keeps
 *     the original pixel dimensions rather than the model's.
 *  2. **Memory.** The composite walks the image in horizontal bands, so peak allocation is
 *     one band of pixels plus the output bitmap — never two full-size int arrays.
 */
object MaskCompositor {

    /** Rows processed per pass. 256 rows of a 6000px-wide image is ~6 MB. */
    private const val BAND_HEIGHT = 256

    /**
     * @param source full-resolution photo (never modified)
     * @param mask   per-pixel foreground confidence in 0..1, row-major, [maskWidth] x [maskHeight]
     */
    fun composite(
        source: Bitmap,
        mask: FloatArray,
        maskWidth: Int,
        maskHeight: Int,
        softness: EdgeSoftness
    ): Bitmap {
        require(maskWidth > 0 && maskHeight > 0) { "empty mask" }
        require(mask.size >= maskWidth * maskHeight) { "mask smaller than its dimensions" }

        val smoothed = if (softness.blurRadius > 0) {
            boxBlur(mask, maskWidth, maskHeight, softness.blurRadius)
        } else {
            mask
        }

        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val scaleX = maskWidth.toFloat() / width
        val scaleY = maskHeight.toFloat() / height

        val band = IntArray(width * min(BAND_HEIGHT, height))

        var y = 0
        while (y < height) {
            val rows = min(BAND_HEIGHT, height - y)
            source.getPixels(band, 0, width, 0, y, width, rows)

            for (row in 0 until rows) {
                val srcY = (y + row + 0.5f) * scaleY - 0.5f
                val rowOffset = row * width
                for (x in 0 until width) {
                    val srcX = (x + 0.5f) * scaleX - 0.5f
                    val confidence = sampleBilinear(smoothed, maskWidth, maskHeight, srcX, srcY)
                    val alpha = remap(confidence, softness.low, softness.high)
                    val index = rowOffset + x
                    val pixel = band[index]
                    band[index] = if (alpha >= 255) {
                        pixel or ALPHA_MASK
                    } else if (alpha <= 0) {
                        0
                    } else {
                        // Straight (non-premultiplied) alpha keeps PNG colours faithful.
                        (alpha shl 24) or (pixel and RGB_MASK)
                    }
                }
            }

            output.setPixels(band, 0, width, 0, y, width, rows)
            y += rows
        }

        return output
    }

    /**
     * Fraction of the mask that is confidently foreground. Used to detect "the model found
     * nothing" and "the model selected the entire frame", both of which mean a useless cut-out.
     */
    fun foregroundCoverage(mask: FloatArray, size: Int): Float {
        if (size <= 0) return 0f
        var count = 0
        for (i in 0 until size) {
            if (mask[i] > 0.5f) count++
        }
        return count.toFloat() / size
    }

    /** Converts an ML Kit RGBA foreground bitmap into a plain alpha mask. */
    fun alphaMaskFrom(bitmap: Bitmap): FloatArray {
        val width = bitmap.width
        val height = bitmap.height
        val mask = FloatArray(width * height)
        val band = IntArray(width * min(BAND_HEIGHT, height))
        var y = 0
        while (y < height) {
            val rows = min(BAND_HEIGHT, height - y)
            bitmap.getPixels(band, 0, width, 0, y, width, rows)
            for (i in 0 until rows * width) {
                mask[y * width + i] = ((band[i] ushr 24) and 0xFF) / 255f
            }
            y += rows
        }
        return mask
    }

    private fun remap(value: Float, low: Float, high: Float): Int {
        if (high <= low) return if (value >= high) 255 else 0
        val t = ((value - low) / (high - low)).coerceIn(0f, 1f)
        // Smoothstep: avoids the hard staircase a linear ramp leaves on hair edges.
        val eased = t * t * (3f - 2f * t)
        return (eased * 255f).roundToInt().coerceIn(0, 255)
    }

    private fun sampleBilinear(
        mask: FloatArray,
        width: Int,
        height: Int,
        x: Float,
        y: Float
    ): Float {
        val clampedX = x.coerceIn(0f, (width - 1).toFloat())
        val clampedY = y.coerceIn(0f, (height - 1).toFloat())
        val x0 = clampedX.toInt()
        val y0 = clampedY.toInt()
        val x1 = min(x0 + 1, width - 1)
        val y1 = min(y0 + 1, height - 1)
        val fx = clampedX - x0
        val fy = clampedY - y0

        val topLeft = mask[y0 * width + x0]
        val topRight = mask[y0 * width + x1]
        val bottomLeft = mask[y1 * width + x0]
        val bottomRight = mask[y1 * width + x1]

        val top = topLeft + (topRight - topLeft) * fx
        val bottom = bottomLeft + (bottomRight - bottomLeft) * fx
        return top + (bottom - top) * fy
    }

    /** Separable box blur — two O(n) passes, no extra per-pixel allocation. */
    internal fun boxBlur(mask: FloatArray, width: Int, height: Int, radius: Int): FloatArray {
        if (radius <= 0) return mask
        val horizontal = FloatArray(width * height)
        val vertical = FloatArray(width * height)

        for (y in 0 until height) {
            val rowStart = y * width
            var sum = 0f
            for (x in -radius..radius) {
                sum += mask[rowStart + x.coerceIn(0, width - 1)]
            }
            val window = (2 * radius + 1).toFloat()
            for (x in 0 until width) {
                horizontal[rowStart + x] = sum / window
                val outgoing = mask[rowStart + max(0, x - radius)]
                val incoming = mask[rowStart + min(width - 1, x + radius + 1)]
                sum += incoming - outgoing
            }
        }

        val window = (2 * radius + 1).toFloat()
        for (x in 0 until width) {
            var sum = 0f
            for (y in -radius..radius) {
                sum += horizontal[y.coerceIn(0, height - 1) * width + x]
            }
            for (y in 0 until height) {
                vertical[y * width + x] = sum / window
                val outgoing = horizontal[max(0, y - radius) * width + x]
                val incoming = horizontal[min(height - 1, y + radius + 1) * width + x]
                sum += incoming - outgoing
            }
        }

        return vertical
    }

    private const val ALPHA_MASK = 0xFF000000.toInt()
    private const val RGB_MASK = 0x00FFFFFF
}
