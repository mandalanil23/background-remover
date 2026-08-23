package com.bgremover.pngmaker.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.bgremover.pngmaker.ui.theme.BrandCyan
import com.bgremover.pngmaker.ui.theme.BrandFuchsia
import com.bgremover.pngmaker.ui.theme.BrandViolet

/** One drifting colour cloud. Positions are fractions of the drawing area. */
private data class Blob(
    val color: Color,
    val x: Float,
    val y: Float,
    val driftX: Float,
    val driftY: Float,
    val radius: Float
)

private val Blobs = listOf(
    Blob(BrandViolet, x = 0.14f, y = 0.06f, driftX = 0.18f, driftY = 0.10f, radius = 0.62f),
    Blob(BrandFuchsia, x = 0.92f, y = 0.22f, driftX = -0.22f, driftY = 0.16f, radius = 0.54f),
    Blob(BrandCyan, x = 0.30f, y = 0.88f, driftX = 0.26f, driftY = -0.14f, radius = 0.58f)
)

private const val DRIFT_PERIOD_MS = 11_000

/**
 * The app's signature backdrop: three brand-coloured clouds drifting slowly behind the
 * content.
 *
 * It is drawn, not composed — the whole effect is three radial gradients in a single
 * `drawBehind`, so it costs one draw pass and allocates nothing per frame. The opacity is
 * derived from the background's luminance so the same code reads well in both themes:
 * bolder on a dark surface, whisper-soft on a light one.
 */
@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(DRIFT_PERIOD_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auroraDrift"
    )

    val background = MaterialTheme.colorScheme.background
    val onDarkSurface = background.luminance() < 0.5f
    val alpha = (if (onDarkSurface) 0.34f else 0.20f) * intensity.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(background)
                val span = maxOf(size.width, size.height)
                Blobs.forEach { blob ->
                    val centre = Offset(
                        x = (blob.x + blob.driftX * drift) * size.width,
                        y = (blob.y + blob.driftY * drift) * size.height
                    )
                    val radius = blob.radius * span
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                blob.color.copy(alpha = alpha),
                                Color.Transparent
                            ),
                            center = centre,
                            radius = radius
                        ),
                        radius = radius,
                        center = centre
                    )
                }
            },
        contentAlignment = contentAlignment,
        content = content
    )
}
