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
 * Phrase destinations. Interrupt is present only while a frame is active.
 * Typed capture keeps Interrupt even when nothing is active; that path does not use this list.
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
