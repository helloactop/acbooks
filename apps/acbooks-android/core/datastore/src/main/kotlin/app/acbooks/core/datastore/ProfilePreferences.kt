package app.acbooks.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.profileDataStore by preferencesDataStore(name = "profile")
private val nicknameKey = stringPreferencesKey("nickname")
private val avatarRevisionKey = longPreferencesKey("avatar_revision")
private val avatarPendingKey = booleanPreferencesKey("avatar_pending")

class ProfilePreferences internal constructor(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.profileDataStore)

    val nickname: Flow<String?> = dataStore.data.map { it[nicknameKey] }

    val avatarRevision: Flow<Long?> = dataStore.data.map { it[avatarRevisionKey] }

    val avatarPending: Flow<Boolean> = dataStore.data.map { it[avatarPendingKey] ?: false }

    suspend fun setNickname(value: String) {
        val nickname = value.trim()
        require(nickname.isNotEmpty())
        dataStore.edit { it[nicknameKey] = nickname }
    }

    suspend fun setAvatarRevision(value: Long?) {
        dataStore.edit {
            if (value == null) it.remove(avatarRevisionKey) else it[avatarRevisionKey] = value
        }
    }

    suspend fun setAvatarPending(value: Boolean) {
        dataStore.edit {
            if (value) it[avatarPendingKey] = true else it.remove(avatarPendingKey)
        }
    }
}
