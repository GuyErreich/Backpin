// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GesturePreferencesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun gestureFlagsDefaultOffAndPersistOnTheStore() {
        runBlocking {
            val store = PreferencesDataStore(context, "gesture-prefs-${UUID.randomUUID()}")
            val fresh = store.preferences.first()
            assertFalse(fresh.gesturesIntroSeen)
            assertFalse(fresh.gestureHintRightSeen)
            assertFalse(fresh.gestureHintLeftSeen)

            store.setGesturesIntroSeen(true)
            store.setGestureHintRightSeen(true)
            store.setGestureHintLeftSeen(true)
            val seen = store.preferences.first()
            assertTrue(seen.gesturesIntroSeen)
            assertTrue(seen.gestureHintRightSeen)
            assertTrue(seen.gestureHintLeftSeen)

            store.setGesturesIntroSeen(false)
            store.setGestureHintRightSeen(false)
            store.setGestureHintLeftSeen(false)
            val cleared = store.preferences.first()
            assertFalse(cleared.gesturesIntroSeen)
            assertFalse(cleared.gestureHintRightSeen)
            assertFalse(cleared.gestureHintLeftSeen)
        }
    }
}
