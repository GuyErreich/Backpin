// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus

import android.os.Looper
import kotlinx.coroutines.runBlocking
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Runs a suspending call off the Compose test thread.
 *
 * That thread is the test scheduler. [runBlocking] on it can park forever once a
 * screen is collecting the database flow, because the continuation is queued on
 * the same scheduler. A real worker thread finishes the call, and this thread
 * keeps the main looper moving so collectors can catch up.
 */
internal fun <T> runOffTestThread(block: suspend () -> T): T {
    val worker = Executors.newSingleThreadExecutor()
    try {
        val pending = worker.submit<T> { runBlocking { block() } }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
        while (!pending.isDone) {
            shadowOf(Looper.getMainLooper()).idle()
            if (System.nanoTime() > deadline) {
                pending.cancel(true)
                error("Suspended call did not finish")
            }
            if (!pending.isDone) Thread.sleep(10)
        }
        return pending.get()
    } finally {
        worker.shutdownNow()
    }
}
