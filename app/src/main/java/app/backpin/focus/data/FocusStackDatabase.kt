package app.backpin.focus.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 2 adds the scheduled day on a task. Version 3 adds an optional time on that day.
 * Version 4 adds the last countdown interval so resume can offer it again.
 * Version 5 adds [repeats] and [repeat_log]. Repeats stay out of [tasks].
 * Register future [Migration] objects in [migrations]. Destructive fallback is intentionally not enabled.
 */
@Database(
    entities = [TaskEntity::class, FocusFrameEntity::class, RepeatEntity::class, RepeatLogEntity::class],
    version = 5,
    exportSchema = true,
)
abstract class FocusStackDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    abstract fun focusFrameDao(): FocusFrameDao

    abstract fun repeatDao(): RepeatDao

    abstract fun repeatLogDao(): RepeatLogDao

    companion object {
        const val NAME: String = "focus_stack.db"

        val MIGRATION_1_2: Migration =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `tasks` ADD COLUMN `scheduledOnEpochDay` INTEGER")
                }
            }

        val MIGRATION_2_3: Migration =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `tasks` ADD COLUMN `scheduledAtMinuteOfDay` INTEGER")
                }
            }

        val MIGRATION_3_4: Migration =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `tasks` ADD COLUMN `countdownIntervalDays` INTEGER")
                }
            }

        val MIGRATION_4_5: Migration =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `repeats` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                            "`band` TEXT NOT NULL, `everyNDays` INTEGER NOT NULL, `anchorEpochDay` INTEGER NOT NULL, " +
                            "`reminderOn` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                            "`createdAtEpochMillis` INTEGER NOT NULL, `archivedAtEpochMillis` INTEGER, " +
                            "PRIMARY KEY(`id`))",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `repeat_log` (`repeatId` TEXT NOT NULL, " +
                            "`epochDay` INTEGER NOT NULL, `outcome` TEXT NOT NULL, " +
                            "`atEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`repeatId`, `epochDay`), " +
                            "FOREIGN KEY(`repeatId`) REFERENCES `repeats`(`id`) " +
                            "ON UPDATE NO ACTION ON DELETE NO ACTION )",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_repeat_log_repeatId` ON `repeat_log` (`repeatId`)",
                    )
                }
            }

        val migrations: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)

        fun create(context: Context): FocusStackDatabase =
            Room
                .databaseBuilder(context, FocusStackDatabase::class.java, NAME)
                .addMigrations(*migrations)
                .build()
    }
}
