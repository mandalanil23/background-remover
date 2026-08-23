package com.bgremover.pngmaker.engine

import com.bgremover.pngmaker.data.model.EngineMode

/**
 * Builds the ordered list of engines to try for a given user preference.
 *
 * This is the single place that knows which implementations exist. To add a remote
 * background-removal API later, implement [BackgroundRemover], add a case to [EngineMode],
 * and return it here — the rest of the app is engine-agnostic.
 *
 * Instances are cached because ML Kit clients are expensive to create and hold native
 * resources; [releaseAll] is called when the process no longer needs them.
 */
class EngineFactory {

    private val cache = mutableMapOf<String, BackgroundRemover>()

    @Synchronized
    fun enginesFor(mode: EngineMode): List<BackgroundRemover> = when (mode) {
        EngineMode.AUTO -> listOf(subject(), person())
        EngineMode.SUBJECT -> listOf(subject())
        EngineMode.PERSON -> listOf(person())
    }

    private fun subject(): BackgroundRemover =
        cache.getOrPut(SubjectSegmentationRemover.ENGINE_ID) { SubjectSegmentationRemover() }

    private fun person(): BackgroundRemover =
        cache.getOrPut(PersonSegmentationRemover.ENGINE_ID) { PersonSegmentationRemover() }

    @Synchronized
    fun releaseAll() {
        cache.values.forEach { runCatching { it.close() } }
        cache.clear()
    }
}
