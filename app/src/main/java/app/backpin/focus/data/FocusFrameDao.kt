// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusFrameDao {
    @Query("SELECT * FROM focus_frames")
    fun observeAll(): Flow<List<FocusFrameEntity>>

    @Query("SELECT * FROM focus_frames WHERE id = :id")
    suspend fun getById(id: String): FocusFrameEntity?

    @Query("SELECT * FROM focus_frames WHERE state = 'ACTIVE' LIMIT 1")
    suspend fun getActive(): FocusFrameEntity?

    @Query("SELECT COUNT(*) FROM focus_frames WHERE state = 'ACTIVE'")
    suspend fun countActive(): Int

    @Query(
        "SELECT * FROM focus_frames WHERE taskId = :taskId AND state != 'CLOSED' " +
            "ORDER BY startedAtEpochMillis DESC LIMIT 1",
    )
    suspend fun openFrameForTask(taskId: String): FocusFrameEntity?

    @Query("SELECT COUNT(*) FROM focus_frames WHERE parentFrameId = :frameId AND state != 'CLOSED'")
    suspend fun countOpenChildren(frameId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FocusFrameEntity)

    @Query("DELETE FROM focus_frames WHERE taskId = :taskId")
    suspend fun deleteForTask(taskId: String)
}
