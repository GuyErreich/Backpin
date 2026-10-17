// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.domain

/**
 * How Home lists tasks.
 * [FLAT] is the shipped layout. [BANDS] groups by time of day.
 */
enum class HomeLayout {
    /** One list. The default. */
    FLAT,

    /** Tasks grouped into morning, noon, and night bands. */
    BANDS,
}
