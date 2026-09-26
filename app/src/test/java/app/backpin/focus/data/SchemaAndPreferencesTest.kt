package app.backpin.focus.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.backpin.focus.domain.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SchemaAndPreferencesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun versionOneBaselineCreatesTaskAndFrameTables() {
        val database =
            Room
                .inMemoryDatabaseBuilder(context, FocusStackDatabase::class.java)
                .allowMainThreadQueries()
                .addMigrations(*FocusStackDatabase.migrations)
                .build()
        val sqlite = database.openHelper.readableDatabase
        assertEquals(1, sqlite.version)
        assertTrue(FocusStackDatabase.migrations.isEmpty())
        val names = mutableListOf<String>()
        sqlite.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            while (cursor.moveToNext()) names += cursor.getString(0)
        }
        assertTrue(names.contains("tasks"))
        assertTrue(names.contains("focus_frames"))
        database.close()
    }

    @Test
    fun preferenceShapeIsStoredAndDoesNotImplyNudges() =
        runBlocking {
            val store = PreferencesDataStore(context, "prefs-${UUID.randomUUID()}")
            store.ensureDefaultsStored()
            val prefs: UserPreferences = store.preferences.first()
            assertFalse(prefs.nudgesEnabled)
            assertEquals(30, prefs.nudgeMinutes)
            assertNull(prefs.quietStart)
            assertNull(prefs.quietEnd)
            assertFalse(prefs.currentNotificationEnabled)
        }
}
