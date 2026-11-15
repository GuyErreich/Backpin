// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.data

/** Marks intro and hint flags so Home tests are not blocked by first-run teaching. */
suspend fun PreferencesDataStore.skipGestureTeaching() {
    setGesturesIntroSeen(true)
    setGestureHintRightSeen(true)
    setGestureHintLeftSeen(true)
}
