// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.themeqa

import android.content.Context
import android.os.Looper
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.backpin.focus.data.FocusRepositoryImpl
import app.backpin.focus.data.FocusStackDatabase
import app.backpin.focus.data.PreferencesDataStore
import app.backpin.focus.domain.AppClock
import app.backpin.focus.domain.FocusUseCases
import app.backpin.focus.repeats.RepeatsService
import app.backpin.focus.ui.FocusStackViewModel
import app.backpin.focus.ui.theme.ThemePalette
import org.robolectric.Shadows.shadowOf
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

internal class ThemeQaEnv(
    val database: FocusStackDatabase,
    val preferences: PreferencesDataStore,
    val repeats: RepeatsService,
    val store: ViewModelStore,
    val viewModel: FocusStackViewModel,
) {
    fun close() {
        store.clear()
        database.close()
    }
}

internal fun themeQaEnv(
    storeName: String = "theme-qa-${UUID.randomUUID()}",
    hour: Int = 8,
    minute: Int = 0,
    day: LocalDate = LocalDate.of(2026, 10, 16),
): ThemeQaEnv {
    val context: Context = ApplicationProvider.getApplicationContext()
    val millis = day.atTime(LocalTime.of(hour, minute)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val database =
        Room
            .inMemoryDatabaseBuilder(context, FocusStackDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    val preferences = PreferencesDataStore(context, storeName)
    val repository =
        FocusRepositoryImpl(
            database = database,
            preferencesStore = preferences,
            clock = AppClock { millis },
        )
    val useCases = FocusUseCases(repository)
    val repeats = RepeatsService(repository)
    val store = ViewModelStore()
    val viewModel =
        ViewModelProvider(
            store,
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return FocusStackViewModel(useCases, repeats) as T
                }
            },
        )[FocusStackViewModel::class.java]
    return ThemeQaEnv(database, preferences, repeats, store, viewModel)
}

internal fun AndroidComposeTestRule<*, *>.labelColor(text: String): Color {
    val layouts = mutableListOf<TextLayoutResult>()
    onNodeWithText(text, useUnmergedTree = true)
        .fetchSemanticsNode()
        .config[SemanticsActions.GetTextLayoutResult]
        .action
        ?.invoke(layouts)
    return layouts.single().layoutInput.style.color
}

internal fun AndroidComposeTestRule<*, *>.tap(label: String) {
    waitForIdle()
    val node = onNodeWithText(label)
    node.performScrollTo()
    node.assertIsEnabled().assertHasClickAction()
    node.performTouchInput { click(center) }
    waitForIdle()
    shadowOf(Looper.getMainLooper()).idle()
}

internal fun AndroidComposeTestRule<*, *>.awaitUntil(predicate: () -> Boolean) {
    waitUntil(timeoutMillis = 8_000) {
        waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        predicate()
    }
}

internal suspend fun PreferencesDataStore.writeRaw(
    key: String,
    value: String,
) {
    val field = PreferencesDataStore::class.java.getDeclaredField("dataStore")
    field.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val dataStore = field.get(this) as DataStore<Preferences>
    dataStore.edit { prefs: MutablePreferences ->
        prefs[stringPreferencesKey(key)] = value
    }
}

internal fun ThemePalette.roleColors(): Set<Color> =
    setOf(
        background,
        surface,
        surfaceRaised,
        outline,
        textPrimary,
        textSecondary,
        accent,
        onAccent,
        now,
        missed,
        done,
        danger,
    )

internal fun Color.rgbEquals(other: Color): Boolean =
    red == other.red && green == other.green && blue == other.blue

internal fun AndroidComposeTestRule<*, *>.allTextColors(): List<Pair<String, Color>> {
    val matcher =
        SemanticsMatcher("GetTextLayoutResult") { node ->
            node.config.contains(SemanticsActions.GetTextLayoutResult)
        }
    return onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().mapNotNull { node ->
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.singleOrNull() ?: return@mapNotNull null
        val color = layout.layoutInput.style.color
        if (color == Color.Unspecified || color == Color.Transparent) return@mapNotNull null
        layout.layoutInput.text.text.toString() to color
    }
}
