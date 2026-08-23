package com.bgremover.pngmaker.ui.screens

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bgremover.pngmaker.BuildConfig
import com.bgremover.pngmaker.R
import com.bgremover.pngmaker.data.model.EdgeSoftness
import com.bgremover.pngmaker.data.model.EngineMode
import com.bgremover.pngmaker.data.model.OutputResolution
import com.bgremover.pngmaker.data.model.ThemeMode
import com.bgremover.pngmaker.ui.SettingsViewModel
import com.bgremover.pngmaker.ui.components.AppScaffold
import com.bgremover.pngmaker.ui.components.SectionCard
import com.bgremover.pngmaker.ui.components.ThinDivider

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val cacheCleared by viewModel.cacheCleared.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val clearedMessage = stringResource(R.string.settings_cache_cleared)

    LaunchedEffect(cacheCleared) {
        if (cacheCleared) {
            snackbarHostState.showSnackbar(clearedMessage)
            viewModel.consumeCacheClearedNotice()
        }
    }

    AppScaffold(
        title = stringResource(R.string.settings),
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
            SectionCard(title = stringResource(R.string.settings_engine)) {
                Column(Modifier.selectableGroup()) {
                    ChoiceRow(
                        title = stringResource(R.string.settings_engine_auto),
                        subtitle = stringResource(R.string.settings_engine_auto_desc),
                        selected = settings.engineMode == EngineMode.AUTO,
                        onSelect = { viewModel.setEngineMode(EngineMode.AUTO) }
                    )
                    ChoiceRow(
                        title = stringResource(R.string.settings_engine_subject),
                        subtitle = stringResource(R.string.settings_engine_subject_desc),
                        selected = settings.engineMode == EngineMode.SUBJECT,
                        onSelect = { viewModel.setEngineMode(EngineMode.SUBJECT) }
                    )
                    ChoiceRow(
                        title = stringResource(R.string.settings_engine_person),
                        subtitle = stringResource(R.string.settings_engine_person_desc),
                        selected = settings.engineMode == EngineMode.PERSON,
                        onSelect = { viewModel.setEngineMode(EngineMode.PERSON) }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            SectionCard(title = stringResource(R.string.settings_quality)) {
                Column(Modifier.selectableGroup()) {
                    ChoiceRow(
                        title = stringResource(R.string.quality_original),
                        subtitle = null,
                        selected = settings.outputResolution == OutputResolution.ORIGINAL,
                        onSelect = { viewModel.setOutputResolution(OutputResolution.ORIGINAL) }
                    )
                    ChoiceRow(
                        title = stringResource(R.string.quality_balanced),
                        subtitle = null,
                        selected = settings.outputResolution == OutputResolution.BALANCED,
                        onSelect = { viewModel.setOutputResolution(OutputResolution.BALANCED) }
                    )
                    ChoiceRow(
                        title = stringResource(R.string.quality_fast),
                        subtitle = null,
                        selected = settings.outputResolution == OutputResolution.FAST,
                        onSelect = { viewModel.setOutputResolution(OutputResolution.FAST) }
                    )
                }
                ThinDivider()
                Text(
                    text = stringResource(R.string.settings_quality_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            SectionCard(title = stringResource(R.string.settings_edge_softness)) {
                Column(Modifier.selectableGroup()) {
                    ChoiceRow(
                        title = stringResource(R.string.edge_sharp),
                        subtitle = null,
                        selected = settings.edgeSoftness == EdgeSoftness.SHARP,
                        onSelect = { viewModel.setEdgeSoftness(EdgeSoftness.SHARP) }
                    )
                    ChoiceRow(
                        title = stringResource(R.string.edge_natural),
                        subtitle = null,
                        selected = settings.edgeSoftness == EdgeSoftness.NATURAL,
                        onSelect = { viewModel.setEdgeSoftness(EdgeSoftness.NATURAL) }
                    )
                    ChoiceRow(
                        title = stringResource(R.string.edge_soft),
                        subtitle = null,
                        selected = settings.edgeSoftness == EdgeSoftness.SOFT,
                        onSelect = { viewModel.setEdgeSoftness(EdgeSoftness.SOFT) }
                    )
                }
                ThinDivider()
                Text(
                    text = stringResource(R.string.settings_edge_softness_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            SectionCard(title = stringResource(R.string.settings_theme)) {
                Column(Modifier.selectableGroup()) {
                    ChoiceRow(
                        title = stringResource(R.string.settings_theme_system),
                        subtitle = null,
                        selected = settings.themeMode == ThemeMode.SYSTEM,
                        onSelect = { viewModel.setThemeMode(ThemeMode.SYSTEM) }
                    )
                    ChoiceRow(
                        title = stringResource(R.string.settings_theme_light),
                        subtitle = null,
                        selected = settings.themeMode == ThemeMode.LIGHT,
                        onSelect = { viewModel.setThemeMode(ThemeMode.LIGHT) }
                    )
                    ChoiceRow(
                        title = stringResource(R.string.settings_theme_dark),
                        subtitle = null,
                        selected = settings.themeMode == ThemeMode.DARK,
                        onSelect = { viewModel.setThemeMode(ThemeMode.DARK) }
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ThinDivider()
                    SwitchRow(
                        title = stringResource(R.string.settings_dynamic_color),
                        subtitle = stringResource(R.string.settings_dynamic_color_desc),
                        checked = settings.dynamicColor,
                        onCheckedChange = viewModel::setDynamicColor
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            SectionCard(title = stringResource(R.string.settings_storage)) {
                SwitchRow(
                    title = stringResource(R.string.settings_keep_recents),
                    subtitle = stringResource(R.string.settings_keep_recents_desc),
                    checked = settings.keepRecents,
                    onCheckedChange = viewModel::setKeepRecents
                )
                ThinDivider()
                ActionRow(
                    title = stringResource(R.string.settings_clear_cache),
                    subtitle = stringResource(R.string.settings_clear_cache_desc),
                    onClick = { viewModel.clearTemporaryFiles() }
                )
            }

            Spacer(Modifier.height(20.dp))

            SectionCard {
                ActionRow(
                    title = stringResource(R.string.privacy_policy),
                    subtitle = null,
                    onClick = onOpenPrivacy
                )
                ThinDivider()
                ActionRow(
                    title = stringResource(R.string.about),
                    subtitle = null,
                    onClick = onOpenAbout
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = stringResource(
                    R.string.version_format,
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}
