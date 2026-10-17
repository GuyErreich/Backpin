// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "focus_frames",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index("taskId"),
        Index("state"),
    ],
)
data class FocusFrameEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val parentFrameId: String?,
    val state: String,
    val startedAtEpochMillis: Long,
    val pausedAtEpochMillis: Long?,
    val accumulatedMillis: Long,
)
