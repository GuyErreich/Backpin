package app.backpin.focus.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 2 adds the scheduled day on a task. Register future [Migration] objects in
 * [migrations]. Destructive fallback is intentionally not enabled.
 */
@Database(
    entities = [TaskEntity::class, FocusFrameEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class FocusStackDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    abstract fun focusFrameDao(): FocusFrameDao

    companion object {
        const val NAME: String = "focus_stack.db"

        val MIGRATION_1_2: Migration =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `tasks` ADD COLUMN `scheduledOnEpochDay` INTEGER")
                }
            }

        val migrations: Array<Migration> = arrayOf(MIGRATION_1_2)

        fun create(context: Context): FocusStackDatabase =
            Room
                .databaseBuilder(context, FocusStackDatabase::class.java, NAME)
                .addMigrations(*migrations)
                .build()
    }
}
