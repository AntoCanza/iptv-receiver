package io.github.antocanza.iptvreceiver.ui

import io.github.antocanza.iptvreceiver.model.Programme
import org.junit.Assert.*
import org.junit.Test

class GuidePresentationTest {
    private fun programme(id: String, start: Long, end: Long) = Programme(id, "Repeated title", "", start, end, true, "2026-10-27 10:00:00")

    @Test fun `overlapping schedules remain intact and do not claim two programmes are current`() {
        val first = programme("first", 100, 200)
        val second = programme("second", 150, 250)
        val programmes = listOf(second, first)
        assertTrue(hasProgrammeOverlaps(programmes))
        assertNull(unambiguousCurrentProgramme(programmes, 175))
        assertEquals(first, unambiguousCurrentProgramme(programmes, 125))
        assertEquals(second, unambiguousCurrentProgramme(programmes, 200))
        assertEquals(listOf(second, first), programmes)
        assertEquals("2026-10-27 10:00:00", first.providerStartLocal)
    }

    @Test fun `adjacent repeated titles are valid and boundaries belong to the next programme`() {
        val first = programme("first", 100, 200)
        val second = programme("second", 200, 300)
        val programmes = listOf(second, first)
        assertFalse(hasProgrammeOverlaps(programmes))
        assertNull(unambiguousCurrentProgramme(programmes, 99))
        assertEquals(second, unambiguousCurrentProgramme(programmes, 200))
        assertNull(unambiguousCurrentProgramme(programmes, 300))
        assertFalse(hasProgrammeOverlaps(emptyList()))
    }
}
