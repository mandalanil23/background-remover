package com.bgremover.pngmaker.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Every gradient in the app comes from here.
 *
 * The colours are deliberately theme-independent: a gradient that changed between light and
 * dark would stop being the brand. Contrast is handled by what sits *on* the gradient —
 * always white — and by keeping gradients off large reading surfaces.
 */
object AppGradients {

    /** The signature three-stop ramp. */
    val Ramp: List<Color> = listOf(BrandViolet, BrandFuchsia, BrandCyan)

    /** Supporting pairs, used to give sibling elements their own identity. */
    val Warm: List<Color> = listOf(BrandFuchsia, BrandAmber)
    val Cool: List<Color> = listOf(BrandCyan, BrandEmerald)
    val Royal: List<Color> = listOf(BrandViolet, BrandCyan)
    val Sunset: List<Color> = listOf(BrandAmber, BrandFuchsia)

    fun horizontal(colors: List<Color> = Ramp): Brush = Brush.horizontalGradient(colors)

    fun vertical(colors: List<Color> = Ramp): Brush = Brush.verticalGradient(colors)

    /** Corner-to-corner. Reads as more energetic than a straight horizontal sweep. */
    fun diagonal(colors: List<Color> = Ramp): Brush =
        Brush.linearGradient(colors = colors, start = Offset.Zero, end = Offset.Infinite)

    /** Closed loop — for spinners and rings, where the seam would otherwise show. */
    fun sweep(colors: List<Color> = Ramp): Brush =
        Brush.sweepGradient(colors + colors.first())
}

/**
 * Paints text (or any drawn content) with a gradient.
 *
 * Renders the content into an offscreen layer and then floods it with the brush using
 * `SrcAtop`, so only the glyphs are tinted. Works on every API level the app supports.
 */
fun Modifier.gradientFill(brush: Brush): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(brush = brush, blendMode = BlendMode.SrcAtop)
    }
