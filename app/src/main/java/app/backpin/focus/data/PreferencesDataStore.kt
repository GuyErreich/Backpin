package app.backpin.focus.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import app.backpin.focus.domain.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Stores the nudge preference shape. Reading it does not schedule work.
 */
class PreferencesDataStore(
    context: Context,
    storeName: String = STORE_NAME,
) {
    private val dataStore =
        PreferenceDataStoreFactory.create(
            produceFile = { context.applicationContext.preferencesDataStoreFile(storeName) },
        )

    val preferences: Flow<UserPreferences> =
        dataStore.data.map { prefs ->
            UserPreferences(
                nudgesEnabled = prefs[Keys.NUDGES_ENABLED] ?: false,
                nudgeMinutes = prefs[Keys.NUDGE_MINUTES] ?: DEFAULT_NUDGE_MINUTES,
                quietStart = prefs[Keys.QUIET_START],
                quietEnd = prefs[Keys.QUIET_END],
                currentNotificationEnabled = prefs[Keys.CURRENT_NOTIFICATION] ?: false,
            )
        }

    /** Writes the default shape so the keys exist even before a later settings pass. */
    suspend fun ensureDefaultsStored() {
        dataStore.edit { prefs ->
            if (Keys.NUDGES_ENABLED !in prefs) prefs[Keys.NUDGES_ENABLED] = false
            if (Keys.NUDGE_MINUTES !in prefs) prefs[Keys.NUDGE_MINUTES] = DEFAULT_NUDGE_MINUTES
            if (Keys.CURRENT_NOTIFICATION !in prefs) prefs[Keys.CURRENT_NOTIFICATION] = false
        }
    }

    private object Keys {
        val NUDGES_ENABLED = booleanPreferencesKey("nudges_enabled")
        val NUDGE_MINUTES = intPreferencesKey("nudge_minutes")
        val QUIET_START = stringPreferencesKey("quiet_start")
        val QUIET_END = stringPreferencesKey("quiet_end")
        val CURRENT_NOTIFICATION = booleanPreferencesKey("current_notification_enabled")
    }

    companion object {
        const val STORE_NAME: String = "focus_preferences"
        const val DEFAULT_NUDGE_MINUTES: Int = 30
    }
}
