package com.bgremover.pngmaker.imaging

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Owns every scratch file the app creates.
 *
 * Working copies live in the cache directory so Android can reclaim them under storage
 * pressure, and the app clears anything older than [MAX_AGE_MS] on every cold start. No
 * copy of a user's original photo is ever kept.
 */
object TempFiles {

    private const val WORKING_DIR = "working"
    private const val SHARE_DIR = "shared"
    private const val MAX_AGE_MS = 24 * 60 * 60 * 1000L
    private const val TAG = "TempFiles"

    fun workingDir(context: Context): File =
        File(context.cacheDir, WORKING_DIR).apply { mkdirs() }

    fun shareDir(context: Context): File =
        File(context.cacheDir, SHARE_DIR).apply { mkdirs() }

    fun newWorkingFile(context: Context, suffix: String = ".png"): File =
        File(workingDir(context), "work_${System.currentTimeMillis()}_${counter++}$suffix")

    /** Removes stale scratch files. Called once at application start. */
    fun purgeStale(context: Context) {
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        listOf(workingDir(context), shareDir(context)).forEach { dir ->
            runCatching {
                dir.listFiles()?.forEach { file ->
                    if (file.lastModified() < cutoff) file.delete()
                }
            }.onFailure { Log.w(TAG, "Could not purge ${dir.name}", it) }
        }
    }

    /** Removes every scratch file. Used by Settings → "Clear temporary files". */
    fun purgeAll(context: Context): Long {
        var freed = 0L
        listOf(workingDir(context), shareDir(context)).forEach { dir ->
            runCatching {
                dir.listFiles()?.forEach { file ->
                    freed += file.length()
                    file.delete()
                }
            }.onFailure { Log.w(TAG, "Could not clear ${dir.name}", it) }
        }
        return freed
    }

    /** True when the device has room for a file of roughly [requiredBytes]. */
    fun hasFreeSpace(context: Context, requiredBytes: Long): Boolean = runCatching {
        context.cacheDir.usableSpace > requiredBytes + SAFETY_MARGIN_BYTES
    }.getOrDefault(true)

    private const val SAFETY_MARGIN_BYTES = 20L * 1024 * 1024
    private var counter = 0
}
