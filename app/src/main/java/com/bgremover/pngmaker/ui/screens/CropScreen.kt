package com.bgremover.pngmaker.ui.screens

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bgremover.pngmaker.R
import com.bgremover.pngmaker.imaging.CropCorner
import com.bgremover.pngmaker.imaging.NormalizedRect
import com.bgremover.pngmaker.ui.CropAspect
import com.bgremover.pngmaker.ui.CropViewModel
import com.bgremover.pngmaker.ui.components.AppScaffold
import com.bgremover.pngmaker.ui.components.AuroraBackground
import com.bgremover.pngmaker.ui.components.ErrorDialog
import com.bgremover.pngmaker.ui.components.PrimaryActionButton
import com.bgremover.pngmaker.ui.theme.AppGradients
import kotlin.math.abs
import kotlin.math.min

/**
 * Crop and rotate, before any background removal happens.
 *
 * Composing the crop before the cut-out is deliberate: segmentation runs on fewer pixels,
 * and a subject the user has already framed gives the model less to be wrong about.
 */
@Composable
fun CropScreen(
    sourceUri: Uri?,
    onCropped: (Uri) -> Unit,
    onBack: () -> Unit,
    viewModel: CropViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(sourceUri) {
        sourceUri?.let(viewModel::start)
    }

    LaunchedEffect(state.result) {
        val file = state.result ?: return@LaunchedEffect
        viewModel.consumeResult()
        onCropped(Uri.fromFile(file))
    }

    AppScaffold(
        title = stringResource(R.string.crop_title),
        onBack = onBack,
        transparent = true,
        actions = {
            TextButton(onClick = viewModel::reset, enabled = !state.isUntouched) {
                Text(stringResource(R.string.crop_reset))
            }
        }
    ) { padding ->
        AuroraBackground(modifier = Modifier.padding(padding), intensity = 0.7f) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val preview = state.preview
                    when {
                        preview != null -> CropCanvas(
                            imageWidth = preview.width,
                            imageHeight = preview.height,
                            bitmap = preview.asImageBitmap(),
                            rect = state.rect,
                            onMove = viewModel::moveBy,
                            onResize = viewModel::resizeBy
                        )

                        state.isLoading -> CircularProgressIndicator()
                    }
                }

                Text(
                    text = stringResource(R.string.crop_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(Modifier.height(14.dp))

                AspectRow(
                    selected = state.aspect,
                    onSelect = viewModel::setAspect,
                    enabled = state.preview != null
                )

                Spacer(Modifier.height(10.dp))

                TransformRow(
                    enabled = state.preview != null,
                    onRotateLeft = { viewModel.rotate(-1) },
                    onRotateRight = { viewModel.rotate(1) },
                    onFlip = viewModel::flip
                )

                Spacer(Modifier.height(14.dp))

                PrimaryActionButton(
                    text = stringResource(
                        if (state.isApplying) R.string.crop_applying else R.string.crop_apply
                    ),
                    icon = Icons.Filled.Check,
                    onClick = viewModel::apply,
                    enabled = state.canApply,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    ErrorDialog(error = state.error, onDismiss = viewModel::dismissError)
}

@Composable
private fun AspectRow(
    selected: CropAspect,
    onSelect: (CropAspect) -> Unit,
    enabled: Boolean
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items = CropAspect.entries.toList(), key = { it.name }) { aspect ->
            FilterChip(
                selected = aspect == selected,
                enabled = enabled,
                onClick = { onSelect(aspect) },
                label = { Text(stringResource(aspect.labelRes)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = Color.White
                )
            )
        }
    }
}

@Composable
private fun TransformRow(
    enabled: Boolean,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onFlip: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        TransformAction(R.string.crop_rotate_left, enabled, onRotateLeft) {
            Icon(Icons.Filled.RotateLeft, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        TransformAction(R.string.crop_rotate_right, enabled, onRotateRight) {
            Icon(
                Icons.Filled.RotateRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
        TransformAction(R.string.crop_flip, enabled, onFlip) {
            Icon(Icons.Filled.Flip, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun TransformAction(
    labelRes: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    TextButton(onClick = onClick, enabled = enabled) {
        icon()
        Spacer(Modifier.width(6.dp))
        Text(stringResource(labelRes))
    }
}

/** Lays the preview out at its natural aspect and hands the overlay the exact same box. */
@Composable
private fun CropCanvas(
    imageWidth: Int,
    imageHeight: Int,
    bitmap: androidx.compose.ui.graphics.ImageBitmap,
    rect: NormalizedRect,
    onMove: (Float, Float) -> Unit,
    onResize: (CropCorner, Float, Float) -> Unit
) {
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (imageWidth <= 0 || imageHeight <= 0) return@BoxWithConstraints
        val scale = min(
            constraints.maxWidth.toFloat() / imageWidth,
            constraints.maxHeight.toFloat() / imageHeight
        )
        val widthDp = with(density) { (imageWidth * scale).toDp() }
        val heightDp = with(density) { (imageHeight * scale).toDp() }

        Box(modifier = Modifier.size(widthDp, heightDp)) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
            CropOverlay(
                rect = rect,
                onMove = onMove,
                onResize = onResize,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

private const val HANDLE_TOUCH_DP = 34
private const val HANDLE_ARM_FRACTION = 0.18f
private const val HANDLE_ARM_MAX_DP = 28

/**
 * The draggable frame.
 *
 * One gesture detector handles everything: on touch-down the nearest corner within reach
 * wins, otherwise a touch inside the window moves it. Deltas are converted to fractions of
 * the box before they leave here, so the view model never learns anything about pixels.
 */
@Composable
private fun CropOverlay(
    rect: NormalizedRect,
    onMove: (Float, Float) -> Unit,
    onResize: (CropCorner, Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentRect = rememberUpdatedState(rect)
    var activeCorner by remember { mutableStateOf<CropCorner?>(null) }
    var movingWindow by remember { mutableStateOf(false) }
    val touchSlop = with(LocalDensity.current) { HANDLE_TOUCH_DP.dp.toPx() }

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { start ->
                    val box = Size(size.width.toFloat(), size.height.toFloat())
                    val corner = cornerNear(start, currentRect.value, box, touchSlop)
                    activeCorner = corner
                    movingWindow = corner == null && contains(currentRect.value, start, box)
                },
                onDragEnd = {
                    activeCorner = null
                    movingWindow = false
                },
                onDragCancel = {
                    activeCorner = null
                    movingWindow = false
                },
                onDrag = { change, drag ->
                    change.consume()
                    if (size.width == 0 || size.height == 0) return@detectDragGestures
                    val dx = drag.x / size.width
                    val dy = drag.y / size.height
                    val corner = activeCorner
                    when {
                        corner != null -> onResize(corner, dx, dy)
                        movingWindow -> onMove(dx, dy)
                    }
                }
            )
        }
    ) {
        val left = currentRect.value.left * size.width
        val top = currentRect.value.top * size.height
        val right = currentRect.value.right * size.width
        val bottom = currentRect.value.bottom * size.height
        val scrim = Color.Black.copy(alpha = 0.55f)

        // Four rectangles rather than one path — cheaper, and no clipping needed.
        drawRect(scrim, topLeft = Offset.Zero, size = Size(size.width, top))
        drawRect(
            scrim,
            topLeft = Offset(0f, bottom),
            size = Size(size.width, size.height - bottom)
        )
        drawRect(scrim, topLeft = Offset(0f, top), size = Size(left, bottom - top))
        drawRect(
            scrim,
            topLeft = Offset(right, top),
            size = Size(size.width - right, bottom - top)
        )

        // Rule-of-thirds guides.
        val thirdsColor = Color.White.copy(alpha = 0.35f)
        for (step in 1..2) {
            val x = left + (right - left) * step / 3f
            val y = top + (bottom - top) * step / 3f
            drawLine(thirdsColor, Offset(x, top), Offset(x, bottom), strokeWidth = 1.dp.toPx())
            drawLine(thirdsColor, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
        }

        drawRect(
            color = Color.White.copy(alpha = 0.9f),
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Gradient corner brackets — the only place the brand ramp appears on this screen.
        val brush = AppGradients.sweep()
        val armMax = HANDLE_ARM_MAX_DP.dp.toPx()
        val arm = min(
            armMax,
            min(right - left, bottom - top) * HANDLE_ARM_FRACTION
        ).coerceAtLeast(1f)
        val thickness = 3.5.dp.toPx()

        fun bracket(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(brush, Offset(x, y), Offset(x + dx * arm, y), thickness, StrokeCap.Round)
            drawLine(brush, Offset(x, y), Offset(x, y + dy * arm), thickness, StrokeCap.Round)
        }
        bracket(left, top, 1f, 1f)
        bracket(right, top, -1f, 1f)
        bracket(left, bottom, 1f, -1f)
        bracket(right, bottom, -1f, -1f)
    }
}

private fun contains(rect: NormalizedRect, point: Offset, box: Size): Boolean =
    point.x >= rect.left * box.width &&
        point.x <= rect.right * box.width &&
        point.y >= rect.top * box.height &&
        point.y <= rect.bottom * box.height

/** The corner within [slop] of [point], or null when the touch was nowhere near one. */
private fun cornerNear(
    point: Offset,
    rect: NormalizedRect,
    box: Size,
    slop: Float
): CropCorner? {
    val left = rect.left * box.width
    val right = rect.right * box.width
    val top = rect.top * box.height
    val bottom = rect.bottom * box.height

    val candidates = listOf(
        CropCorner.TOP_LEFT to Offset(left, top),
        CropCorner.TOP_RIGHT to Offset(right, top),
        CropCorner.BOTTOM_LEFT to Offset(left, bottom),
        CropCorner.BOTTOM_RIGHT to Offset(right, bottom)
    )

    var best: CropCorner? = null
    var bestDistance = slop
    candidates.forEach { (corner, position) ->
        val distance = maxOf(abs(point.x - position.x), abs(point.y - position.y))
        if (distance <= bestDistance) {
            bestDistance = distance
            best = corner
        }
    }
    return best
}
