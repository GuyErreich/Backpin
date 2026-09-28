package app.backpin.focus.domain

import java.time.LocalTime

/**
 * Quiet hours may cross midnight. The end is exclusive.
 * A missing or equal bound means quiet hours are off.
 */
fun isQuietNow(
    start: String?,
    end: String?,
    now: LocalTime,
): Boolean {
    if (start.isNullOrBlank() || end.isNullOrBlank()) return false
    val quietStart = runCatching { LocalTime.parse(start) }.getOrNull() ?: return false
    val quietEnd = runCatching { LocalTime.parse(end) }.getOrNull() ?: return false
    if (quietStart == quietEnd) return false
    return if (quietStart.isBefore(quietEnd)) {
        !now.isBefore(quietStart) && now.isBefore(quietEnd)
    } else {
        !now.isBefore(quietStart) || now.isBefore(quietEnd)
    }
}
