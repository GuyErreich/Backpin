// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE bucket = :bucket AND status = 'OPEN' ORDER BY sortOrder ASC")
    suspend fun openInBucket(bucket: String): List<TaskEntity>

    @Query("SELECT COUNT(*) FROM tasks WHERE bucket = 'TODAY' AND status = 'OPEN'")
    suspend fun countOpenToday(): Int

    /**
     * Open Today tasks that occupy a cap slot: excludes an open child of the
     * current frame (a nested interruption).
     */
    @Query(
        "SELECT COUNT(*) FROM tasks WHERE bucket = 'TODAY' AND status = 'OPEN' " +
            "AND id NOT IN (SELECT taskId FROM focus_frames " +
            "WHERE state != 'CLOSED' AND parentFrameId IS NOT NULL)",
    )
    suspend fun countedOpenToday(): Int

    @Query(
        "SELECT * FROM tasks WHERE bucket = 'TODAY' AND status = 'OPEN' " +
            "AND id NOT IN (SELECT taskId FROM focus_frames " +
            "WHERE state != 'CLOSED' AND parentFrameId IS NOT NULL) " +
            "ORDER BY sortOrder DESC LIMIT 1",
    )
    suspend fun newestCountedOpenToday(): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: String)
}
