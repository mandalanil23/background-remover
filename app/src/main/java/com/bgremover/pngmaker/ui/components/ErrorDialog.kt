package com.bgremover.pngmaker.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.bgremover.pngmaker.R
import com.bgremover.pngmaker.util.AppError

/**
 * The one place errors are shown. Every message is plain language with a clear next step —
 * the app never surfaces a stack trace or an error code.
 */
@Composable
fun ErrorDialog(
    error: AppError?,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)? = null
) {
    if (error == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text(stringResource(R.string.error_title)) },
        text = { Text(stringResource(error.messageRes)) },
        confirmButton = {
            if (onRetry != null && error.recoverable) {
                TextButton(onClick = {
                    onDismiss()
                    onRetry()
                }) { Text(stringResource(R.string.retry)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
            }
        },
        dismissButton = {
            if (onRetry != null && error.recoverable) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) }
            }
        },
        shape = MaterialTheme.shapes.large
    )
}
