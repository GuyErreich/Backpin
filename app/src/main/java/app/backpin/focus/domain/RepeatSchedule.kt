// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.domain

/**
 * Rebuilds the next band start, follow-up, and end alarms.
 * One alarm per band event, never per repeat.
 */
fun interface RepeatScheduleSync {
    fun resync()
}

/** Whether the OS will accept an exact-and-allow-while-idle alarm. */
fun interface ExactAlarmAccess {
    fun canScheduleExactAlarms(): Boolean
}
