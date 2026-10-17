// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RepeatLogDao {
    @Query("SELECT * FROM repeat_log")
    fun observeAll(): Flow<List<RepeatLogEntity>>

    @Query("SELECT * FROM repeat_log WHERE epochDay = :epochDay")
    suspend fun forDay(epochDay: Long): List<RepeatLogEntity>

    @Query("SELECT * FROM repeat_log WHERE repeatId = :repeatId AND epochDay = :epochDay")
    suspend fun get(
        repeatId: String,
        epochDay: Long,
    ): RepeatLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RepeatLogEntity)

    @Query("DELETE FROM repeat_log WHERE repeatId = :repeatId AND epochDay = :epochDay")
    suspend fun delete(
        repeatId: String,
        epochDay: Long,
    )
}
