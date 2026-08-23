package com.bgremover.pngmaker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bgremover.pngmaker.data.model.AppSettings
import com.bgremover.pngmaker.data.model.EdgeSoftness
import com.bgremover.pngmaker.data.model.EngineMode
import com.bgremover.pngmaker.data.model.OutputResolution
import com.bgremover.pngmaker.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.Calendar

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Persists user preferences plus the running export counter used for unique file names.
 * A corrupted store degrades to defaults rather than crashing the app.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val ENGINE = stringPreferencesKey("engine_mode")
        val OUTPUT = stringPreferencesKey("output_resolution")
        val EDGE = stringPreferencesKey("edge_softness")
        val KEEP_RECENTS = booleanPreferencesKey("keep_recents")
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val EXPORT_COUNTER = intPreferencesKey("export_counter")
        val EXPORT_YEAR = intPreferencesKey("export_year")
    }

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { prefs ->
            AppSettings(
                engineMode = EngineMode.fromName(prefs[Keys.ENGINE]),
                outputResolution = OutputResolution.fromName(prefs[Keys.OUTPUT]),
                edgeSoftness = EdgeSoftness.fromName(prefs[Keys.EDGE]),
                keepRecents = prefs[Keys.KEEP_RECENTS] ?: true,
                themeMode = ThemeMode.fromName(prefs[Keys.THEME]),
                dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true
            )
        }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setEngineMode(mode: EngineMode) = updatePrefs { it[Keys.ENGINE] = mode.name }

    suspend fun setOutputResolution(value: OutputResolution) =
        updatePrefs { it[Keys.OUTPUT] = value.name }

    suspend fun setEdgeSoftness(value: EdgeSoftness) = updatePrefs { it[Keys.EDGE] = value.name }

    suspend fun setKeepRecents(value: Boolean) = updatePrefs { it[Keys.KEEP_RECENTS] = value }

    suspend fun setThemeMode(value: ThemeMode) = updatePrefs { it[Keys.THEME] = value.name }

    suspend fun setDynamicColor(value: Boolean) = updatePrefs { it[Keys.DYNAMIC_COLOR] = value }

    /**
     * Returns the next export index for the current year, e.g. 1 -> `IMG_2026_001.png`.
     * The counter resets automatically when the year rolls over.
     */
    suspend fun nextExportIndex(): Pair<Int, Int> {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        var index = 1
        context.dataStore.edit { prefs ->
            val storedYear = prefs[Keys.EXPORT_YEAR] ?: year
            val stored = if (storedYear == year) (prefs[Keys.EXPORT_COUNTER] ?: 0) else 0
            index = stored + 1
            prefs[Keys.EXPORT_COUNTER] = index
            prefs[Keys.EXPORT_YEAR] = year
        }
        return year to index
    }

    private suspend fun updatePrefs(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        runCatching { context.dataStore.edit(block) }
    }
}
