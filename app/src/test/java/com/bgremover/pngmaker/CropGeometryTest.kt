package com.bgremover.pngmaker

import com.bgremover.pngmaker.imaging.CropCorner
import com.bgremover.pngmaker.imaging.CropGeometry
import com.bgremover.pngmaker.imaging.NormalizedRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The crop window is chosen on a downscaled preview and applied to the full-resolution
 * photo, so every one of these rules has to hold independently of image size. That is what
 * makes the maths worth testing on its own, away from Android.
 */
class CropGeometryTest {

    private fun assertInsideImage(rect: NormalizedRect) {
        assertTrue("left $rect", rect.left >= -1e-4f)
        assertTrue("top $rect", rect.top >= -1e-4f)
        assertTrue("right $rect", rect.right <= 1f + 1e-4f)
        assertTrue("bottom $rect", rect.bottom <= 1f + 1e-4f)
        assertTrue("width $rect", rect.width > 0f)
        assertTrue("height $rect", rect.height > 0f)
    }

    @Test
    fun `quarter turns wrap in both directions`() {
        assertEquals(0, CropGeometry.normalizeQuarters(0))
        assertEquals(3, CropGeometry.normalizeQuarters(-1))
        assertEquals(1, CropGeometry.normalizeQuarters(5))
        assertEquals(2, CropGeometry.normalizeQuarters(-6))
    }

    @Test
    fun `odd quarter turns swap the reported dimensions`() {
        assertEquals(1920, CropGeometry.rotatedWidth(1920, 1080, quarters = 0))
        assertEquals(1080, CropGeometry.rotatedHeight(1920, 1080, quarters = 0))
        assertEquals(1080, CropGeometry.rotatedWidth(1920, 1080, quarters = 1))
        assertEquals(1920, CropGeometry.rotatedHeight(1920, 1080, quarters = 1))
        assertEquals(1920, CropGeometry.rotatedWidth(1920, 1080, quarters = -2))
    }

    @Test
    fun `a pixel aspect becomes a different normalised aspect on a non-square image`() {
        // Square pixels on a 2:1 image need a rectangle half as wide as it is tall.
        assertEquals(0.5f, CropGeometry.normalizedAspect(1f, 1000, 500), 1e-4f)
        // On a square image the two are the same thing.
        assertEquals(1.6f, CropGeometry.normalizedAspect(1.6f, 800, 800), 1e-4f)
    }

    @Test
    fun `a square crop of a landscape photo really is square in pixels`() {
        val rect = CropGeometry.centeredWithAspect(1f, imageWidth = 1000, imageHeight = 500)
        assertInsideImage(rect)
        val pixels = CropGeometry.toPixels(rect, 1000, 500)
        assertEquals(pixels.height, pixels.width)
        // And it uses the full short side rather than shrinking needlessly.
        assertEquals(500, pixels.height)
    }

    @Test
    fun `a 16 to 9 crop of a portrait photo keeps its ratio`() {
        val rect = CropGeometry.centeredWithAspect(16f / 9f, imageWidth = 1080, imageHeight = 1920)
        assertInsideImage(rect)
        val pixels = CropGeometry.toPixels(rect, 1080, 1920)
        assertEquals(16f / 9f, pixels.width.toFloat() / pixels.height, 0.02f)
        assertEquals(1080, pixels.width)
    }

    @Test
    fun `a free crop is the whole frame`() {
        assertEquals(CropGeometry.FULL, CropGeometry.centeredWithAspect(null, 100, 100))
        assertTrue(CropGeometry.isFullFrame(CropGeometry.FULL))
        assertFalse(CropGeometry.isFullFrame(NormalizedRect(0.1f, 0f, 1f, 1f)))
    }

    @Test
    fun `moving stops at the edge instead of leaving the image`() {
        val rect = NormalizedRect(0.6f, 0.6f, 0.9f, 0.9f)
        val pushed = CropGeometry.move(rect, dx = 0.8f, dy = 0.8f)
        assertInsideImage(pushed)
        assertEquals(1f, pushed.right, 1e-4f)
        assertEquals(1f, pushed.bottom, 1e-4f)
        // The window slid but kept its size — that is what makes dragging feel solid.
        assertEquals(rect.width, pushed.width, 1e-4f)
        assertEquals(rect.height, pushed.height, 1e-4f)
    }

