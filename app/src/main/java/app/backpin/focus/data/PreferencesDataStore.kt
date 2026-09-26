package app.backpin.focus.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import app.backpin.focus.domain.DEFAULT_TODAY_LIMIT
import app.backpin.focus.domain.MAX_TODAY_LIMIT
import app.backpin.focus.domain.MIN_TODAY_LIMIT
import app.backpin.focus.domain.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Stores nudge timing, quiet hours, the current-task notification preference,
 * and the ordered capture phrases. Writing a value does not schedule work;
 * the notification coordinator does that.
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
                lastInteractionAtEpochMillis = prefs[Keys.LAST_INTERACTION] ?: 0L,
                lastNudgedAtEpochMillis = prefs[Keys.LAST_NUDGED] ?: 0L,
                lastNudgedTaskId = prefs[Keys.LAST_NUDGED_TASK],
                todayLimit = (prefs[Keys.TODAY_LIMIT] ?: DEFAULT_TODAY_LIMIT).coerceIn(MIN_TODAY_LIMIT, MAX_TODAY_LIMIT),
                savedPhrases = decodeSavedPhrases(prefs[Keys.SAVED_PHRASES]),
            )
        }

    /** Writes the default shape so the keys exist even before a later settings pass. */
    suspend fun ensureDefaultsStored() {
        dataStore.edit { prefs ->
            if (Keys.NUDGES_ENABLED !in prefs) prefs[Keys.NUDGES_ENABLED] = false
            if (Keys.NUDGE_MINUTES !in prefs) prefs[Keys.NUDGE_MINUTES] = DEFAULT_NUDGE_MINUTES
            if (Keys.CURRENT_NOTIFICATION !in prefs) prefs[Keys.CURRENT_NOTIFICATION] = false
            if (Keys.TODAY_LIMIT !in prefs) prefs[Keys.TODAY_LIMIT] = DEFAULT_TODAY_LIMIT
            if (Keys.SAVED_PHRASES !in prefs) {
                prefs[Keys.SAVED_PHRASES] = encodeSavedPhrases(DEFAULT_SAVED_PHRASES)
            }
        }
    }

    suspend fun setNudge(
        enabled: Boolean,
        minutes: Int,
    ) {
        val storedMinutes = if (minutes in ALLOWED_NUDGE_MINUTES) minutes else DEFAULT_NUDGE_MINUTES
        dataStore.edit { prefs ->
            prefs[Keys.NUDGES_ENABLED] = enabled
            prefs[Keys.NUDGE_MINUTES] = storedMinutes
        }
    }

    suspend fun setQuietHours(
        start: String?,
        end: String?,
    ) {
        dataStore.edit { prefs ->
            if (start.isNullOrBlank() || end.isNullOrBlank()) {
                prefs.remove(Keys.QUIET_START)
                prefs.remove(Keys.QUIET_END)
            } else {
                prefs[Keys.QUIET_START] = start
                prefs[Keys.QUIET_END] = end
            }
        }
    }

    suspend fun setTodayLimit(limit: Int) {
        val stored = limit.coerceIn(MIN_TODAY_LIMIT, MAX_TODAY_LIMIT)
        dataStore.edit { prefs ->
            prefs[Keys.TODAY_LIMIT] = stored
        }
    }

    suspend fun setCurrentNotificationEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.CURRENT_NOTIFICATION] = enabled
        }
    }

    suspend fun setLastInteractionAt(epochMillis: Long) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_INTERACTION] = epochMillis
        }
    }

    suspend fun setLastNudge(
        epochMillis: Long,
        taskId: String,
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_NUDGED] = epochMillis
            prefs[Keys.LAST_NUDGED_TASK] = taskId
        }
    }

    /** Replaces the capture phrases. An empty list stays empty and is not seeded again. */
    suspend fun setSavedPhrases(phrases: List<String>) {
        dataStore.edit { prefs ->
            prefs[Keys.SAVED_PHRASES] = encodeSavedPhrases(phrases)
        }
    }

    private object Keys {
        val NUDGES_ENABLED = booleanPreferencesKey("nudges_enabled")
        val NUDGE_MINUTES = intPreferencesKey("nudge_minutes")
        val QUIET_START = stringPreferencesKey("quiet_start")
        val QUIET_END = stringPreferencesKey("quiet_end")
        val CURRENT_NOTIFICATION = booleanPreferencesKey("current_notification_enabled")
        val LAST_INTERACTION = longPreferencesKey("last_interaction_at")
        val LAST_NUDGED = longPreferencesKey("last_nudged_at")
        val LAST_NUDGED_TASK = stringPreferencesKey("last_nudged_task")
        val TODAY_LIMIT = intPreferencesKey("today_limit")
        val SAVED_PHRASES = stringPreferencesKey("saved_phrases")
    }

    companion object {
        const val STORE_NAME: String = "focus_preferences"
        const val DEFAULT_NUDGE_MINUTES: Int = 30
        val ALLOWED_NUDGE_MINUTES: Set<Int> = setOf(30, 60, 120)
        val DEFAULT_SAVED_PHRASES: List<String> =
            listOf(
                "Talk to my wife",
                "Talk to <name>",
            )
    }
}

/** One phrase per line, in display order. Blank lines are dropped. */
internal fun encodeSavedPhrases(phrases: List<String>): String =
    phrases
        .map { it.replace('\n', ' ').trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")

/** Restores [encodeSavedPhrases]. A missing or empty value is an empty list. */
internal fun decodeSavedPhrases(stored: String?): List<String> {
    if (stored.isNullOrBlank()) return emptyList()
    return stored.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
}
