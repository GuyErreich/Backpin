package app.backpin.focus.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "repeats")
data class RepeatEntity(
    @PrimaryKey val id: String,
    val title: String,
    val band: String,
    val everyNDays: Int,
    val anchorEpochDay: Long,
    val reminderOn: Boolean,
    val sortOrder: Long,
    val createdAtEpochMillis: Long,
    val archivedAtEpochMillis: Long?,
)
