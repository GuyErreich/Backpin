// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import app.backpin.focus.domain.BandTimes
import app.backpin.focus.domain.CountdownUnit
import app.backpin.focus.domain.DEFAULT_TODAY_LIMIT
import app.backpin.focus.domain.HomeLayout
import app.backpin.focus.domain.MAX_COUNTDOWN_DAYS
import app.backpin.focus.domain.MAX_TODAY_LIMIT
import app.backpin.focus.domain.MIN_COUNTDOWN_DAYS
import app.backpin.focus.domain.MINUTES_PER_DAY
import app.backpin.focus.domain.MIN_TODAY_LIMIT
import app.backpin.focus.domain.RepeatBand
import app.backpin.focus.domain.ThemeId
import app.backpin.focus.domain.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Stores nudge timing, quiet hours, the current-task notification preference,
 * the ordered capture phrases, the last countdown count used on this phone,
 * the named palette, the Home list layout, and the Coming up days/months unit.
 * Writing a value does not schedule work; the notification coordinator does that.
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
                lastCountdownDays =
                prefs[Keys.LAST_COUNTDOWN_DAYS]?.takeIf { it in MIN_COUNTDOWN_DAYS..MAX_COUNTDOWN_DAYS },
                bandTimes = readBandTimes(prefs),
                heldBandNudge = prefs[Keys.HELD_BAND_NUDGE]?.let { runCatching { RepeatBand.valueOf(it) }.getOrNull() },
                heldBandEpochDay = prefs[Keys.HELD_BAND_EPOCH_DAY],
                exactAlarmPromptDismissed = prefs[Keys.EXACT_ALARM_PROMPT_DISMISSED] ?: false,
            )
        }

    /**
     * Named palette stored on this phone.
     * Missing or unknown names are [ThemeId.SIGNAL].
     */
    val themeId: Flow<ThemeId> =
        dataStore.data.map { prefs ->
            prefs[Keys.THEME_ID]?.let { runCatching { ThemeId.valueOf(it) }.getOrNull() }
                ?: ThemeId.SIGNAL
        }

    /**
     * How Home lists tasks.
     * Missing or unknown names are [HomeLayout.FLAT].
     */
    val homeLayout: Flow<HomeLayout> =
        dataStore.data.map { prefs ->
            prefs[Keys.HOME_LAYOUT]?.let { runCatching { HomeLayout.valueOf(it) }.getOrNull() }
                ?: HomeLayout.FLAT
        }

    /**
     * How Coming up writes the wait until a day.
     * Missing or unknown names are [CountdownUnit.DAYS].
     */
    val countdownUnit: Flow<CountdownUnit> =
        dataStore.data.map { prefs ->
            prefs[Keys.COUNTDOWN_UNIT]?.let { runCatching { CountdownUnit.valueOf(it) }.getOrNull() }
                ?: CountdownUnit.DAYS
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
            if (Keys.BAND_MORNING_START !in prefs) {
                prefs[Keys.BAND_MORNING_START] = BandTimes.DEFAULT_MORNING_START_MINUTE
            }
            if (Keys.BAND_NOON_START !in prefs) {
                prefs[Keys.BAND_NOON_START] = BandTimes.DEFAULT_NOON_START_MINUTE
            }
            if (Keys.BAND_NIGHT_START !in prefs) {
                prefs[Keys.BAND_NIGHT_START] = BandTimes.DEFAULT_NIGHT_START_MINUTE
            }
            if (Keys.BAND_NIGHT_END !in prefs) {
                prefs[Keys.BAND_NIGHT_END] = BandTimes.DEFAULT_NIGHT_END_MINUTE
            }
            if (Keys.THEME_ID !in prefs) prefs[Keys.THEME_ID] = ThemeId.SIGNAL.name
            if (Keys.HOME_LAYOUT !in prefs) prefs[Keys.HOME_LAYOUT] = HomeLayout.FLAT.name
            if (Keys.COUNTDOWN_UNIT !in prefs) prefs[Keys.COUNTDOWN_UNIT] = CountdownUnit.DAYS.name
        }
    }

    /** Writes [id] by enum name. */
    suspend fun setThemeId(id: ThemeId) {
        dataStore.edit { prefs ->
            prefs[Keys.THEME_ID] = id.name
        }
    }

    /** Writes [layout] by enum name. */
    suspend fun setHomeLayout(layout: HomeLayout) {
        dataStore.edit { prefs ->
            prefs[Keys.HOME_LAYOUT] = layout.name
        }
    }

    /** Writes [unit] by enum name. */
    suspend fun setCountdownUnit(unit: CountdownUnit) {
        dataStore.edit { prefs ->
            prefs[Keys.COUNTDOWN_UNIT] = unit.name
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

    /** Remembers the last countdown count used on this phone. Out-of-range values are ignored. */
    suspend fun setLastCountdownDays(days: Int) {
        if (days !in MIN_COUNTDOWN_DAYS..MAX_COUNTDOWN_DAYS) return
        dataStore.edit { prefs ->
            prefs[Keys.LAST_COUNTDOWN_DAYS] = days
        }
    }

    /** Replaces the capture phrases. An empty list stays empty and is not seeded again. */
    suspend fun setSavedPhrases(phrases: List<String>) {
        dataStore.edit { prefs ->
            prefs[Keys.SAVED_PHRASES] = encodeSavedPhrases(phrases)
        }
    }

    /**
     * Writes user-set band times. Invalid or out-of-order values are ignored.
     * Returns true when the stored times changed.
     */
    suspend fun setBandTimes(times: BandTimes): Boolean {
        val stored = clampBandTimes(times) ?: return false
        dataStore.edit { prefs ->
            prefs[Keys.BAND_MORNING_START] = stored.morningStartMinute
            prefs[Keys.BAND_NOON_START] = stored.noonStartMinute
            prefs[Keys.BAND_NIGHT_START] = stored.nightStartMinute
            prefs[Keys.BAND_NIGHT_END] = stored.nightEndMinute
        }
        return true
    }

    /** Remembers a band nudge that should post once the active focus frame ends. */
    suspend fun setHeldBandNudge(
        band: RepeatBand?,
        epochDay: Long?,
    ) {
        dataStore.edit { prefs ->
            if (band == null || epochDay == null) {
                prefs.remove(Keys.HELD_BAND_NUDGE)
                prefs.remove(Keys.HELD_BAND_EPOCH_DAY)
            } else {
                prefs[Keys.HELD_BAND_NUDGE] = band.name
                prefs[Keys.HELD_BAND_EPOCH_DAY] = epochDay
            }
        }
    }

    /** Epoch day the quiet follow-up already posted for [band], or null. */
    suspend fun followUpPostedDay(band: RepeatBand): Long? {
        val key = followUpKey(band)
        return dataStore.data.map { it[key] }.first()
    }

    /** Marks that today's quiet follow-up for [band] has already posted. */
    suspend fun setFollowUpPostedDay(
        band: RepeatBand,
        epochDay: Long,
    ) {
        dataStore.edit { prefs ->
            prefs[followUpKey(band)] = epochDay
        }
    }

    /** One-time Settings "Not now" for the exact-alarm prompt. */
    suspend fun setExactAlarmPromptDismissed(dismissed: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.EXACT_ALARM_PROMPT_DISMISSED] = dismissed
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
        val LAST_COUNTDOWN_DAYS = intPreferencesKey("last_countdown_days")
        val BAND_MORNING_START = intPreferencesKey("band_morning_start")
        val BAND_NOON_START = intPreferencesKey("band_noon_start")
        val BAND_NIGHT_START = intPreferencesKey("band_night_start")
        val BAND_NIGHT_END = intPreferencesKey("band_night_end")
        val HELD_BAND_NUDGE = stringPreferencesKey("held_band_nudge")
        val HELD_BAND_EPOCH_DAY = longPreferencesKey("held_band_epoch_day")
        val FOLLOW_UP_MORNING = longPreferencesKey("follow_up_morning_day")
        val FOLLOW_UP_NOON = longPreferencesKey("follow_up_noon_day")
        val FOLLOW_UP_NIGHT = longPreferencesKey("follow_up_night_day")
        val EXACT_ALARM_PROMPT_DISMISSED = booleanPreferencesKey("exact_alarm_prompt_dismissed")
        val THEME_ID = stringPreferencesKey("theme_id")
        val HOME_LAYOUT = stringPreferencesKey("home_layout")
        val COUNTDOWN_UNIT = stringPreferencesKey("countdown_unit")
    }

    private fun followUpKey(band: RepeatBand) = when (band) {
        RepeatBand.MORNING -> Keys.FOLLOW_UP_MORNING
        RepeatBand.NOON -> Keys.FOLLOW_UP_NOON
        RepeatBand.NIGHT -> Keys.FOLLOW_UP_NIGHT
    }

    private fun readBandTimes(prefs: Preferences): BandTimes {
        val times =
            BandTimes(
                morningStartMinute = prefs[Keys.BAND_MORNING_START] ?: BandTimes.DEFAULT_MORNING_START_MINUTE,
                noonStartMinute = prefs[Keys.BAND_NOON_START] ?: BandTimes.DEFAULT_NOON_START_MINUTE,
                nightStartMinute = prefs[Keys.BAND_NIGHT_START] ?: BandTimes.DEFAULT_NIGHT_START_MINUTE,
                nightEndMinute = prefs[Keys.BAND_NIGHT_END] ?: BandTimes.DEFAULT_NIGHT_END_MINUTE,
            )
        return clampBandTimes(times) ?: BandTimes.DEFAULT
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
internal fun encodeSavedPhrases(phrases: List<String>): String = phrases
    .map { it.replace('\n', ' ').trim() }
    .filter { it.isNotEmpty() }
    .joinToString("\n")

/** Restores [encodeSavedPhrases]. A missing or empty value is an empty list. */
internal fun decodeSavedPhrases(stored: String?): List<String> {
    if (stored.isNullOrBlank()) return emptyList()
    return stored.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
}

/** Clamps each minute, then accepts the set only when morning < noon < night start < night end. */
internal fun clampBandTimes(times: BandTimes): BandTimes? {
    val clamped =
        BandTimes(
            morningStartMinute = times.morningStartMinute.coerceIn(0, MINUTES_PER_DAY),
            noonStartMinute = times.noonStartMinute.coerceIn(0, MINUTES_PER_DAY),
            nightStartMinute = times.nightStartMinute.coerceIn(0, MINUTES_PER_DAY),
            nightEndMinute = times.nightEndMinute.coerceIn(0, MINUTES_PER_DAY),
        )
    return clamped.takeIf { it.isValid() }
}
