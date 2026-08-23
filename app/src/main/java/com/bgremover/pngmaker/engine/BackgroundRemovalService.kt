package com.bgremover.pngmaker.engine

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.bgremover.pngmaker.data.SettingsRepository
import com.bgremover.pngmaker.data.model.AppSettings
import com.bgremover.pngmaker.imaging.MaskCompositor
import com.bgremover.pngmaker.imaging.PhotoDecoder
import com.bgremover.pngmaker.imaging.SourceImage
import com.bgremover.pngmaker.imaging.TempFiles
import com.bgremover.pngmaker.util.AppError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.max

/** Coarse stages, surfaced to the progress screen so the wait feels accountable. */
enum class RemovalStage(val progress: Float) {
    PREPARING(0.08f),
    ANALYSING(0.35f),
    REFINING(0.70f),
    FINISHING(0.92f),
    DONE(1f)
}

/**
 * The finished job. The full-resolution PNG lives on disk rather than in memory, so a
 * 16-megapixel cut-out costs ~0 heap while the user looks at the preview.
 */
data class RemovalOutcome(
    val resultFile: File,
    val previewOriginal: Bitmap,
    val previewResult: Bitmap,
    val width: Int,
    val height: Int,
    val engineId: String,
    val wasDownscaled: Boolean,
    val source: SourceImage
)

/**
 * Decode → segment → composite → write PNG.
 *
 * Runs entirely on [Dispatchers.Default]/[Dispatchers.IO]; the caller's coroutine can be
 * cancelled at any point and every stage checks for cancellation before doing more work.
 */
class BackgroundRemovalService(
    private val context: Context,
    private val settings: SettingsRepository,
    private val engines: EngineFactory
) {

    suspend fun removeBackground(
        source: SourceImage,
        onStage: (RemovalStage) -> Unit = {}
    ): RemovalOutcome = withContext(Dispatchers.Default) {
        val appSettings = settings.current()

        onStage(RemovalStage.PREPARING)
        coroutineContext.ensureActive()

        val budget = appSettings.outputResolution.maxPixels
        val wasDownscaled = source.pixelCount > budget

        val full = try {
            PhotoDecoder.decode(context, source.uri, budget)
        } catch (decodeError: PhotoDecoder.DecodeException) {
            throw SegmentationException(decodeError.error, decodeError)
        }

        var working: Bitmap? = null
        var composed: Bitmap? = null
        try {
            working = PhotoDecoder.scaleForSegmentation(full, SEGMENTATION_MAX_DIMENSION)

            onStage(RemovalStage.ANALYSING)
            coroutineContext.ensureActive()

            val segmentation = runEngines(appSettings, working)

            onStage(RemovalStage.REFINING)
            coroutineContext.ensureActive()

            val coverage = MaskCompositor.foregroundCoverage(segmentation.mask, segmentation.size)
            if (coverage < MIN_COVERAGE) throw SegmentationException(AppError.NoSubjectFound)

            composed = try {
                MaskCompositor.composite(
                    source = full,
                    mask = segmentation.mask,
                    maskWidth = segmentation.width,
                    maskHeight = segmentation.height,
                    softness = appSettings.edgeSoftness
                )
            } catch (outOfMemory: OutOfMemoryError) {
                throw SegmentationException(AppError.OutOfMemory)
            }

            onStage(RemovalStage.FINISHING)
            coroutineContext.ensureActive()

            val estimatedBytes = composed.width.toLong() * composed.height.toLong() * 4L
            if (!TempFiles.hasFreeSpace(context, estimatedBytes)) {
                throw SegmentationException(AppError.InsufficientStorage)
            }

            val outputFile = TempFiles.newWorkingFile(context)
            writePng(composed, outputFile)

            val previewResult = downscaleForPreview(composed)
            val previewOriginal = downscaleForPreview(full)

            onStage(RemovalStage.DONE)

            RemovalOutcome(
                resultFile = outputFile,
                previewOriginal = previewOriginal,
                previewResult = previewResult,
                width = composed.width,
                height = composed.height,
                engineId = segmentation.engineId,
                wasDownscaled = wasDownscaled,
                source = source
            )
        } catch (timeout: TimeoutCancellationException) {
            throw SegmentationException(AppError.Timeout, timeout)
        } finally {
            if (working != null && working !== full) working.recycle()
            composed?.recycle()
            full.recycle()
        }
    }

    /**
     * Tries each engine in preference order. A missing Play services model is not an error
     * the user should ever see — it just means the next engine takes over.
     */
    private suspend fun runEngines(
        appSettings: AppSettings,
        working: Bitmap
    ): EngineOutput {
        val candidates = engines.enginesFor(appSettings.engineMode)
        var lastError: SegmentationException? = null

        for (engine in candidates) {
            coroutineContext.ensureActive()
            try {
                if (!engine.isAvailable()) continue
                val result = withTimeout(SEGMENTATION_TIMEOUT_MS) { engine.segment(working) }
                return EngineOutput(result.mask, result.width, result.height, engine.id)
            } catch (timeout: TimeoutCancellationException) {
                lastError = SegmentationException(AppError.Timeout, timeout)
            } catch (failure: SegmentationException) {
                Log.w(TAG, "Engine ${engine.id} failed: ${failure.error}", failure)
                lastError = failure
            }
        }

        throw lastError ?: SegmentationException(AppError.Unknown)
    }

    private fun writePng(bitmap: Bitmap, file: File) {
        BufferedOutputStream(FileOutputStream(file), BUFFER_SIZE).use { stream ->
            // PNG ignores the quality argument and is always lossless — nothing is
            // re-compressed and the alpha channel is written verbatim.
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                throw SegmentationException(AppError.SaveFailed)
            }
            stream.flush()
        }
    }

    private fun downscaleForPreview(bitmap: Bitmap): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= PREVIEW_MAX_DIMENSION) return bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val scale = PREVIEW_MAX_DIMENSION.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            max(1, (bitmap.width * scale).toInt()),
            max(1, (bitmap.height * scale).toInt()),
            true
        )
    }

    private data class EngineOutput(
        val mask: FloatArray,
        val width: Int,
        val height: Int,
        val engineId: String
    ) {
        val size: Int get() = width * height

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is EngineOutput) return false
            return engineId == other.engineId && width == other.width &&
                height == other.height && mask.contentEquals(other.mask)
        }

        override fun hashCode(): Int =
            ((mask.contentHashCode() * 31 + width) * 31 + height) * 31 + engineId.hashCode()
    }

    companion object {
        private const val TAG = "BgRemovalService"

        /**
         * Segmentation runs on a copy no larger than this; the resulting mask is then
         * resampled back onto the full-resolution photo. 1536 px is the sweet spot between
         * mask detail and model latency on mid-range hardware.
         */
        const val SEGMENTATION_MAX_DIMENSION = 1536

        /** Preview bitmaps kept in memory for the before/after screen. */
        const val PREVIEW_MAX_DIMENSION = 1600

        private const val SEGMENTATION_TIMEOUT_MS = 45_000L
        private const val MIN_COVERAGE = 0.004f
        private const val BUFFER_SIZE = 64 * 1024
    }
}
