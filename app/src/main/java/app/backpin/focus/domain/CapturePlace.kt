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
