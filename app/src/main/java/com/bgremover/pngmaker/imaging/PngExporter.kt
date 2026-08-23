package com.bgremover.pngmaker.imaging

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import com.bgremover.pngmaker.util.AppError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.Locale

/**
 * Writes the finished PNG into the user's gallery.
 *
 * On Android 10+ this goes through MediaStore, which needs **no permission at all** and
 * places the file in `Pictures/Background Remover`. On Android 7-9 it falls back to the
 * legacy public directory, which is the only case where WRITE_EXTERNAL_STORAGE is needed.
 *
 * The bytes are copied verbatim from the already-encoded PNG, so nothing is re-compressed
 * and transparency is preserved exactly.
 */
object PngExporter {

    const val ALBUM_NAME = "Background Remover"
    private const val TAG = "PngExporter"
    private const val BUFFER_SIZE = 64 * 1024

    class ExportException(val error: AppError, cause: Throwable? = null) : Exception(cause)

    data class ExportResult(val uri: Uri, val displayName: String, val sizeBytes: Long)

    /** `IMG_2026_001.png` — stable, sortable and collision-free. */
    fun buildFileName(year: Int, index: Int): String =
        String.format(Locale.US, "IMG_%d_%03d.png", year, index)

    suspend fun saveToGallery(
        context: Context,
        sourceFile: File,
        fileName: String
    ): ExportResult = withContext(Dispatchers.IO) {
        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            throw ExportException(AppError.SaveFailed)
        }
        if (!TempFiles.hasFreeSpace(context, sourceFile.length())) {
            throw ExportException(AppError.InsufficientStorage)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStore(context, sourceFile, fileName)
            } else {
                saveToLegacyDirectory(context, sourceFile, fileName)
            }
        } catch (security: SecurityException) {
            throw ExportException(AppError.StoragePermission, security)
        } catch (io: IOException) {
            val message = io.message.orEmpty().lowercase()
            val error = if ("space" in message || "enospc" in message) {
                AppError.InsufficientStorage
            } else {
                AppError.SaveFailed
            }
            throw ExportException(error, io)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveViaMediaStore(
        context: Context,
        sourceFile: File,
        fileName: String
    ): ExportResult {
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/$ALBUM_NAME"
            )
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(collection, values)
            ?: throw ExportException(AppError.SaveFailed)

        try {
            resolver.openOutputStream(uri)?.use { output ->
                FileInputStream(sourceFile).use { input -> input.copyTo(output, BUFFER_SIZE) }
            } ?: throw ExportException(AppError.SaveFailed)

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (throwable: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw throwable
        }

        return ExportResult(uri, fileName, sourceFile.length())
    }

    @Suppress("DEPRECATION")
    private fun saveToLegacyDirectory(
        context: Context,
        sourceFile: File,
        fileName: String
    ): ExportResult {
        val picturesDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES
        )
        val albumDir = File(picturesDir, ALBUM_NAME)
        if (!albumDir.exists() && !albumDir.mkdirs()) {
            throw ExportException(AppError.StoragePermission)
        }

        var target = File(albumDir, fileName)
        var suffix = 1
        while (target.exists()) {
            target = File(albumDir, fileName.removeSuffix(".png") + "_$suffix.png")
            suffix++
        }

        FileInputStream(sourceFile).use { input ->
            target.outputStream().use { output -> input.copyTo(output, BUFFER_SIZE) }
        }

        // Make the new file visible to the gallery immediately.
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DATA, target.absolutePath)
            put(MediaStore.Images.Media.DISPLAY_NAME, target.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
        }
        val uri = runCatching {
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        }.getOrNull()

        if (uri == null) Log.w(TAG, "Saved to ${target.absolutePath} but MediaStore index failed")

        return ExportResult(uri ?: Uri.fromFile(target), target.name, target.length())
    }
}
