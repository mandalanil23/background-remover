package com.bgremover.pngmaker

import com.bgremover.pngmaker.util.formatDimensions
import com.bgremover.pngmaker.util.formatFileSize
import com.bgremover.pngmaker.util.sanitizeDisplayName
import org.junit.Assert.assertEquals
import org.junit.Test

class FormattingTest {

    @Test
    fun `file size handles zero and negatives without throwing`() {
        assertEquals("0 B", formatFileSize(0))
        assertEquals("0 B", formatFileSize(-1))
    }

    @Test
    fun `file size uses sensible units`() {
        assertEquals("512 B", formatFileSize(512))
        assertEquals("1.0 KB", formatFileSize(1024))
        assertEquals("1.5 MB", formatFileSize((1.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun `dimensions are rendered with a multiplication sign`() {
        assertEquals("1920 × 1080", formatDimensions(1920, 1080))
    }

    @Test
    fun `display names are stripped of paths and unsafe characters`() {
        assertEquals("photo.jpg", sanitizeDisplayName("/storage/emulated/0/DCIM/photo.jpg"))
        assertEquals("image", sanitizeDisplayName(null))
        assertEquals("image", sanitizeDisplayName("   "))
        assertEquals("my photo (1).png", sanitizeDisplayName("my photo (1).png"))
    }
}
