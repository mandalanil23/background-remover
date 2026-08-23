package com.bgremover.pngmaker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bgremover.pngmaker.R
import com.bgremover.pngmaker.ui.EditorUiState
import com.bgremover.pngmaker.ui.components.AppScaffold
import com.bgremover.pngmaker.ui.components.BeforeAfterSlider
import com.bgremover.pngmaker.ui.components.CheckerboardBox
import com.bgremover.pngmaker.ui.components.ErrorDialog
import com.bgremover.pngmaker.ui.components.PrimaryActionButton
import com.bgremover.pngmaker.ui.components.SecondaryActionButton
import com.bgremover.pngmaker.ui.components.ZoomableImage
import com.bgremover.pngmaker.ui.components.rememberZoomState
import com.bgremover.pngmaker.util.formatDimensions

/**
 * Step 4: proof. The cut-out sits on a checkerboard so transparency is unmistakable, and
 * the compare mode wipes back to the untouched original.
 */
@Composable
fun PreviewScreen(
    state: EditorUiState,
    onContinue: () -> Unit,
    onProcessAnother: () -> Unit,
    onConsumeNotice: () -> Unit,
    onDismissError: () -> Unit,
    onBack: () -> Unit
) {
    val outcome = state.outcome
    val snackbarHostState = remember { SnackbarHostState() }
    var compareMode by remember { mutableStateOf(false) }
    val zoomState = rememberZoomState()

    val noticeText = state.notice?.let { stringResource(it) }
    LaunchedEffect(noticeText) {
        noticeText?.let {
            snackbarHostState.showSnackbar(it)
            onConsumeNotice()
        }
    }

    AppScaffold(
        title = stringResource(R.string.preview_title),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        actions = {
            IconButton(onClick = { zoomState.zoomBy(1.4f) }, enabled = !compareMode) {
                Icon(Icons.Filled.ZoomIn, stringResource(R.string.zoom_in))
            }
            IconButton(onClick = { zoomState.zoomBy(1f / 1.4f) }, enabled = !compareMode) {
                Icon(Icons.Filled.ZoomOut, stringResource(R.string.zoom_out))
            }
            IconButton(onClick = { zoomState.reset() }, enabled = !compareMode) {
                Icon(Icons.Filled.Refresh, stringResource(R.string.reset_view))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            if (outcome == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.error_generic),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
                return@Column
            }

            val resultImage = remember(outcome) { outcome.previewResult.asImageBitmap() }
            val originalImage = remember(outcome) { outcome.previewOriginal.asImageBitmap() }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !compareMode,
                    onClick = { compareMode = false },
                    label = { Text(stringResource(R.string.result)) }
                )
                FilterChip(
                    selected = compareMode,
                    onClick = { compareMode = true },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Compare,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text(stringResource(R.string.compare)) }
                )
            }

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                if (compareMode) {
                    BeforeAfterSlider(
                        original = originalImage,
                        result = resultImage,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CheckerboardBox(modifier = Modifier.fillMaxSize()) {
                        ZoomableImage(
                            image = resultImage,
                            contentDescription = stringResource(R.string.result),
                            zoomState = zoomState,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            AnimatedVisibility(visible = !compareMode) {
                Text(
                    text = stringResource(R.string.checkerboard_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = "${formatDimensions(outcome.width, outcome.height)} • PNG",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            PrimaryActionButton(
                text = stringResource(R.string.continue_to_save),
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            SecondaryActionButton(
                text = stringResource(R.string.process_another),
                onClick = onProcessAnother,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
        }
    }

    ErrorDialog(error = state.error, onDismiss = onDismissError)
}
