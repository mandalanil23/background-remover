package com.bgremover.pngmaker.imaging

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import com.bgremover.pngmaker.util.AppError
import com.bgremover.pngmaker.util.sanitizeDisplayName
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Reads user-selected images safely.
 *
 * Everything here is defensive: a URI can be revoked, a "photo" can be a text file, and a
 * 108-megapixel panorama can arrive on a 2 GB phone. Failures surface as [DecodeException]
 * carrying a user-facing [AppError] instead of propagating raw platform exceptions.
 */
object PhotoDecoder {

    /** Formats we accept from the picker. Anything else is rejected early and politely. */
    val SUPPORTED_MIME_TYPES = setOf(
        "image/jpeg", "image/jpg", "image/png", "image/webp", "image/heic", "image/heif"
    )

    class DecodeException(val error: AppError) : Exception()

    /** Reads name/size/dimensions without allocating pixel memory. */
    fun readInfo(context: Context, uri: Uri): SourceImage {
        val resolver = context.contentResolver
        var displayName = "image"
        var sizeBytes = 0L

        runCatching {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                        displayName = sanitizeDisplayName(cursor.getString(nameIndex))
                    }
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        sizeBytes = cursor.getLong(sizeIndex)
                    }
                }
            }
        }

        // A `file://` URI — which is what the crop screen hands back — has no provider
        // behind it, so the query above returns nothing and `getType` returns null. Both
        // facts are readable straight off the file instead.
        var fileMimeType = ""
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            uri.path?.let(::File)?.takeIf { it.isFile }?.let { file ->
                displayName = sanitizeDisplayName(file.name)
                sizeBytes = file.length()
                fileMimeType = when (file.extension.lowercase()) {
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    "jpg", "jpeg" -> "image/jpeg"
                    else -> ""
                }
            }
        }

        val mimeType = resolver.getType(uri)?.lowercase().orEmpty().ifEmpty { fileMimeType }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val opened = runCatching {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        }
        if (opened.isFailure) throw DecodeException(AppError.InvalidImage)

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw DecodeException(AppError.InvalidImage)
        }
        if (mimeType.isNotEmpty() && mimeType !in SUPPORTED_MIME_TYPES) {
            throw DecodeException(AppError.UnsupportedFormat)
        }

        // Dimensions as the user sees them, i.e. after EXIF rotation.
        val rotation = readRotationDegrees(context, uri)
        val swapped = rotation == 90 || rotation == 270
        return SourceImage(
            uri = uri,
            displayName = displayName,
            mimeType = mimeType.ifEmpty { "image/*" },
            sizeBytes = sizeBytes,
            width = if (swapped) bounds.outHeight else bounds.outWidth,
            height = if (swapped) bounds.outWidth else bounds.outHeight
        )
    }

    /**
     * Decodes the image at the highest resolution that still fits [maxPixels], applying
     * EXIF rotation. Falls back to progressively smaller samples on [OutOfMemoryError] so a
     * huge photo degrades in quality instead of killing the process.
     */
    fun decode(context: Context, uri: Uri, maxPixels: Long): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.contentResolver.openInputStream(uri)
                ?.use { BitmapFactory.decodeStream(it, null, bounds) }
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw DecodeException(AppError.InvalidImage)
        }

        var sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxPixels)
        var lastError: Throwable? = null

        repeat(MAX_DECODE_ATTEMPTS) {
            try {
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inMutable = false
                }
                val decoded = context.contentResolver.openInputStream(uri)
                    ?.use { BitmapFactory.decodeStream(it, null, options) }
                    ?: throw DecodeException(AppError.InvalidImage)

                val rotated = applyExifRotation(context, uri, decoded)
                return ensureWithinBudget(rotated, maxPixels)
            } catch (oom: OutOfMemoryError) {
                lastError = oom
                sampleSize *= 2
                System.gc()
            } catch (decodeException: DecodeException) {
                throw decodeException
            } catch (throwable: Throwable) {
                lastError = throwable
                return@repeat
            }
        }

        throw DecodeException(
            if (lastError is OutOfMemoryError) AppError.OutOfMemory else AppError.InvalidImage
        )
    }

    /** Downscales a copy for the segmentation model; the original bitmap is untouched. */
    fun scaleForSegmentation(source: Bitmap, maxDimension: Int): Bitmap {
        val longest = max(source.width, source.height)
        if (longest <= maxDimension) return source
        val scale = maxDimension.toFloat() / longest
        val width = max(1, (source.width * scale).toInt())
        val height = max(1, (source.height * scale).toInt())
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    private fun ensureWithinBudget(bitmap: Bitmap, maxPixels: Long): Bitmap {
        val pixels = bitmap.width.toLong() * bitmap.height.toLong()
        if (pixels <= maxPixels) return bitmap
        val scale = sqrt(maxPixels.toDouble() / pixels).toFloat()
        val width = max(1, (bitmap.width * scale).toInt())
        val height = max(1, (bitmap.height * scale).toInt())
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        if (scaled != bitmap) bitmap.recycle()
        return scaled
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxPixels: Long): Int {
        var sampleSize = 1
        var pixels = width.toLong() * height.toLong()
        while (pixels / (sampleSize.toLong() * sampleSize.toLong()) > maxPixels) {
            sampleSize *= 2
        }
        return min(sampleSize, 32)
    }

    private fun readRotationDegrees(context: Context, uri: Uri): Int = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    }.getOrDefault(0)

    private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f); matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f); matrix.postScale(-1f, 1f)
            }

            else -> return bitmap
        }

        return runCatching {
            val rotated = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )
            if (rotated != bitmap) bitmap.recycle()
            rotated
        }.getOrDefault(bitmap)
    }

    private const val MAX_DECODE_ATTEMPTS = 4
}
