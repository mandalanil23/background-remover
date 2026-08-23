package com.bgremover.pngmaker

import com.bgremover.pngmaker.imaging.MaskCompositor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the parts of the compositor that do not touch Android graphics.
 * The mask maths is where a regression would silently ruin every exported PNG.
 */
class MaskCompositorTest {

    @Test
    fun `coverage counts confident foreground pixels only`() {
        val mask = floatArrayOf(0f, 0.2f, 0.51f, 1f)
        assertEquals(0.5f, MaskCompositor.foregroundCoverage(mask, mask.size), 0.0001f)
    }

    @Test
    fun `coverage of an empty mask is zero`() {
        assertEquals(0f, MaskCompositor.foregroundCoverage(FloatArray(0), 0), 0.0001f)
    }

    @Test
    fun `box blur preserves a uniform mask`() {
        val mask = FloatArray(16) { 1f }
        val blurred = MaskCompositor.boxBlur(mask, 4, 4, 1)
        blurred.forEach { assertEquals(1f, it, 0.0001f) }
    }

    @Test
    fun `box blur softens a hard edge`() {
        val width = 8
        val height = 1
        // Left half background, right half foreground.
        val mask = FloatArray(width) { if (it < width / 2) 0f else 1f }
        val blurred = MaskCompositor.boxBlur(mask, width, height, 1)

        // The pixel just left of the boundary must pick up some foreground weight...
        assertTrue("edge should soften", blurred[3] > 0f)
        // ...and the pixel just right of it must lose some.
        assertTrue("edge should soften", blurred[4] < 1f)
        // Far from the edge the values are unchanged.
        assertEquals(0f, blurred[0], 0.0001f)
        assertEquals(1f, blurred[7], 0.0001f)
    }

    @Test
    fun `box blur with zero radius is a no-op`() {
        val mask = floatArrayOf(0f, 1f, 0f, 1f)
        assertTrue(MaskCompositor.boxBlur(mask, 4, 1, 0).contentEquals(mask))
    }
}
