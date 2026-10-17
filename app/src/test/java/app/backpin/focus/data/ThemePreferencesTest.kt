// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import app.backpin.focus.domain.HomeLayout
import app.backpin.focus.domain.ThemeId
import app.backpin.focus.themeqa.writeRaw
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ThemePreferencesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun themeIdAndHomeLayoutDefaultToSignalAndFlat() {
        runBlocking {
            val store = PreferencesDataStore(context, "theme-defaults-${UUID.randomUUID()}")
            assertEquals(ThemeId.SIGNAL, store.themeId.first())
            assertEquals(HomeLayout.FLAT, store.homeLayout.first())
            store.ensureDefaultsStored()
            assertEquals(ThemeId.SIGNAL, store.themeId.first())
            assertEquals(HomeLayout.FLAT, store.homeLayout.first())
        }
    }

    @Test
    fun themeIdAndHomeLayoutPersistOnTheStore() {
        runBlocking {
            val store = PreferencesDataStore(context, "theme-persist-${UUID.randomUUID()}")
            store.setThemeId(ThemeId.EMBER)
            store.setHomeLayout(HomeLayout.BANDS)
            assertEquals(ThemeId.EMBER, store.themeId.first())
            assertEquals(HomeLayout.BANDS, store.homeLayout.first())
            store.setThemeId(ThemeId.STILL_WATER)
            store.setHomeLayout(HomeLayout.FLAT)
            assertEquals(ThemeId.STILL_WATER, store.themeId.first())
            assertEquals(HomeLayout.FLAT, store.homeLayout.first())
        }
    }

    @Test
    fun storedThemeIdAndHomeLayoutSurviveANewDataStoreOnTheSameFile() {
        runBlocking {
            val name = "theme-recreate-${UUID.randomUUID()}"
            val original = PreferencesDataStore(context, name)
            original.setThemeId(ThemeId.CLEAR_LINE)
            original.setHomeLayout(HomeLayout.BANDS)
            assertEquals(ThemeId.CLEAR_LINE, original.themeId.first())
            val copyName = "theme-recreate-copy-${UUID.randomUUID()}"
            val source = context.preferencesDataStoreFile(name)
            val dest = context.preferencesDataStoreFile(copyName)
            dest.parentFile?.mkdirs()
            source.copyTo(dest, overwrite = true)
            val recreated = PreferencesDataStore(context, copyName)
            assertEquals(ThemeId.CLEAR_LINE, recreated.themeId.first())
            assertEquals(HomeLayout.BANDS, recreated.homeLayout.first())
        }
    }

    @Test
    fun unknownStoredThemeIdFallsBackToSignal() {
        runBlocking {
            val store = PreferencesDataStore(context, "theme-unknown-${UUID.randomUUID()}")
            store.ensureDefaultsStored()
            store.writeRaw("theme_id", "NOT_A_THEME")
            assertEquals(ThemeId.SIGNAL, store.themeId.first())
            store.writeRaw("home_layout", "GRID")
            assertEquals(HomeLayout.FLAT, store.homeLayout.first())
        }
    }
}
