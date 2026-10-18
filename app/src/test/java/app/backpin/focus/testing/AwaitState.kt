// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.testing

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Waits until [predicate] matches a value. Skips stale emissions. Does not
 * count events. On timeout, fails with the last value seen. No
 * [Thread.sleep] and no fixed [kotlinx.coroutines.delay].
 */
suspend fun <T> Flow<T>.awaitState(
    timeout: Duration = 5.seconds,
    predicate: (T) -> Boolean,
): T {
    var last: T? = null
    var seen = false
    return try {
        withTimeout(timeout) {
            first { value ->
                last = value
                seen = true
                predicate(value)
            }
        }
    } catch (error: TimeoutCancellationException) {
        throw AssertionError(awaitStateTimeoutMessage(timeout, seen, last), error)
    } catch (error: NoSuchElementException) {
        throw AssertionError(awaitStateCompletedMessage(seen, last), error)
    }
}

private fun awaitStateTimeoutMessage(
    timeout: Duration,
    seen: Boolean,
    last: Any?,
): String = "awaitState timed out after $timeout. Last value: ${lastValueText(seen, last)}"

private fun awaitStateCompletedMessage(
    seen: Boolean,
    last: Any?,
): String = "awaitState: flow completed without a match. Last value: ${lastValueText(seen, last)}"

private fun lastValueText(
    seen: Boolean,
    last: Any?,
): String = if (seen) last.toString() else "none"
