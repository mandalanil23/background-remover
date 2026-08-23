package com.bgremover.pngmaker.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bgremover.pngmaker.R
import com.bgremover.pngmaker.ui.components.AppScaffold

/**
 * The full policy, shipped inside the app so it is available offline and reviewable by
 * Play without following a link. The same text lives in `PRIVACY_POLICY.md` for hosting.
 */
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    val sections = listOf(
        R.string.privacy_h_collect to R.string.privacy_b_collect,
        R.string.privacy_h_images to R.string.privacy_b_images,
        R.string.privacy_h_storage to R.string.privacy_b_storage,
        R.string.privacy_h_permissions to R.string.privacy_b_permissions,
        R.string.privacy_h_thirdparty to R.string.privacy_b_thirdparty,
        R.string.privacy_h_children to R.string.privacy_b_children,
        R.string.privacy_h_changes to R.string.privacy_b_changes,
        R.string.privacy_h_contact to R.string.privacy_b_contact
    )

    AppScaffold(
        title = stringResource(R.string.privacy_policy),
        onBack = onBack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.app_full_name),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.privacy_updated),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.privacy_intro),
                style = MaterialTheme.typography.bodyLarge
            )

            sections.forEach { (heading, body) ->
                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(heading),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(body),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}
