package com.bgremover.pngmaker.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bgremover.pngmaker.R
import com.bgremover.pngmaker.di.ServiceLocator
import com.bgremover.pngmaker.engine.RemovalOutcome
import com.bgremover.pngmaker.engine.RemovalStage
import com.bgremover.pngmaker.engine.SegmentationException
import com.bgremover.pngmaker.imaging.PhotoDecoder
import com.bgremover.pngmaker.imaging.PngExporter
import com.bgremover.pngmaker.imaging.ShareHelper
import com.bgremover.pngmaker.imaging.SourceImage
import com.bgremover.pngmaker.util.AppError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EditorUiState(
    val source: SourceImage? = null,
    val stage: RemovalStage? = null,
    val outcome: RemovalOutcome? = null,
    val exportFileName: String? = null,
    val savedUri: Uri? = null,
    val isSaving: Boolean = false,
    val error: AppError? = null,
    @StringRes val notice: Int? = null
) {
    val isProcessing: Boolean get() = stage != null && stage != RemovalStage.DONE
    val progress: Float get() = stage?.progress ?: 0f
    val hasResult: Boolean get() = outcome != null
}

/**
 * Owns the whole "one image at a time" workflow: selection, processing, preview, export.
 *
 * Held at the navigation-graph level so the user can move between the preview and the
 * save screen without re-running segmentation, and so a configuration change (rotation,
 * theme switch, font-scale change) never loses a finished result.
 */
class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private val removalService = ServiceLocator.removalService
    private val settings = ServiceLocator.settingsRepository
    private val recents = ServiceLocator.recentImagesRepository

    private var processingJob: Job? = null

    private val context: Context get() = getApplication<Application>().applicationContext

    /** Called when the user picks a file. Reads metadata only — no pixels yet. */
    fun onImageSelected(uri: Uri) {
        viewModelScope.launch {
            discardCurrentResult()
            val info = withContext(Dispatchers.IO) {
                runCatching { PhotoDecoder.readInfo(context, uri) }
            }
            info.onSuccess { source ->
                _state.value = EditorUiState(source = source)
            }.onFailure { throwable ->
                val error = (throwable as? PhotoDecoder.DecodeException)?.error
                    ?: AppError.from(throwable)
                _state.value = EditorUiState(error = error)
            }
        }
    }

    /** Runs segmentation. Safe to call repeatedly; a previous run is cancelled first. */
    fun process() {
        val source = _state.value.source ?: return
        processingJob?.cancel()
        _state.value = _state.value.copy(
            stage = RemovalStage.PREPARING,
            error = null,
            outcome = null,
            savedUri = null
        )

        processingJob = viewModelScope.launch {
            try {
                val outcome = removalService.removeBackground(source) { stage ->
                    _state.value = _state.value.copy(stage = stage)
                }
                val (year, index) = settings.nextExportIndex()
                val fileName = PngExporter.buildFileName(year, index)

                _state.value = _state.value.copy(
                    stage = RemovalStage.DONE,
                    outcome = outcome,
                    exportFileName = fileName,
                    error = null,
                    notice = if (outcome.wasDownscaled) R.string.error_too_large else null
                )

                if (settings.current().keepRecents) {
                    recents.add(
                        pngFile = outcome.resultFile,
                        fileName = fileName,
                        width = outcome.width,
                        height = outcome.height,
                        savedUri = null,
                        sourceName = source.displayName
                    )
                }
            } catch (cancellation: CancellationException) {
                _state.value = _state.value.copy(stage = null)
                throw cancellation
            } catch (segmentation: SegmentationException) {
                Log.w(TAG, "Segmentation failed", segmentation)
                _state.value = _state.value.copy(stage = null, error = segmentation.error)
            } catch (throwable: Throwable) {
                Log.e(TAG, "Unexpected processing failure", throwable)
                _state.value = _state.value.copy(stage = null, error = AppError.from(throwable))
            }
        }
    }

    fun cancelProcessing() {
        processingJob?.cancel()
        processingJob = null
        _state.value = _state.value.copy(stage = null)
    }

    /** True when this device needs the legacy write permission before saving. */
    fun requiresLegacyStoragePermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    fun savePng() {
        val current = _state.value
        val outcome = current.outcome ?: return
        val fileName = current.exportFileName ?: return
        if (current.isSaving) return

        _state.value = current.copy(isSaving = true, error = null)
        viewModelScope.launch {
            try {
                val result = PngExporter.saveToGallery(context, outcome.resultFile, fileName)
                _state.value = _state.value.copy(
                    isSaving = false,
                    savedUri = result.uri,
                    notice = R.string.saved_to
                )
                recents.items.value.firstOrNull { it.fileName == fileName }?.let { entry ->
                    recents.updateSavedUri(entry.id, result.uri.toString())
                }
            } catch (export: PngExporter.ExportException) {
                _state.value = _state.value.copy(isSaving = false, error = export.error)
            } catch (throwable: Throwable) {
                Log.e(TAG, "Save failed", throwable)
                _state.value = _state.value.copy(
                    isSaving = false,
                    error = AppError.from(throwable)
                )
            }
        }
    }

    fun share(activityContext: Context) {
        val current = _state.value
        val outcome = current.outcome ?: return
        val fileName = current.exportFileName ?: return
        try {
            ShareHelper.sharePng(activityContext, outcome.resultFile, fileName)
        } catch (share: ShareHelper.ShareException) {
            _state.value = current.copy(error = share.error)
        } catch (throwable: Throwable) {
            _state.value = current.copy(error = AppError.ShareFailed)
        }
    }

    fun openSavedImage(activityContext: Context) {
        val uri = _state.value.savedUri ?: return
        runCatching { ShareHelper.viewImage(activityContext, uri) }
            .onFailure { _state.value = _state.value.copy(error = AppError.ShareFailed) }
    }

    fun consumeNotice() {
        if (_state.value.notice != null) _state.value = _state.value.copy(notice = null)
    }

    fun dismissError() {
        if (_state.value.error != null) _state.value = _state.value.copy(error = null)
    }

    /** "Process another image" — clears state and removes the scratch file. */
    fun reset() {
        processingJob?.cancel()
        processingJob = null
        viewModelScope.launch { discardCurrentResult() }
        _state.value = EditorUiState()
    }

    private suspend fun discardCurrentResult() {
        val outcome = _state.value.outcome ?: return
        withContext(Dispatchers.IO) {
            runCatching { outcome.resultFile.delete() }
        }
    }

    override fun onCleared() {
        super.onCleared()
        processingJob?.cancel()
    }

    companion object {
        private const val TAG = "EditorViewModel"
    }
}
