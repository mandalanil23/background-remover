package com.bgremover.pngmaker.data.model

import org.json.JSONObject

/**
 * One entry in the "Recent images" list. The PNG itself lives in the app's private
 * `files/processed` directory; [savedUri] is set once the user exports it to the gallery.
 */
data class ProcessedImage(
    val id: String,
    val fileName: String,
    val localPath: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val createdAt: Long,
    val savedUri: String? = null,
    val sourceName: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put(KEY_ID, id)
        put(KEY_FILE_NAME, fileName)
        put(KEY_LOCAL_PATH, localPath)
        put(KEY_WIDTH, width)
        put(KEY_HEIGHT, height)
        put(KEY_SIZE, sizeBytes)
        put(KEY_CREATED_AT, createdAt)
        savedUri?.let { put(KEY_SAVED_URI, it) }
        sourceName?.let { put(KEY_SOURCE_NAME, it) }
    }

    companion object {
        private const val KEY_ID = "id"
        private const val KEY_FILE_NAME = "fileName"
        private const val KEY_LOCAL_PATH = "localPath"
        private const val KEY_WIDTH = "width"
        private const val KEY_HEIGHT = "height"
        private const val KEY_SIZE = "sizeBytes"
        private const val KEY_CREATED_AT = "createdAt"
        private const val KEY_SAVED_URI = "savedUri"
        private const val KEY_SOURCE_NAME = "sourceName"

        fun fromJson(json: JSONObject): ProcessedImage? {
            val id = json.optString(KEY_ID).takeIf { it.isNotEmpty() } ?: return null
            val path = json.optString(KEY_LOCAL_PATH).takeIf { it.isNotEmpty() } ?: return null
            return ProcessedImage(
                id = id,
                fileName = json.optString(KEY_FILE_NAME, "image.png"),
                localPath = path,
                width = json.optInt(KEY_WIDTH),
                height = json.optInt(KEY_HEIGHT),
                sizeBytes = json.optLong(KEY_SIZE),
                createdAt = json.optLong(KEY_CREATED_AT),
                savedUri = json.optString(KEY_SAVED_URI).takeIf { it.isNotEmpty() },
                sourceName = json.optString(KEY_SOURCE_NAME).takeIf { it.isNotEmpty() }
            )
        }
    }
}
