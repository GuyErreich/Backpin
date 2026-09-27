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
    fun freshDatabaseIsVersionTwoWithAScheduledDay() {
        val database =
            Room
                .inMemoryDatabaseBuilder(context, FocusStackDatabase::class.java)
                .allowMainThreadQueries()
                .addMigrations(*FocusStackDatabase.migrations)
                .build()
        val sqlite = database.openHelper.readableDatabase
        assertEquals(2, sqlite.version)
        assertEquals(1, FocusStackDatabase.migrations.size)
        val columns = mutableListOf<String>()
        sqlite.query("PRAGMA table_info(tasks)").use { cursor ->
            while (cursor.moveToNext()) columns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
        }
        assertTrue(columns.contains("scheduledOnEpochDay"))
        database.close()
    }

    @Test
    fun migrationFromVersionOneKeepsTasksAndAddsScheduledDay() {
        val name = "migrate-${UUID.randomUUID()}"
        val helper =
            androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
                androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration
                    .builder(context)
                    .name(name)
                    .callback(
                        object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                                db.execSQL(
                                    "CREATE TABLE IF NOT EXISTS `tasks` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                                        "`bucket` TEXT NOT NULL, `status` TEXT NOT NULL, `returnCue` TEXT, " +
                                        "`sortOrder` INTEGER NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, " +
                                        "`completedAtEpochMillis` INTEGER, PRIMARY KEY(`id`))",
                                )
                                db.execSQL(
                                    "CREATE TABLE IF NOT EXISTS `focus_frames` (`id` TEXT NOT NULL, `taskId` TEXT NOT NULL, " +
                                        "`parentFrameId` TEXT, `state` TEXT NOT NULL, `startedAtEpochMillis` INTEGER NOT NULL, " +
                                        "`pausedAtEpochMillis` INTEGER, `accumulatedMillis` INTEGER NOT NULL, " +
                                        "PRIMARY KEY(`id`), FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) " +
                                        "ON UPDATE NO ACTION ON DELETE NO ACTION )",
                                )
                                db.execSQL(
                                    "CREATE INDEX IF NOT EXISTS `index_focus_frames_taskId` ON `focus_frames` (`taskId`)",
                                )
                                db.execSQL(
                                    "CREATE INDEX IF NOT EXISTS `index_focus_frames_state` ON `focus_frames` (`state`)",
                                )
                                db.execSQL(
                                    "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
                                )
                                db.execSQL(
                                    "INSERT OR REPLACE INTO room_master_table (id,identity_hash) " +
                                        "VALUES(42, 'b2812a992a89874cef600aa086ad10d1')",
                                )
                                db.execSQL(
                                    "INSERT INTO tasks (id, title, bucket, status, returnCue, sortOrder, " +
                                        "createdAtEpochMillis, completedAtEpochMillis) " +
                                        "VALUES ('t1', 'Buy milk', 'INBOX', 'OPEN', NULL, 1, 1, NULL)",
                                )
                            }

                            override fun onUpgrade(
                                db: androidx.sqlite.db.SupportSQLiteDatabase,
                                oldVersion: Int,
                                newVersion: Int,
                            ) = Unit
                        },
                    ).build(),
            )
        helper.writableDatabase.close()
        val migrated =
            Room
                .databaseBuilder(context, FocusStackDatabase::class.java, name)
                .addMigrations(*FocusStackDatabase.migrations)
                .allowMainThreadQueries()
                .build()
        val sqlite = migrated.openHelper.readableDatabase
        assertEquals(2, sqlite.version)
        sqlite.query("SELECT title, scheduledOnEpochDay FROM tasks WHERE id = 't1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Buy milk", cursor.getString(0))
            assertTrue(cursor.isNull(1))
        }
        migrated.close()
        context.deleteDatabase(name)
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
            assertEquals(3, prefs.todayLimit)
            store.setTodayLimit(5)
            assertEquals(5, store.preferences.first().todayLimit)
            store.setTodayLimit(9)
            assertEquals(5, store.preferences.first().todayLimit)
            store.setTodayLimit(0)
            assertEquals(1, store.preferences.first().todayLimit)
        }

    @Test
    fun savedPhrasesSeedOnceAndKeepEdits() =
        runBlocking {
            val store = PreferencesDataStore(context, "phrases-${UUID.randomUUID()}")
            assertEquals(emptyList<String>(), store.preferences.first().savedPhrases)
            store.ensureDefaultsStored()
            assertEquals(listOf("Talk to my wife", "Talk to <name>"), store.preferences.first().savedPhrases)
            store.setSavedPhrases(listOf("Talk to <name>", "Call the school"))
            store.ensureDefaultsStored()
            assertEquals(listOf("Talk to <name>", "Call the school"), store.preferences.first().savedPhrases)
            store.setSavedPhrases(emptyList())
            store.ensureDefaultsStored()
            assertEquals(emptyList<String>(), store.preferences.first().savedPhrases)
        }
}
