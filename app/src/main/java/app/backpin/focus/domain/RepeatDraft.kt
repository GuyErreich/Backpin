package app.backpin.focus.domain

/**
 * Prefill for the create/edit repeat sheet. [id] is null when creating.
 *
 * @property id Existing row, or null for a new repeat.
 * @property title Title field.
 * @property band Morning, noon, or night.
 * @property everyNDays 1 is every day.
 * @property reminderOn Whether the band reminder should fire.
 * @property sortOrder Display order among unarchived repeats.
 * @property anchorEpochDay First due day.
 */
data class RepeatDraft(
    val id: String? = null,
    val title: String = "",
    val band: RepeatBand = RepeatBand.MORNING,
    val everyNDays: Int = 1,
    val reminderOn: Boolean = true,
    val sortOrder: Long = 0L,
    val anchorEpochDay: Long = 0L,
) {
    /** True when this draft edits an existing row. */
    val editing: Boolean
        get() = id != null
}

/** Copies an existing repeat into a sheet draft. */
fun Repeat.toDraft(): RepeatDraft =
    RepeatDraft(
        id = id,
        title = title,
        band = band,
        everyNDays = everyNDays,
        reminderOn = reminderOn,
        sortOrder = sortOrder,
        anchorEpochDay = anchorEpochDay,
    )
