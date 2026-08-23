package com.bgremover.pngmaker.ui

import android.app.Application
import android.graphics.Bitmap
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bgremover.pngmaker.R
import com.bgremover.pngmaker.di.ServiceLocator
import com.bgremover.pngmaker.imaging.CropCorner
import com.bgremover.pngmaker.imaging.CropGeometry
import com.bgremover.pngmaker.imaging.ImageCropper
import com.bgremover.pngmaker.imaging.NormalizedRect
import com.bgremover.pngmaker.util.AppError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** The aspect ratios offered on the crop screen. `pixelAspect` is width ÷ height. */
enum class CropAspect(@StringRes val labelRes: Int, val pixelAspect: Float?) {
    FREE(R.string.crop_free, null),
    SQUARE(R.string.crop_square, 1f),
    LANDSCAPE_4_3(R.string.crop_4_3, 4f / 3f),
    PORTRAIT_3_4(R.string.crop_3_4, 3f / 4f),
    LANDSCAPE_16_9(R.string.crop_16_9, 16f / 9f),
    PORTRAIT_9_16(R.string.crop_9_16, 9f / 16f)
}

data class CropUiState(
    val preview: Bitmap? = null,
    val rect: NormalizedRect = CropGeometry.FULL,
    val aspect: CropAspect = CropAspect.FREE,
    val quarters: Int = 0,
    val flipHorizontally: Boolean = false,
    val isLoading: Boolean = true,
    val isApplying: Boolean = false,
    val error: AppError? = null,
    val result: File? = null
) {
    val canApply: Boolean get() = preview != null && !isApplying && !isLoading

    /** Nothing to do when the frame is untouched and there is no rotation or mirror. */
    val isUntouched: Boolean
        get() = quarters == 0 && !flipHorizontally && CropGeometry.isFullFrame(rect)
}

/**
 * Owns the crop editor for one image.
 *
 * Two bitmaps are held: the decoded preview exactly as it came off disk, and the oriented
 * copy the user is looking at. Keeping the original means a rotation is a fresh transform
 * of pristine pixels rather than a transform of a transform, so four quarter-turns bring
 * back the image you started with instead of a slightly softer one.
 */
class CropViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(CropUiState())
    val state: StateFlow<CropUiState> = _state.asStateFlow()

    private val settings = ServiceLocator.settingsRepository

    private val context: Context get() = getApplication<Application>().applicationContext

    private var sourceUri: Uri? = null
    private var base: Bitmap? = null

    /** Idempotent — navigating back to a screen that is already loaded is a no-op. */
    fun start(uri: Uri) {
        if (sourceUri == uri && base != null) return
        sourceUri = uri
        releaseBitmaps()
        _state.value = CropUiState(isLoading = true)

        viewModelScope.launch {
            try {
                val decoded = ImageCropper.loadPreview(context, uri)
                base = decoded
                _state.value = CropUiState(
                    preview = decoded,
                    rect = CropGeometry.FULL,
                    isLoading = false
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (crop: ImageCropper.CropException) {
                _state.value = CropUiState(isLoading = false, error = crop.error)
            } catch (throwable: Throwable) {
                Log.e(TAG, "Could not open the image for cropping", throwable)
                _state.value = CropUiState(isLoading = false, error = AppError.from(throwable))
            }
        }
    }

    fun rotate(quarterTurns: Int) {
        val current = _state.value
        if (current.preview == null) return
        reorient(
            quarters = CropGeometry.normalizeQuarters(current.quarters + quarterTurns),
            flip = current.flipHorizontally
        )
    }

    fun flip() {
        val current = _state.value
        if (current.preview == null) return
        reorient(quarters = current.quarters, flip = !current.flipHorizontally)
    }

    fun setAspect(aspect: CropAspect) {
        val preview = _state.value.preview ?: return
        _state.value = _state.value.copy(
            aspect = aspect,
            rect = CropGeometry.centeredWithAspect(
                pixelAspect = aspect.pixelAspect,
                imageWidth = preview.width,
                imageHeight = preview.height
            )
        )
    }

    fun moveBy(dx: Float, dy: Float) {
        _state.value = _state.value.copy(rect = CropGeometry.move(_state.value.rect, dx, dy))
    }

    fun resizeBy(corner: CropCorner, dx: Float, dy: Float) {
        val current = _state.value
        val preview = current.preview ?: return
        _state.value = current.copy(
            rect = CropGeometry.resize(
                rect = current.rect,
                corner = corner,
                dx = dx,
                dy = dy,
                pixelAspect = current.aspect.pixelAspect,
                imageWidth = preview.width,
                imageHeight = preview.height
            )
        )
    }

    /** Back to the untouched frame — rotation and mirror included. */
    fun reset() {
        val current = _state.value
        if (current.preview == null) return
        if (current.quarters != 0 || current.flipHorizontally) {
            reorient(quarters = 0, flip = false, aspect = CropAspect.FREE)
        } else {
            _state.value = current.copy(rect = CropGeometry.FULL, aspect = CropAspect.FREE)
        }
    }

    fun apply() {
        val current = _state.value
        val uri = sourceUri ?: return
        if (!current.canApply) return

        _state.value = current.copy(isApplying = true, error = null)
        viewModelScope.launch {
            try {
                val maxPixels = settings.current().outputResolution.maxPixels
                val file = ImageCropper.apply(
                    context = context,
                    uri = uri,
                    maxPixels = maxPixels,
                    quarters = current.quarters,
                    flipHorizontally = current.flipHorizontally,
                    rect = current.rect
                )
                _state.value = _state.value.copy(isApplying = false, result = file)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (crop: ImageCropper.CropException) {
                _state.value = _state.value.copy(isApplying = false, error = crop.error)
            } catch (throwable: Throwable) {
                Log.e(TAG, "Crop failed", throwable)
                _state.value = _state.value.copy(
                    isApplying = false,
                    error = AppError.from(throwable)
                )
            }
        }
    }

    fun consumeResult() {
        if (_state.value.result != null) _state.value = _state.value.copy(result = null)
    }

    fun dismissError() {
        if (_state.value.error != null) _state.value = _state.value.copy(error = null)
    }

    private fun reorient(quarters: Int, flip: Boolean, aspect: CropAspect? = null) {
        val original = base ?: return
        val nextAspect = aspect ?: _state.value.aspect

        viewModelScope.launch {
            try {
                val oriented = withContext(Dispatchers.Default) {
                    ImageCropper.orient(original, quarters, flip)
                }
                // The rectangle is relative to the oriented image, so a turn that swaps
                // width and height invalidates it — start again from the full frame.
                _state.value = _state.value.copy(
                    preview = oriented,
                    quarters = quarters,
                    flipHorizontally = flip,
                    aspect = nextAspect,
                    rect = CropGeometry.centeredWithAspect(
                        pixelAspect = nextAspect.pixelAspect,
                        imageWidth = oriented.width,
                        imageHeight = oriented.height
                    )
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (crop: ImageCropper.CropException) {
                _state.value = _state.value.copy(error = crop.error)
            } catch (throwable: Throwable) {
                Log.e(TAG, "Could not rotate the preview", throwable)
                _state.value = _state.value.copy(error = AppError.from(throwable))
            }
        }
    }

    /**
     * Drops the references and lets the collector do the rest.
     *
     * Explicitly recycling would be tempting, but a bitmap handed to Compose can still be
     * on its way to the screen for a frame or two after the state that referenced it was
     * replaced — and drawing a recycled bitmap is a hard crash, not a glitch. At preview
     * size these are a few megabytes with a short life, which the collector handles.
     */
    private fun releaseBitmaps() {
        base = null
    }

    override fun onCleared() {
        super.onCleared()
        releaseBitmaps()
    }

    companion object {
        private const val TAG = "CropViewModel"
    }
}
