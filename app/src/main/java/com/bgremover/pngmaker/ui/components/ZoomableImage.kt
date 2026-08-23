package com.bgremover.pngmaker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.Image

/** Pan/zoom state that survives recomposition and can be reset from a toolbar button. */
@Stable
class ZoomState(
    val minScale: Float = 1f,
    val maxScale: Float = 6f
) {
    // Delegated properties cannot declare a private setter, so the mutable state is kept
    // private and exposed through read-only accessors.
    private var currentScale by mutableFloatStateOf(1f)
    private var currentOffset by mutableStateOf(Offset.Zero)

    val scale: Float get() = currentScale
    val offset: Offset get() = currentOffset

    private var containerSize: IntSize = IntSize.Zero

    val isTransformed: Boolean get() = currentScale > 1.001f || currentOffset != Offset.Zero

    fun onContainerSizeChanged(size: IntSize) {
        containerSize = size
        currentOffset = clampOffset(currentOffset, currentScale)
    }

    fun transform(zoomChange: Float, panChange: Offset) {
        val newScale = (currentScale * zoomChange).coerceIn(minScale, maxScale)
        val newOffset = if (newScale <= minScale) Offset.Zero else currentOffset + panChange
        currentScale = newScale
        currentOffset = clampOffset(newOffset, newScale)
    }

    fun zoomBy(factor: Float) {
        val newScale = (currentScale * factor).coerceIn(minScale, maxScale)
        currentScale = newScale
        currentOffset =
            clampOffset(if (newScale <= minScale) Offset.Zero else currentOffset, newScale)
    }

    fun reset() {
        currentScale = 1f
        currentOffset = Offset.Zero
    }

    /** Keeps the image from being dragged completely out of view. */
    private fun clampOffset(candidate: Offset, forScale: Float): Offset {
        if (containerSize == IntSize.Zero || forScale <= minScale) return Offset.Zero
        val maxX = (containerSize.width * (forScale - 1f)) / 2f
        val maxY = (containerSize.height * (forScale - 1f)) / 2f
        return Offset(
            x = candidate.x.coerceIn(-maxX, maxX),
            y = candidate.y.coerceIn(-maxY, maxY)
        )
    }
}

@Composable
fun rememberZoomState(): ZoomState = remember { ZoomState() }

/**
 * An image the user can pinch, drag and double-tap. Deliberately gesture-only: there is no
 * scroll container fighting for the same touches.
 */
@Composable
fun ZoomableImage(
    image: ImageBitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    zoomState: ZoomState = rememberZoomState()
) {
    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        zoomState.transform(zoomChange, panChange)
    }

    val animatedScale by animateFloatAsState(
        targetValue = zoomState.scale,
        label = "zoomScale"
    )

    Box(
        modifier = modifier
            .onSizeChanged { zoomState.onContainerSizeChanged(it) }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (zoomState.isTransformed) zoomState.reset() else zoomState.zoomBy(2.5f)
                    }
                )
            }
            .transformable(state = transformableState)
    ) {
        Image(
            bitmap = image,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = animatedScale
                    scaleY = animatedScale
                    translationX = zoomState.offset.x
                    translationY = zoomState.offset.y
                }
        )
    }
}
