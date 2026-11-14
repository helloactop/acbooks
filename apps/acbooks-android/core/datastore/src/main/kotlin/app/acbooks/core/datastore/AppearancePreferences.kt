package app.acbooks.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appearanceDataStore by preferencesDataStore(name = "appearance")
private val themeModeKey = stringPreferencesKey("theme_mode")
private val accentKey = stringPreferencesKey("accent")

enum class ThemeMode(val storedValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
}

class AppearancePreferences internal constructor(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.appearanceDataStore)

    val themeMode: Flow<ThemeMode> = dataStore.data.map { preferences ->
        ThemeMode.entries.firstOrNull { it.storedValue == preferences[themeModeKey] }
            ?: ThemeMode.SYSTEM
    }

    val accent: Flow<String?> = dataStore.data.map { it[accentKey]?.takeIf(AccentHex::matches) }

    suspend fun setAccent(hex: String?) {
        require(hex == null || AccentHex.matches(hex))
        dataStore.edit {
            if (hex == null) it.remove(accentKey) else it[accentKey] = hex.uppercase()
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[themeModeKey] = mode.storedValue }
    }
}

private val AccentHex = Regex("#[0-9a-fA-F]{6}")
