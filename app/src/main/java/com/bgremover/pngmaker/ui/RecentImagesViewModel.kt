package com.bgremover.pngmaker.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bgremover.pngmaker.data.model.ProcessedImage
import com.bgremover.pngmaker.di.ServiceLocator
import com.bgremover.pngmaker.imaging.ShareHelper
import com.bgremover.pngmaker.util.AppError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class RecentImagesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ServiceLocator.recentImagesRepository

    val items: StateFlow<List<ProcessedImage>> = repository.items

    private val _error = MutableStateFlow<AppError?>(null)
    val error: StateFlow<AppError?> = _error.asStateFlow()

    init {
        viewModelScope.launch { repository.load() }
    }

    fun refresh() = viewModelScope.launch { repository.load() }

    fun delete(id: String) = viewModelScope.launch { repository.remove(id) }

    fun clearAll() = viewModelScope.launch { repository.clearAll() }

    fun share(activityContext: Context, item: ProcessedImage) {
        runCatching { ShareHelper.sharePng(activityContext, File(item.localPath), item.fileName) }
            .onFailure { _error.value = AppError.ShareFailed }
    }

    fun open(activityContext: Context, item: ProcessedImage) {
        val uri = item.savedUri?.let { Uri.parse(it) }
            ?: runCatching { ShareHelper.contentUriFor(activityContext, File(item.localPath)) }
                .getOrNull()
        if (uri == null) {
            _error.value = AppError.ShareFailed
            return
        }
        runCatching { ShareHelper.viewImage(activityContext, uri) }
            .onFailure { _error.value = AppError.ShareFailed }
    }

    fun dismissError() {
        _error.value = null
    }
}
