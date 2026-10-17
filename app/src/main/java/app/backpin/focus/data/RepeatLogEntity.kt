package app.backpin.focus.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "repeat_log",
    primaryKeys = ["repeatId", "epochDay"],
    foreignKeys = [
        ForeignKey(
            entity = RepeatEntity::class,
            parentColumns = ["id"],
            childColumns = ["repeatId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [Index("repeatId")],
)
data class RepeatLogEntity(
    val repeatId: String,
    val epochDay: Long,
    val outcome: String,
    val atEpochMillis: Long,
)
