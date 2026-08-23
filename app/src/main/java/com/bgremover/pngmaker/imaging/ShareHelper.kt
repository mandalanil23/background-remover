package com.bgremover.pngmaker.imaging

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.bgremover.pngmaker.R
import com.bgremover.pngmaker.util.AppError
import java.io.File

/**
 * Hands the finished PNG to WhatsApp, Gmail, Telegram, Drive or anything else installed.
 *
 * The file is exposed through a FileProvider content URI with a one-shot read grant, so no
 * raw file path ever leaves the app and no storage permission is involved.
 */
object ShareHelper {

    class ShareException(val error: AppError = AppError.ShareFailed) : Exception()

    fun contentUriFor(context: Context, file: File): Uri {
        val shareDir = TempFiles.shareDir(context)
        val target = if (file.parentFile?.absolutePath == shareDir.absolutePath) {
            file
        } else {
            File(shareDir, file.name).also { copy -> file.copyTo(copy, overwrite = true) }
        }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            target
        )
    }

    fun sharePng(context: Context, file: File, fileName: String) {
        val uri = try {
            contentUriFor(context, renamedCopy(context, file, fileName))
        } catch (throwable: Throwable) {
            throw ShareException()
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = MIME_PNG
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri(fileName, uri)
        }

        val chooser = Intent.createChooser(intent, context.getString(R.string.share_via)).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(chooser)
        } catch (notFound: ActivityNotFoundException) {
            throw ShareException()
        }
    }

    /** Opens a saved gallery image in the user's photo viewer. */
    fun viewImage(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, MIME_PNG)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (notFound: ActivityNotFoundException) {
            throw ShareException()
        }
    }

    /** Shares under the user-visible file name rather than the internal scratch name. */
    private fun renamedCopy(context: Context, file: File, fileName: String): File {
        val shareDir = TempFiles.shareDir(context)
        val target = File(shareDir, fileName)
        if (target.absolutePath != file.absolutePath) {
            file.copyTo(target, overwrite = true)
        }
        return target
    }

    private const val MIME_PNG = "image/png"
}
