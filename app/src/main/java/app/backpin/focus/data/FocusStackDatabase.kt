package app.backpin.focus.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

/**
 * Version 1 is the migration baseline. Register future [Migration] objects in
 * [migrations]. Destructive fallback is intentionally not enabled.
 */
@Database(
    entities = [TaskEntity::class, FocusFrameEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class FocusStackDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    abstract fun focusFrameDao(): FocusFrameDao

    companion object {
        const val NAME: String = "focus_stack.db"

        val migrations: Array<Migration> = emptyArray()

        fun create(context: Context): FocusStackDatabase =
            Room
                .databaseBuilder(context, FocusStackDatabase::class.java, NAME)
                .addMigrations(*migrations)
                .build()
    }
}
