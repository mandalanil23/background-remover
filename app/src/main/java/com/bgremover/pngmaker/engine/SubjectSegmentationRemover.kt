package com.bgremover.pngmaker.engine

import android.graphics.Bitmap
import android.util.Log
import com.bgremover.pngmaker.util.AppError
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.tasks.await

/**
 * General-purpose subject segmentation from Google Play services ML Kit: people, animals,
 * food, products — anything that reads as "the thing in front".
 *
 * The model is delivered by Play services, so it may be missing on a device that has never
 * been online. That case is reported as [AppError.ModelNeedsDownload] and the caller falls
 * back to the bundled person model.
 */
class SubjectSegmentationRemover : BackgroundRemover {

    override val id: String = ENGINE_ID

    private val segmenter: SubjectSegmenter by lazy {
        SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder()
                .enableForegroundConfidenceMask()
                .build()
        )
    }

    override suspend fun isAvailable(): Boolean = true

    override suspend fun segment(bitmap: Bitmap): SegmentationResult {
        val input = InputImage.fromBitmap(bitmap, 0)
        val result = try {
            segmenter.process(input).await()
        } catch (mlKitException: MlKitException) {
            throw SegmentationException(mapMlKitError(mlKitException), mlKitException)
        } catch (outOfMemory: OutOfMemoryError) {
            throw SegmentationException(AppError.OutOfMemory, null)
        } catch (throwable: Throwable) {
            throw SegmentationException(AppError.Unknown, throwable)
        }

        val buffer = result.foregroundConfidenceMask
            ?: throw SegmentationException(AppError.NoSubjectFound)

        val expected = bitmap.width * bitmap.height
        val mask = FloatArray(expected)
        buffer.rewind()
        val available = minOf(buffer.remaining(), expected)
        buffer.get(mask, 0, available)

        return SegmentationResult(mask, bitmap.width, bitmap.height)
    }

    override fun close() {
        runCatching { segmenter.close() }
            .onFailure { Log.w(TAG, "Failed to close subject segmenter", it) }
    }

    private fun mapMlKitError(exception: MlKitException): AppError = when (exception.errorCode) {
        MlKitException.UNAVAILABLE, MlKitException.NOT_FOUND -> AppError.ModelNeedsDownload
        MlKitException.NETWORK_ISSUE -> AppError.ModelNeedsDownload
        MlKitException.DEADLINE_EXCEEDED -> AppError.Timeout
        MlKitException.RESOURCE_EXHAUSTED -> AppError.OutOfMemory
        MlKitException.INVALID_ARGUMENT -> AppError.InvalidImage
        else -> AppError.ModelUnavailable
    }

    companion object {
        const val ENGINE_ID = "mlkit-subject"
        private const val TAG = "SubjectSegmenter"
    }
}
