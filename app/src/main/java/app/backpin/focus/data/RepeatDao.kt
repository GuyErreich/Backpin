// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RepeatDao {
    @Query("SELECT * FROM repeats WHERE archivedAtEpochMillis IS NULL ORDER BY sortOrder ASC")
    fun observeUnarchived(): Flow<List<RepeatEntity>>

    @Query("SELECT * FROM repeats WHERE archivedAtEpochMillis IS NULL ORDER BY sortOrder ASC")
    suspend fun unarchived(): List<RepeatEntity>

    @Query("SELECT * FROM repeats WHERE id = :id")
    suspend fun getById(id: String): RepeatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RepeatEntity)
}
