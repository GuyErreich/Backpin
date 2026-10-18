// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.theme

import app.backpin.focus.domain.ThemeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ThemePaletteTest {
    @Test
    fun colorsForReturnsADistinctPaletteForEachThemeAndSignalIsDefault() {
        val signal = colorsFor(ThemeId.SIGNAL)
        val stillWater = colorsFor(ThemeId.STILL_WATER)
        val ember = colorsFor(ThemeId.EMBER)
        val clearLine = colorsFor(ThemeId.CLEAR_LINE)
        val schemes = listOf(signal, stillWater, ember, clearLine)
        val backgrounds = schemes.map { it.background }.toSet()
        val surfaces = schemes.map { it.surface }.toSet()
        val onBackgrounds = schemes.map { it.onBackground }.toSet()
        val primaries = schemes.map { it.primary }.toSet()
        assertEquals("each theme must have its own background", 4, backgrounds.size)
        assertEquals("each theme must have its own surface", 4, surfaces.size)
        assertEquals("each theme must have its own onBackground", 4, onBackgrounds.size)
        assertEquals("each theme must have its own primary", 4, primaries.size)
        assertEquals(paletteFor(ThemeId.SIGNAL).toColorScheme().background, signal.background)
        assertEquals(paletteFor(ThemeId.SIGNAL).textPrimary, signal.onBackground)
        assertEquals(paletteFor(ThemeId.EMBER).background, ember.background)
        assertEquals(paletteFor(ThemeId.CLEAR_LINE).background, clearLine.background)
        assertEquals(paletteFor(ThemeId.STILL_WATER).background, stillWater.background)
        assertEquals(ThemeId.SIGNAL, ThemeId.entries.first())
        assertNotEquals(signal.background, ember.background)
        ThemeId.entries.forEach { id ->
            val palette = paletteFor(id)
            val scheme = colorsFor(id)
            assertEquals(id.name, palette.textPrimary, scheme.onBackground)
            assertEquals(id.name, palette.textPrimary, scheme.onSurface)
            assertEquals(id.name, palette.textSecondary, scheme.onSurfaceVariant)
            assertEquals(id.name, palette.onAccent, scheme.onPrimary)
            assertEquals(id.name, palette.onAccent, scheme.onSecondary)
        }
    }
}
