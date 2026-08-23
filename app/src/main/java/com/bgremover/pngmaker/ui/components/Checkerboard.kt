package com.bgremover.pngmaker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bgremover.pngmaker.ui.theme.CheckerDark
import com.bgremover.pngmaker.ui.theme.CheckerDarkOnDarkTheme
import com.bgremover.pngmaker.ui.theme.CheckerLight
import com.bgremover.pngmaker.ui.theme.CheckerLightOnDarkTheme
import kotlin.math.ceil

/**
 * The universal "this pixel is transparent" affordance. Every surface that shows a cut-out
 * sits on one of these so the user can tell an erased background from a white one.
 */
@Composable
fun CheckerboardBox(
    modifier: Modifier = Modifier,
    cellSize: Dp = 12.dp,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val darkTheme = isSystemInDarkTheme()
    val light = if (darkTheme) CheckerLightOnDarkTheme else CheckerLight
    val dark = if (darkTheme) CheckerDarkOnDarkTheme else CheckerDark

    Box(
        modifier = modifier
            .background(light)
            .drawBehind {
                val cell = cellSize.toPx().coerceAtLeast(2f)
                val columns = ceil(size.width / cell).toInt()
                val rows = ceil(size.height / cell).toInt()
                for (row in 0 until rows) {
                    for (column in 0 until columns) {
                        if ((row + column) % 2 == 0) continue
                        drawRect(
                            color = dark,
                            topLeft = Offset(column * cell, row * cell),
                            size = Size(
                                width = minOf(cell, size.width - column * cell),
                                height = minOf(cell, size.height - row * cell)
                            )
                        )
                    }
                }
            },
        content = content
    )
}

/** Small square swatch used in list rows and empty states. */
@Composable
fun CheckerboardSwatch(
    modifier: Modifier = Modifier,
    cellSize: Dp = 6.dp,
    tint: Color? = null,
    content: @Composable BoxScope.() -> Unit = {}
) {
    Box(modifier) {
        CheckerboardBox(
            modifier = Modifier.matchParentSize(),
            cellSize = cellSize
        )
        tint?.let { Box(Modifier.matchParentSize().background(it)) }
        content()
    }
}