    @Test
    fun `a corner cannot be dragged through its anchor`() {
        val rect = NormalizedRect(0.2f, 0.2f, 0.8f, 0.8f)
        val collapsed = CropGeometry.resize(
            rect = rect,
            corner = CropCorner.TOP_LEFT,
            dx = 5f,
            dy = 5f,
            pixelAspect = null,
            imageWidth = 1000,
            imageHeight = 1000
        )
        assertInsideImage(collapsed)
        assertTrue(collapsed.width >= CropGeometry.MIN_SIZE - 1e-4f)
        assertTrue(collapsed.height >= CropGeometry.MIN_SIZE - 1e-4f)
        // The bottom-right corner is the anchor and must not have moved.
        assertEquals(0.8f, collapsed.right, 1e-4f)
        assertEquals(0.8f, collapsed.bottom, 1e-4f)
    }

    @Test
    fun `resizing keeps the anchor still`() {
        val rect = NormalizedRect(0.2f, 0.2f, 0.8f, 0.8f)
        val resized = CropGeometry.resize(
            rect, CropCorner.BOTTOM_RIGHT, dx = -0.2f, dy = -0.1f,
            pixelAspect = null, imageWidth = 400, imageHeight = 400
        )
        assertEquals(0.2f, resized.left, 1e-4f)
        assertEquals(0.2f, resized.top, 1e-4f)
        assertEquals(0.6f, resized.right, 1e-3f)
        assertEquals(0.7f, resized.bottom, 1e-3f)
    }

    @Test
    fun `a locked ratio survives every corner drag`() {
        val image = 1600 to 1200
        val ratios = listOf(1f, 4f / 3f, 3f / 4f, 16f / 9f, 9f / 16f)
        val drags = listOf(0.3f to 0.05f, -0.25f to 0.4f, 0.6f to -0.6f, -0.9f to -0.9f)

        ratios.forEach { ratio ->
            var rect = CropGeometry.centeredWithAspect(ratio, image.first, image.second)
            CropCorner.entries.forEach { corner ->
                drags.forEach { (dx, dy) ->
                    rect = CropGeometry.resize(
                        rect, corner, dx, dy, ratio, image.first, image.second
                    )
                    assertInsideImage(rect)
                    val actual =
                        (rect.width * image.first) / (rect.height * image.second)
                    assertTrue(
                        "ratio drifted to $actual (wanted $ratio) after $corner $dx/$dy",
                        abs(actual - ratio) < 0.02f
                    )
                }
            }
        }
    }

    @Test
    fun `pixel windows always fit inside the bitmap`() {
        val silly = listOf(
            NormalizedRect(-3f, -3f, 9f, 9f),
            NormalizedRect(0.999f, 0.999f, 1f, 1f),
            NormalizedRect(0f, 0f, 0.0001f, 0.0001f),
            NormalizedRect(0.5f, 0.5f, 0.5f, 0.5f)
        )
        listOf(4032 to 3024, 640 to 480, 20 to 4000).forEach { (width, height) ->
            silly.forEach { rect ->
                val pixels = CropGeometry.toPixels(CropGeometry.sanitize(rect), width, height)
                assertTrue("left ${pixels.left}", pixels.left >= 0)
                assertTrue("top ${pixels.top}", pixels.top >= 0)
                assertTrue("width ${pixels.width}", pixels.width > 0)
                assertTrue("height ${pixels.height}", pixels.height > 0)
                assertTrue(
                    "right edge ${pixels.left + pixels.width} > $width",
                    pixels.left + pixels.width <= width
                )
                assertTrue(
                    "bottom edge ${pixels.top + pixels.height} > $height",
                    pixels.top + pixels.height <= height
                )
            }
        }
    }

    @Test
    fun `sanitize pulls a degenerate rectangle back to a usable one`() {
        val fixed = CropGeometry.sanitize(NormalizedRect(0.5f, 0.5f, 0.5f, 0.5f))
        assertInsideImage(fixed)
        assertEquals(CropGeometry.MIN_SANE, fixed.width, 1e-4f)
        assertEquals(CropGeometry.MIN_SANE, fixed.height, 1e-4f)
    }

    @Test
    fun `sanitize leaves a deliberately small crop alone`() {
        // A 9:16 window dragged small on a wide photo is legitimately narrower than the
        // interactive minimum. The safety net must not silently re-frame it.
        val tiny = NormalizedRect(0.40f, 0.40f, 0.4338f, 0.48f)
        val kept = CropGeometry.sanitize(tiny)
        assertEquals(tiny.width, kept.width, 1e-4f)
        assertEquals(tiny.height, kept.height, 1e-4f)
    }

    @Test
    fun `a full-frame crop maps to the whole bitmap`() {
        val pixels = CropGeometry.toPixels(CropGeometry.FULL, 4032, 3024)
        assertEquals(0, pixels.left)
        assertEquals(0, pixels.top)
        assertEquals(4032, pixels.width)
        assertEquals(3024, pixels.height)
    }
}
