// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.domain

/**
 * Named color palette stored on this phone.
 * Palettes live in Theme.kt; this id is the persisted choice.
 */
enum class ThemeId {
    /** Default palette. */
    SIGNAL,

    /** Cool, still palette. */
    STILL_WATER,

    /** Warm palette. */
    EMBER,

    /** High-contrast palette. */
    CLEAR_LINE,
}
