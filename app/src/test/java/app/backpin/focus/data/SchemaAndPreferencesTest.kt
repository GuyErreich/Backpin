// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.backpin.focus.domain.BandTimes
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
    fun freshDatabaseIsVersionFiveWithRepeatTables() {
        val database =
            Room
                .inMemoryDatabaseBuilder(context, FocusStackDatabase::class.java)
                .allowMainThreadQueries()
                .addMigrations(*FocusStackDatabase.migrations)
                .build()
        val sqlite = database.openHelper.readableDatabase
        assertEquals(5, sqlite.version)
        assertEquals(4, FocusStackDatabase.migrations.size)
        val columns = mutableListOf<String>()
        sqlite.query("PRAGMA table_info(tasks)").use { cursor ->
            while (cursor.moveToNext()) columns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
        }
        assertTrue(columns.contains("scheduledOnEpochDay"))
        assertTrue(columns.contains("scheduledAtMinuteOfDay"))
        assertTrue(columns.contains("countdownIntervalDays"))
        val tables = mutableListOf<String>()
        sqlite.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            while (cursor.moveToNext()) tables += cursor.getString(0)
        }
        assertTrue(tables.contains("repeats"))
        assertTrue(tables.contains("repeat_log"))
        database.close()
    }

    @Test
    fun migrationFromVersionThreeKeepsRowsAndAddsAnEmptyInterval() {
        val name = "migrate-v3-${UUID.randomUUID()}"
        val helper =
            androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
                androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration
                    .builder(context)
                    .name(name)
                    .callback(
                        object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(3) {
                            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                                db.execSQL(
                                    "CREATE TABLE IF NOT EXISTS `tasks` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                                        "`bucket` TEXT NOT NULL, `status` TEXT NOT NULL, `returnCue` TEXT, " +
                                        "`sortOrder` INTEGER NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, " +
                                        "`completedAtEpochMillis` INTEGER, `scheduledOnEpochDay` INTEGER, " +
                                        "`scheduledAtMinuteOfDay` INTEGER, PRIMARY KEY(`id`))",
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
                                        "VALUES(42, '4783373acecde80ad4f91a87acc46ba6')",
                                )
                                db.execSQL(
                                    "INSERT INTO tasks (id, title, bucket, status, returnCue, sortOrder, " +
                                        "createdAtEpochMillis, completedAtEpochMillis, scheduledOnEpochDay, " +
                                        "scheduledAtMinuteOfDay) " +
                                        "VALUES ('t1', 'Dana birthday', 'COUNTDOWN', 'OPEN', NULL, 1, 1, NULL, 20746, NULL)",
                                )
                                db.execSQL(
                                    "INSERT INTO tasks (id, title, bucket, status, returnCue, sortOrder, " +
                                        "createdAtEpochMillis, completedAtEpochMillis, scheduledOnEpochDay, " +
                                        "scheduledAtMinuteOfDay) " +
                                        "VALUES ('t2', 'Write notes', 'TODAY', 'OPEN', NULL, 2, 2, NULL, NULL, NULL)",
                                )
                                db.execSQL(
                                    "INSERT INTO focus_frames (id, taskId, parentFrameId, state, startedAtEpochMillis, " +
                                        "pausedAtEpochMillis, accumulatedMillis) VALUES ('f1', 't2', NULL, 'ACTIVE', 5, NULL, 0)",
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
        assertEquals(5, sqlite.version)
        sqlite
            .query(
                "SELECT title, bucket, scheduledOnEpochDay, countdownIntervalDays FROM tasks WHERE id = 't1'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Dana birthday", cursor.getString(0))
                assertEquals("COUNTDOWN", cursor.getString(1))
                assertEquals(20746L, cursor.getLong(2))
                assertTrue(cursor.isNull(3))
            }
        sqlite.query("SELECT COUNT(*) FROM tasks").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
        }
        sqlite.query("SELECT taskId, state FROM focus_frames WHERE id = 'f1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("t2", cursor.getString(0))
            assertEquals("ACTIVE", cursor.getString(1))
        }
        migrated.close()
        context.deleteDatabase(name)
    }

    @Test
    fun migrationFromVersionFourKeepsRowsAndAddsEmptyRepeatTables() {
        val name = "migrate-v4-${UUID.randomUUID()}"
        val helper =
            androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
                androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration
                    .builder(context)
                    .name(name)
                    .callback(
                        object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(4) {
                            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                                db.execSQL(
                                    "CREATE TABLE IF NOT EXISTS `tasks` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                                        "`bucket` TEXT NOT NULL, `status` TEXT NOT NULL, `returnCue` TEXT, " +
                                        "`sortOrder` INTEGER NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, " +
                                        "`completedAtEpochMillis` INTEGER, `scheduledOnEpochDay` INTEGER, " +
                                        "`scheduledAtMinuteOfDay` INTEGER, `countdownIntervalDays` INTEGER, " +
                                        "PRIMARY KEY(`id`))",
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
                                        "VALUES(42, 'cfd62a5c4c75fbd6ff949da239090139')",
                                )
                                db.execSQL(
                                    "INSERT INTO tasks (id, title, bucket, status, returnCue, sortOrder, " +
                                        "createdAtEpochMillis, completedAtEpochMillis, scheduledOnEpochDay, " +
                                        "scheduledAtMinuteOfDay, countdownIntervalDays) " +
                                        "VALUES ('t1', 'Dana birthday', 'COUNTDOWN', 'OPEN', NULL, 1, 1, NULL, 20746, NULL, 12)",
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
        assertEquals(5, sqlite.version)
        sqlite
            .query("SELECT title, countdownIntervalDays FROM tasks WHERE id = 't1'")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Dana birthday", cursor.getString(0))
                assertEquals(12, cursor.getInt(1))
            }
        sqlite.query("SELECT COUNT(*) FROM repeats").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        sqlite.query("SELECT COUNT(*) FROM repeat_log").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migrated.close()
        context.deleteDatabase(name)
    }

    @Test
    fun migrationFromVersionTwoKeepsRowsAndAddsAnEmptyTime() {
        val name = "migrate-v2-${UUID.randomUUID()}"
        val helper =
            androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
                androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration
                    .builder(context)
                    .name(name)
                    .callback(
                        object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(2) {
                            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                                db.execSQL(
                                    "CREATE TABLE IF NOT EXISTS `tasks` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                                        "`bucket` TEXT NOT NULL, `status` TEXT NOT NULL, `returnCue` TEXT, " +
                                        "`sortOrder` INTEGER NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, " +
                                        "`completedAtEpochMillis` INTEGER, `scheduledOnEpochDay` INTEGER, PRIMARY KEY(`id`))",
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
                                        "VALUES(42, 'cd911e664abd4f75af02a91c958887e0')",
                                )
                                db.execSQL(
                                    "INSERT INTO tasks (id, title, bucket, status, returnCue, sortOrder, " +
                                        "createdAtEpochMillis, completedAtEpochMillis, scheduledOnEpochDay) " +
                                        "VALUES ('t1', 'Call Dana', 'SCHEDULED', 'OPEN', 'Ask about Friday', 1, 1, NULL, 20734)",
                                )
                                db.execSQL(
                                    "INSERT INTO tasks (id, title, bucket, status, returnCue, sortOrder, " +
                                        "createdAtEpochMillis, completedAtEpochMillis, scheduledOnEpochDay) " +
                                        "VALUES ('t2', 'Write notes', 'TODAY', 'OPEN', NULL, 2, 2, NULL, NULL)",
                                )
                                db.execSQL(
                                    "INSERT INTO focus_frames (id, taskId, parentFrameId, state, startedAtEpochMillis, " +
                                        "pausedAtEpochMillis, accumulatedMillis) VALUES ('f1', 't2', NULL, 'ACTIVE', 5, NULL, 0)",
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
        assertEquals(5, sqlite.version)
        sqlite
            .query("SELECT title, bucket, returnCue, scheduledOnEpochDay, scheduledAtMinuteOfDay FROM tasks WHERE id = 't1'")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Call Dana", cursor.getString(0))
                assertEquals("SCHEDULED", cursor.getString(1))
                assertEquals("Ask about Friday", cursor.getString(2))
                assertEquals(20734L, cursor.getLong(3))
                assertTrue(cursor.isNull(4))
            }
        sqlite.query("SELECT COUNT(*) FROM tasks").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
        }
        sqlite.query("SELECT taskId, state FROM focus_frames WHERE id = 'f1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("t2", cursor.getString(0))
            assertEquals("ACTIVE", cursor.getString(1))
        }
        migrated.close()
        context.deleteDatabase(name)
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
        assertEquals(5, sqlite.version)
        sqlite.query("SELECT title, scheduledOnEpochDay, scheduledAtMinuteOfDay FROM tasks WHERE id = 't1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Buy milk", cursor.getString(0))
            assertTrue(cursor.isNull(1))
            assertTrue(cursor.isNull(2))
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
            assertNull(store.preferences.first().lastCountdownDays)
            store.setLastCountdownDays(12)
            assertEquals(12, store.preferences.first().lastCountdownDays)
            store.setLastCountdownDays(0)
            assertEquals(12, store.preferences.first().lastCountdownDays)
            assertEquals(7 * 60, store.preferences.first().bandTimes.morningStartMinute)
            assertEquals(12 * 60, store.preferences.first().bandTimes.noonStartMinute)
            assertEquals(18 * 60, store.preferences.first().bandTimes.nightStartMinute)
            assertEquals(23 * 60, store.preferences.first().bandTimes.nightEndMinute)
            store.setBandTimes(BandTimes(6 * 60, 11 * 60, 17 * 60, 22 * 60))
            assertEquals(6 * 60, store.preferences.first().bandTimes.morningStartMinute)
            assertFalse(store.preferences.first().exactAlarmPromptDismissed)
            store.setExactAlarmPromptDismissed(true)
            assertTrue(store.preferences.first().exactAlarmPromptDismissed)
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
