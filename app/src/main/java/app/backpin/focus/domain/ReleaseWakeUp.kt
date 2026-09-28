package app.backpin.focus.domain

/**
 * Asks for one inexact release check at a local moment. It may run a few minutes late.
 * Implementations must not use exact alarms.
 */
fun interface ReleaseWakeUp {
    fun wakeAt(epochMillis: Long)
}
