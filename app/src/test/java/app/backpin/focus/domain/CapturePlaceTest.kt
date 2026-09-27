package app.backpin.focus.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CapturePlaceTest {
    @Test
    fun phraseDestinationsAreOneStepAndSkipInterruptWhenIdle() {
        assertEquals(
            listOf(
                CapturePlace.INBOX,
                CapturePlace.TODAY,
                CapturePlace.START,
                CapturePlace.LATER,
                CapturePlace.SCHEDULE,
            ),
            capturePlaces(frameActive = false),
        )
        assertFalse(capturePlaces(frameActive = false).contains(CapturePlace.INTERRUPT))
    }

    @Test
    fun interruptIsOfferedOnlyWhileAFrameIsActive() {
        assertEquals(
            listOf(
                CapturePlace.INBOX,
                CapturePlace.TODAY,
                CapturePlace.START,
                CapturePlace.INTERRUPT,
                CapturePlace.LATER,
                CapturePlace.SCHEDULE,
            ),
            capturePlaces(frameActive = true),
        )
    }
}
