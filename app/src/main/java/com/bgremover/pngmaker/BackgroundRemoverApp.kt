package com.bgremover.pngmaker

import android.app.Application
import android.content.ComponentCallbacks2
import android.util.Log
import com.bgremover.pngmaker.di.ServiceLocator
import com.bgremover.pngmaker.imaging.TempFiles
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BackgroundRemoverApp : Application() {

    private val handler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Background housekeeping failed", throwable)
    }
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + handler)

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.initialize(this)

        installLastResortExceptionHandler()

        // Working copies from a previous session are never needed again.
        appScope.launch {
            TempFiles.purgeStale(this@BackgroundRemoverApp)
            ServiceLocator.recentImagesRepository.load()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            ServiceLocator.releaseEngines()
        }
    }

    /**
     * Anything that escapes a coroutine or a UI callback is logged before the platform
     * handler runs, so a release crash is still diagnosable from a bug report.
     */
    private fun installLastResortExceptionHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception on ${thread.name}", throwable)
            runCatching { TempFiles.purgeStale(this) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        private const val TAG = "BackgroundRemoverApp"
    }
}
