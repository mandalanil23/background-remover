package com.bgremover.pngmaker.imaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import com.bgremover.pngmaker.util.AppError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Applies the crop screen's decisions to real pixels.
 *
 * The preview and the final render share [orient], which is the whole trick: whatever the
 * user sees while dragging is produced by the same transform that will be applied to the
 * full-resolution photo, so a rectangle chosen on a 2 MP preview lands exactly where it
 * looked like it would on a 12 MP original.
 *
 * The result is written as JPEG rather than PNG. This runs *before* segmentation, so there
 * is no alpha channel to protect yet, and a lossless 12 MP PNG would cost tens of megabytes
 * of cache and several seconds of encoding for no visible gain.
 */
object ImageCropper {

    /** Preview budget — big enough to judge an edge, small enough to rotate instantly. */
    const val PREVIEW_MAX_PIXELS = 2_400_000L

    private const val JPEG_QUALITY = 96
    private const val TAG = "ImageCropper"

    class CropException(val error: AppError) : Exception()

    /** Decodes a working-size copy for the editor. EXIF rotation is already applied. */
    suspend fun loadPreview(context: Context, uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        decodeOrThrow(context, uri, PREVIEW_MAX_PIXELS)
    }

    /**
     * Rotates by [quarters] × 90° and optionally mirrors.
     *
     * Flip is applied before rotation. That order is arbitrary but it is applied
     * identically to the preview and to the export, which is the only property that
     * matters — the user judges the result by eye, not by matrix convention.
     */
    fun orient(source: Bitmap, quarters: Int, flipHorizontally: Boolean): Bitmap {
        val turns = CropGeometry.normalizeQuarters(quarters)
        if (turns == 0 && !flipHorizontally) return source

        val matrix = Matrix()
        if (flipHorizontally) matrix.postScale(-1f, 1f)
        if (turns != 0) matrix.postRotate(90f * turns)

        return try {
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        } catch (oom: OutOfMemoryError) {
            Log.w(TAG, "Out of memory while orienting", oom)
            throw CropException(AppError.OutOfMemory)
        }
    }

    /**
     * Produces the cropped file the rest of the pipeline will treat as the source image.
     *
     * @param maxPixels the user's "maximum processing size" setting — cropping never
     *   decodes more than the app was already willing to decode.
     */
    suspend fun apply(
        context: Context,
        uri: Uri,
        maxPixels: Long,
        quarters: Int,
        flipHorizontally: Boolean,
        rect: NormalizedRect
    ): File = withContext(Dispatchers.IO) {
        val decoded = decodeOrThrow(context, uri, maxPixels)

        // `orient` returns the same instance when there is nothing to do, so the
        // intermediate is only recycled when a genuinely new bitmap was produced.
        val oriented = orient(decoded, quarters, flipHorizontally)
        if (oriented !== decoded) decoded.recycle()

        val window = CropGeometry.toPixels(
            rect = CropGeometry.sanitize(rect),
            imageWidth = oriented.width,
            imageHeight = oriented.height
        )

        val cropped = try {
            Bitmap.createBitmap(oriented, window.left, window.top, window.width, window.height)
        } catch (oom: OutOfMemoryError) {
            oriented.recycle()
            Log.w(TAG, "Out of memory while cropping", oom)
            throw CropException(AppError.OutOfMemory)
        } catch (illegal: IllegalArgumentException) {
            oriented.recycle()
            Log.w(TAG, "Crop window rejected: $window", illegal)
            throw CropException(AppError.InvalidImage)
        }
        if (cropped !== oriented) oriented.recycle()

        val estimatedBytes = cropped.width.toLong() * cropped.height.toLong()
        if (!TempFiles.hasFreeSpace(context, estimatedBytes)) {
            cropped.recycle()
            throw CropException(AppError.InsufficientStorage)
        }

        val target = TempFiles.newWorkingFile(context, suffix = ".jpg")
        try {
            FileOutputStream(target).use { stream ->
                if (!cropped.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)) {
                    throw CropException(AppError.SaveFailed)
                }
            }
        } catch (crop: CropException) {
            target.delete()
            throw crop
        } catch (throwable: Throwable) {
            target.delete()
            Log.w(TAG, "Could not write the cropped file", throwable)
            throw CropException(AppError.from(throwable))
        } finally {
            cropped.recycle()
        }

        target
    }

    private fun decodeOrThrow(context: Context, uri: Uri, maxPixels: Long): Bitmap = try {
        PhotoDecoder.decode(context, uri, maxPixels)
    } catch (decode: PhotoDecoder.DecodeException) {
        throw CropException(decode.error)
    } catch (oom: OutOfMemoryError) {
        throw CropException(AppError.OutOfMemory)
    } catch (throwable: Throwable) {
        Log.w(TAG, "Could not decode for cropping", throwable)
        throw CropException(AppError.from(throwable))
    }
}
