package com.bgremover.pngmaker.engine

import android.graphics.Bitmap
import android.util.Log
import com.bgremover.pngmaker.util.AppError
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.tasks.await

/**
 * Bundled person segmentation. The model ships inside the APK, so this engine works on a
 * brand-new device with no network, no Play services model download and no account.
 *
 * It is the fallback whenever general subject segmentation is unavailable, and the primary
 * engine when the user picks "People (offline)" in Settings.
 */
class PersonSegmentationRemover : BackgroundRemover {

    override val id: String = ENGINE_ID

    private val segmenter: Segmenter by lazy {
        Segmentation.getClient(
            SelfieSegmenterOptions.Builder()
                .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                .build()
        )
    }

    override suspend fun isAvailable(): Boolean = true

    override suspend fun segment(bitmap: Bitmap): SegmentationResult {
        val input = InputImage.fromBitmap(bitmap, 0)
        val mask = try {
            segmenter.process(input).await()
        } catch (mlKitException: MlKitException) {
            throw SegmentationException(mapMlKitError(mlKitException), mlKitException)
        } catch (outOfMemory: OutOfMemoryError) {
            throw SegmentationException(AppError.OutOfMemory, null)
        } catch (throwable: Throwable) {
            throw SegmentationException(AppError.Unknown, throwable)
        }

        val width = mask.width
        val height = mask.height
        if (width <= 0 || height <= 0) throw SegmentationException(AppError.NoSubjectFound)

        // ML Kit hands back a ByteBuffer of little-endian floats, one per mask pixel.
        val buffer = mask.buffer
        buffer.rewind()
        val values = FloatArray(width * height)
        val count = minOf(values.size, buffer.remaining() / Float.SIZE_BYTES)
        for (i in 0 until count) {
            values[i] = buffer.float
        }

        return SegmentationResult(values, width, height)
    }

    override fun close() {
        runCatching { segmenter.close() }
            .onFailure { Log.w(TAG, "Failed to close person segmenter", it) }
    }

    private fun mapMlKitError(exception: MlKitException): AppError = when (exception.errorCode) {
        MlKitException.DEADLINE_EXCEEDED -> AppError.Timeout
        MlKitException.RESOURCE_EXHAUSTED -> AppError.OutOfMemory
        MlKitException.INVALID_ARGUMENT -> AppError.InvalidImage
        else -> AppError.Unknown
    }

    companion object {
        const val ENGINE_ID = "mlkit-person"
        private const val TAG = "PersonSegmenter"
    }
}
