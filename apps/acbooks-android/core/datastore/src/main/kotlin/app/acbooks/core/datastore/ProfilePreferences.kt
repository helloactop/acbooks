package app.acbooks.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.profileDataStore by preferencesDataStore(name = "profile")
private val nicknameKey = stringPreferencesKey("nickname")

class ProfilePreferences internal constructor(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.profileDataStore)

    val nickname: Flow<String?> = dataStore.data.map { it[nicknameKey] }

    suspend fun setNickname(value: String) {
        val nickname = value.trim()
        require(nickname.isNotEmpty())
        dataStore.edit { it[nicknameKey] = nickname }
    }
}
