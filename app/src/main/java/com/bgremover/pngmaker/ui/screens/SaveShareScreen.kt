package com.bgremover.pngmaker.ui.screens

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import com.bgremover.pngmaker.R
import com.bgremover.pngmaker.ui.EditorUiState
import com.bgremover.pngmaker.ui.components.AppScaffold
import com.bgremover.pngmaker.ui.components.CheckerboardBox
import com.bgremover.pngmaker.ui.components.ErrorDialog
import com.bgremover.pngmaker.ui.components.InfoRow
import com.bgremover.pngmaker.ui.components.PrimaryActionButton
import com.bgremover.pngmaker.ui.components.SecondaryActionButton
import com.bgremover.pngmaker.ui.components.SectionCard
import com.bgremover.pngmaker.ui.components.ThinDivider
import com.bgremover.pngmaker.util.formatDimensions
import com.bgremover.pngmaker.util.formatFileSize

/**
 * Step 5: keep it. Saving goes to `Pictures/Background Remover`; sharing hands the same
 * PNG to any installed app through a content URI.
 */
@Composable
fun SaveShareScreen(
    state: EditorUiState,
    onSave: () -> Unit,
    onShare: (Context) -> Unit,
    onOpenSaved: (Context) -> Unit,
    requiresLegacyPermission: Boolean,
    onProcessAnother: () -> Unit,
    onConsumeNotice: () -> Unit,
    onDismissError: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val outcome = state.outcome

    // Only Android 7-9 needs this; on 10+ the launcher is created but never used.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) onSave() }

    val startSave = {
        if (requiresLegacyPermission) {
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            onSave()
        }
    }

    val noticeText = state.notice?.let { stringResource(it) }
    LaunchedEffect(noticeText) {
        noticeText?.let {
            snackbarHostState.showSnackbar(it)
            onConsumeNotice()
        }
    }

    AppScaffold(
        title = stringResource(R.string.save_png),
        onBack = onBack,
        snackbarHostState = snackbarHostState
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            if (outcome == null) {
                Text(
                    text = stringResource(R.string.error_generic),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                return@Column
            }

            val resultImage = remember(outcome) { outcome.previewResult.asImageBitmap() }

            CheckerboardBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(MaterialTheme.shapes.large)
            ) {
                Image(
                    bitmap = resultImage,
                    contentDescription = stringResource(R.string.result),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(Modifier.height(16.dp))

            SectionCard {
                InfoRow(
                    label = stringResource(R.string.info_file),
                    value = state.exportFileName.orEmpty()
                )
                ThinDivider()
                InfoRow(
                    label = stringResource(R.string.info_dimensions),
                    value = formatDimensions(outcome.width, outcome.height)
                )
                ThinDivider()
                InfoRow(
                    label = stringResource(R.string.info_size),
                    value = formatFileSize(outcome.resultFile.length())
                )
                ThinDivider()
                InfoRow(
                    label = stringResource(R.string.info_transparency),
                    value = stringResource(R.string.info_transparency_value)
                )
            }

            Spacer(Modifier.height(20.dp))

            if (state.savedUri != null) {
                SavedBanner(onOpen = { onOpenSaved(context) })
                Spacer(Modifier.height(12.dp))
            }

            PrimaryActionButton(
                text = stringResource(R.string.save_png),
                icon = Icons.Filled.Download,
                onClick = startSave,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth()
            )

            if (state.isSaving) {
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }

            Spacer(Modifier.height(10.dp))

            SecondaryActionButton(
                text = stringResource(R.string.share),
                icon = Icons.Filled.Share,
                onClick = { onShare(context) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            SecondaryActionButton(
                text = stringResource(R.string.process_another),
                icon = Icons.Filled.Refresh,
                onClick = onProcessAnother,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    ErrorDialog(error = state.error, onDismiss = onDismissError)
}

@Composable
private fun SavedBanner(onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.saved_to),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onOpen) {
            Icon(
                Icons.Filled.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.open_saved_image))
        }
    }
}
