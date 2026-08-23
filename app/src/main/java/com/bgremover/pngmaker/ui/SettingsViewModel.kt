package com.bgremover.pngmaker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bgremover.pngmaker.data.model.AppSettings
import com.bgremover.pngmaker.data.model.EdgeSoftness
import com.bgremover.pngmaker.data.model.EngineMode
import com.bgremover.pngmaker.data.model.OutputResolution
import com.bgremover.pngmaker.data.model.ThemeMode
import com.bgremover.pngmaker.di.ServiceLocator
import com.bgremover.pngmaker.imaging.TempFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ServiceLocator.settingsRepository

    val settings: StateFlow<AppSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings()
    )

    private val _cacheCleared = MutableStateFlow(false)
    val cacheCleared: StateFlow<Boolean> = _cacheCleared.asStateFlow()

    fun setEngineMode(mode: EngineMode) = viewModelScope.launch { repository.setEngineMode(mode) }

    fun setOutputResolution(value: OutputResolution) =
        viewModelScope.launch { repository.setOutputResolution(value) }

    fun setEdgeSoftness(value: EdgeSoftness) =
        viewModelScope.launch { repository.setEdgeSoftness(value) }

    fun setKeepRecents(value: Boolean) = viewModelScope.launch {
        repository.setKeepRecents(value)
        if (!value) ServiceLocator.recentImagesRepository.clearAll()
    }

    fun setThemeMode(value: ThemeMode) = viewModelScope.launch { repository.setThemeMode(value) }

    fun setDynamicColor(value: Boolean) =
        viewModelScope.launch { repository.setDynamicColor(value) }

    fun clearTemporaryFiles() = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            TempFiles.purgeAll(getApplication<Application>().applicationContext)
        }
        _cacheCleared.value = true
    }

    fun consumeCacheClearedNotice() {
        _cacheCleared.value = false
    }
}
