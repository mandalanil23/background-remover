package com.bgremover.pngmaker.di

import android.content.Context
import com.bgremover.pngmaker.data.RecentImagesRepository
import com.bgremover.pngmaker.data.SettingsRepository
import com.bgremover.pngmaker.engine.BackgroundRemovalService
import com.bgremover.pngmaker.engine.EngineFactory

/**
 * Minimal hand-rolled dependency container.
 *
 * The app has four singletons and no build-time code generation, so a full DI framework
 * would add build complexity without buying anything. Everything here is created lazily
 * and holds only the application context.
 */
object ServiceLocator {

    @Volatile
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    private val requireContext: Context
        get() = appContext ?: error("ServiceLocator.initialize() was never called")

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(requireContext) }

    val recentImagesRepository: RecentImagesRepository by lazy {
        RecentImagesRepository(requireContext)
    }

    val engineFactory: EngineFactory by lazy { EngineFactory() }

    val removalService: BackgroundRemovalService by lazy {
        BackgroundRemovalService(requireContext, settingsRepository, engineFactory)
    }

    /** Called when the process is trimmed hard, to hand native model memory back. */
    fun releaseEngines() {
        engineFactory.releaseAll()
    }
}
