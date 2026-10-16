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
    fun editPlacesAreWhereATaskLives() {
        assertEquals(
            listOf(
                CapturePlace.INBOX,
                CapturePlace.TODAY,
                CapturePlace.LATER,
                CapturePlace.SCHEDULE,
            ),
            editPlaces(),
        )
        assertEquals(CapturePlace.INBOX, TaskBucket.INBOX.editPlace())
        assertEquals(CapturePlace.TODAY, TaskBucket.TODAY.editPlace())
        assertEquals(CapturePlace.LATER, TaskBucket.LATER.editPlace())
        assertEquals(CapturePlace.SCHEDULE, TaskBucket.SCHEDULED.editPlace())
        assertEquals(CapturePlace.SCHEDULE, TaskBucket.COUNTDOWN.editPlace())
        assertEquals(null, TaskBucket.INTERRUPTION.editPlace())
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
