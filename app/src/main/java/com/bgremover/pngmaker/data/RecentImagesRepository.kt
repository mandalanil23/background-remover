package com.bgremover.pngmaker.data

import android.content.Context
import android.util.Log
import com.bgremover.pngmaker.data.model.ProcessedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.util.UUID

/**
 * Keeps the most recent results so the user can find them again without hunting through
 * the gallery.
 *
 * Everything lives in the app's private `files/processed` directory plus a small JSON
 * index — nothing is written to shared storage until the user explicitly saves, and the
 * whole store is excluded from cloud backup.
 */
class RecentImagesRepository(private val context: Context) {

    private val _items = MutableStateFlow<List<ProcessedImage>>(emptyList())
    val items: StateFlow<List<ProcessedImage>> = _items.asStateFlow()

    private val processedDir: File
        get() = File(context.filesDir, DIR_NAME).apply { mkdirs() }

    private val indexFile: File
        get() = File(processedDir, INDEX_FILE)

    suspend fun load() = withContext(Dispatchers.IO) {
        _items.value = readIndex().filter { File(it.localPath).exists() }
    }

    /**
     * Copies [pngFile] into the private store and prepends it to the list.
     * Returns the stored entry, or null when persistence is disabled or fails.
     */
    suspend fun add(
        pngFile: File,
        fileName: String,
        width: Int,
        height: Int,
        savedUri: String?,
        sourceName: String?
    ): ProcessedImage? = withContext(Dispatchers.IO) {
        runCatching {
            val id = UUID.randomUUID().toString()
            val target = File(processedDir, "$id.png")
            pngFile.copyTo(target, overwrite = true)

            val entry = ProcessedImage(
                id = id,
                fileName = fileName,
                localPath = target.absolutePath,
                width = width,
                height = height,
                sizeBytes = target.length(),
                createdAt = System.currentTimeMillis(),
                savedUri = savedUri,
                sourceName = sourceName
            )

            val updated = (listOf(entry) + readIndex()).take(MAX_ENTRIES)
            writeIndex(updated)
            pruneOrphans(updated)
            _items.value = updated
            entry
        }.onFailure { Log.w(TAG, "Could not store recent image", it) }.getOrNull()
    }

    suspend fun updateSavedUri(id: String, savedUri: String) = withContext(Dispatchers.IO) {
        val updated = readIndex().map { if (it.id == id) it.copy(savedUri = savedUri) else it }
        writeIndex(updated)
        _items.value = updated
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        val remaining = readIndex().filterNot { entry ->
            if (entry.id == id) {
                runCatching { File(entry.localPath).delete() }
                true
            } else {
                false
            }
        }
        writeIndex(remaining)
        _items.value = remaining
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        runCatching { processedDir.listFiles()?.forEach { it.delete() } }
        writeIndex(emptyList())
        _items.value = emptyList()
    }

    private fun readIndex(): List<ProcessedImage> = runCatching {
        if (!indexFile.exists()) return@runCatching emptyList<ProcessedImage>()
        val array = JSONArray(indexFile.readText())
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                ProcessedImage.fromJson(obj)?.let { add(it) }
            }
        }
    }.onFailure { Log.w(TAG, "Recent index unreadable, starting fresh", it) }
        .getOrDefault(emptyList())

    private fun writeIndex(entries: List<ProcessedImage>) {
        runCatching {
            val array = JSONArray()
            entries.forEach { array.put(it.toJson()) }
            indexFile.writeText(array.toString())
        }.onFailure { Log.w(TAG, "Could not write recent index", it) }
    }

    /** Deletes PNGs that are no longer referenced by the index. */
    private fun pruneOrphans(entries: List<ProcessedImage>) {
        val keep = entries.map { File(it.localPath).name }.toSet() + INDEX_FILE
        runCatching {
            processedDir.listFiles()?.forEach { file ->
                if (file.name !in keep) file.delete()
            }
        }
    }

    companion object {
        private const val TAG = "RecentImages"
        private const val DIR_NAME = "processed"
        private const val INDEX_FILE = "index.json"
        private const val MAX_ENTRIES = 30
    }
}
