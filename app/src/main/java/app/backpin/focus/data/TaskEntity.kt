package app.backpin.focus.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val bucket: String,
    val status: String,
    val returnCue: String?,
    val sortOrder: Long,
    val createdAtEpochMillis: Long,
    val completedAtEpochMillis: Long?,
    val scheduledOnEpochDay: Long? = null,
    val scheduledAtMinuteOfDay: Int? = null,
    val countdownIntervalDays: Int? = null,
)
