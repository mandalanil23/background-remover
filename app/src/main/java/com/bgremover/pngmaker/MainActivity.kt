package com.bgremover.pngmaker

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bgremover.pngmaker.data.model.AppSettings
import com.bgremover.pngmaker.di.ServiceLocator
import com.bgremover.pngmaker.nav.AppNavHost
import com.bgremover.pngmaker.ui.theme.BackgroundRemoverTheme

/**
 * Single-activity host. Everything else is Compose.
 *
 * The activity also accepts images shared in from other apps (`ACTION_SEND`) and "open
 * with" intents (`ACTION_VIEW`), so the app is usable straight from the gallery.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settings by ServiceLocator.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())

            var pendingUri by remember { mutableStateOf(readImageUri(intent)) }

            BackgroundRemoverTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost(
                        initialImageUri = pendingUri,
                        onInitialImageConsumed = { pendingUri = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    /** Extracts an image URI from a share / view intent, ignoring anything else. */
    private fun readImageUri(intent: Intent?): Uri? {
        if (intent == null) return null
        val type = intent.type.orEmpty()
        if (!type.startsWith("image/") && type.isNotEmpty()) return null

        return when (intent.action) {
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                }
            }

            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
    }
}
