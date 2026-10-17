// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.domain

/**
 * Where a new capture can land, chosen in one step.
 * [INTERRUPT] pauses the active frame and starts this task nested under it.
 */
enum class CapturePlace {
    INBOX,
    TODAY,
    START,
    INTERRUPT,
    LATER,
    SCHEDULE,
}

/**
 * Destinations for a typed line or a phrase, chosen in one step.
 * [INTERRUPT] is included only while a frame is active.
 */
fun capturePlaces(frameActive: Boolean): List<CapturePlace> =
    buildList {
        add(CapturePlace.INBOX)
        add(CapturePlace.TODAY)
        add(CapturePlace.START)
        if (frameActive) add(CapturePlace.INTERRUPT)
        add(CapturePlace.LATER)
        add(CapturePlace.SCHEDULE)
    }

/**
 * Where an existing task can live after edit. Start and Interrupt stay on the card;
 * they are actions, not fields.
 */
fun editPlaces(): List<CapturePlace> =
    listOf(
        CapturePlace.INBOX,
        CapturePlace.TODAY,
        CapturePlace.LATER,
        CapturePlace.SCHEDULE,
    )

/** The capture place that matches this bucket, or null when the task is a nested interruption. */
fun TaskBucket.editPlace(): CapturePlace? =
    when (this) {
        TaskBucket.INBOX -> CapturePlace.INBOX
        TaskBucket.TODAY -> CapturePlace.TODAY
        TaskBucket.LATER -> CapturePlace.LATER
        TaskBucket.SCHEDULED,
        TaskBucket.COUNTDOWN,
        -> CapturePlace.SCHEDULE
        TaskBucket.INTERRUPTION -> null
    }
