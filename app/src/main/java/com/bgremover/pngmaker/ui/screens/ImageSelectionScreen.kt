package com.bgremover.pngmaker.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bgremover.pngmaker.R
import com.bgremover.pngmaker.ui.EditorUiState
import com.bgremover.pngmaker.ui.components.AppScaffold
import com.bgremover.pngmaker.ui.components.ErrorDialog
import com.bgremover.pngmaker.ui.components.GradientBadge
import com.bgremover.pngmaker.ui.components.InfoRow
import com.bgremover.pngmaker.ui.components.PrimaryActionButton
import com.bgremover.pngmaker.ui.components.SectionCard
import com.bgremover.pngmaker.ui.components.ThinDivider
import com.bgremover.pngmaker.util.formatDimensions
import com.bgremover.pngmaker.util.formatFileSize
import com.bgremover.pngmaker.util.formatMegapixels

/**
 * Step 2 of the flow: pick a photo and confirm it before any processing starts.
 *
 * Both entry points are permission-free — the Android Photo Picker and the Storage Access
 * Framework each grant access to exactly the one file the user chose.
 */
@Composable
fun ImageSelectionScreen(
    state: EditorUiState,
    onImagePicked: (Uri) -> Unit,
    onStartProcessing: () -> Unit,
    onDismissError: () -> Unit,
    onBack: () -> Unit
) {
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(onImagePicked) }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onImagePicked) }

    val openGallery = {
        photoPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }
    val openFiles = {
        documentPicker.launch(arrayOf("image/*"))
    }

    AppScaffold(
        title = stringResource(R.string.selected_image),
        onBack = onBack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            val source = state.source

            if (source == null) {
                PickerOption(
                    icon = Icons.Filled.PhotoLibrary,
                    title = stringResource(R.string.choose_from_gallery),
                    subtitle = stringResource(R.string.choose_from_gallery_desc),
                    onClick = openGallery
                )
                Spacer(Modifier.height(12.dp))
                PickerOption(
                    icon = Icons.Filled.Folder,
                    title = stringResource(R.string.browse_files),
                    subtitle = stringResource(R.string.browse_files_desc),
                    onClick = openFiles
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.supported_formats),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(if (source.isLandscape) 4f / 3f else 3f / 4f)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    AsyncImage(
                        model = source.uri,
                        contentDescription = stringResource(R.string.selected_image),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(Modifier.height(16.dp))

                SectionCard {
                    InfoRow(label = stringResource(R.string.info_file), value = source.displayName)
                    ThinDivider()
                    InfoRow(
                        label = stringResource(R.string.info_dimensions),
                        value = formatDimensions(source.width, source.height)
                    )
                    ThinDivider()
                    InfoRow(
                        label = stringResource(R.string.info_resolution),
                        value = formatMegapixels(source.width, source.height)
                    )
                    ThinDivider()
                    InfoRow(label = stringResource(R.string.info_size), value = formatFileSize(source.sizeBytes))
                    ThinDivider()
                    InfoRow(label = stringResource(R.string.info_format), value = source.mimeType)
                }

                Spacer(Modifier.height(20.dp))

                PrimaryActionButton(
                    text = stringResource(R.string.remove_background),
                    onClick = onStartProcessing,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(onClick = openGallery) {
                        Icon(
                            Icons.Filled.SwapHoriz,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.change_image))
                    }
                }
            }
        }
    }

    ErrorDialog(error = state.error, onDismiss = onDismissError)
}

@Composable
private fun PickerOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GradientBadge(icon = icon)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
