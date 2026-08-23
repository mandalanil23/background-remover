package com.bgremover.pngmaker.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bgremover.pngmaker.R

/**
 * Drag-to-compare view: the cut-out on the right, the untouched original on the left,
 * with a handle the user drags across. Far more convincing than two thumbnails side by
 * side, because the subject stays in exactly the same place.
 */
@Composable
fun BeforeAfterSlider(
    original: ImageBitmap,
    result: ImageBitmap,
    modifier: Modifier = Modifier
) {
    var handleFraction by remember { mutableFloatStateOf(0.5f) }
    var widthPx by remember { mutableFloatStateOf(1f) }

    Box(
        modifier = modifier
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    handleFraction = (handleFraction + dragAmount / widthPx).coerceIn(0f, 1f)
                }
            }
    ) {
        // After: the transparent PNG on a checkerboard.
        CheckerboardBox(modifier = Modifier.fillMaxSize()) {
            Image(
                bitmap = result,
                contentDescription = stringResource(R.string.result),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Before: the original, clipped to the left of the handle.
        Image(
            bitmap = original,
            contentDescription = stringResource(R.string.original),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    clipRect(right = size.width * handleFraction) {
                        this@drawWithContent.drawContent()
                    }
                }
        )

        // Handle
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    val x = size.width * handleFraction
                    drawRect(
                        color = Color.White,
                        topLeft = androidx.compose.ui.geometry.Offset(x - 1.5f, 0f),
                        size = androidx.compose.ui.geometry.Size(3f, size.height)
                    )
                }
        )

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer { translationX = widthPx * (handleFraction - 0.5f) }
                .size(40.dp)
                .clip(CircleShape)
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = null, Modifier.size(18.dp))
                Icon(Icons.Filled.ChevronRight, contentDescription = null, Modifier.size(18.dp))
            }
        }

        Label(
            text = stringResource(R.string.original),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        )
        Label(
            text = stringResource(R.string.result),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
        )
    }
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = Color.Black.copy(alpha = 0.55f),
        contentColor = Color.White
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
