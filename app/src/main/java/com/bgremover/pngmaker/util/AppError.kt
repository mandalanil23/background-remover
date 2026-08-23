package com.bgremover.pngmaker.util

import androidx.annotation.StringRes
import com.bgremover.pngmaker.R

/**
 * Every failure the user can possibly see, mapped to a plain-language string.
 * Nothing in the app throws to the UI layer — failures always arrive as one of these.
 */
sealed class AppError(@StringRes val messageRes: Int, val recoverable: Boolean = true) {

    data object InvalidImage : AppError(R.string.error_invalid_image)
    data object UnsupportedFormat : AppError(R.string.error_unsupported_format)
    data object OutOfMemory : AppError(R.string.error_out_of_memory)
    data object NoSubjectFound : AppError(R.string.error_no_subject)
    data object Timeout : AppError(R.string.error_timeout)
    data object ModelUnavailable : AppError(R.string.error_model_unavailable)
    data object ModelNeedsDownload : AppError(R.string.error_no_network_for_model)
    data object StoragePermission : AppError(R.string.error_storage_permission)
    data object InsufficientStorage : AppError(R.string.error_insufficient_storage)
    data object SaveFailed : AppError(R.string.error_save_failed)
    data object ShareFailed : AppError(R.string.error_share_failed)
    data object Unknown : AppError(R.string.error_generic)

    companion object {
        /**
         * Last line of defence: turns any unexpected throwable into a friendly message
         * instead of letting it reach the default crash handler.
         */
        fun from(throwable: Throwable?): AppError = when (throwable) {
            null -> Unknown
            is OutOfMemoryError -> OutOfMemory
            is kotlinx.coroutines.TimeoutCancellationException -> Timeout
            is java.io.FileNotFoundException -> InvalidImage
            is SecurityException -> StoragePermission
            is java.io.IOException -> {
                val message = throwable.message.orEmpty().lowercase()
                if ("space" in message || "enospc" in message) InsufficientStorage else SaveFailed
            }

            else -> Unknown
        }
    }
}
