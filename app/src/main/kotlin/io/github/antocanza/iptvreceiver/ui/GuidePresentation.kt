package io.github.antocanza.iptvreceiver.ui

import io.github.antocanza.iptvreceiver.model.Programme

/** Provider conflicts stay visible; neither timestamps nor archive clocks are rewritten. */
internal fun hasProgrammeOverlaps(programmes: List<Programme>): Boolean {
    var end = Long.MIN_VALUE
    for (programme in programmes.sortedBy { it.startsAtSeconds }) {
        if (programme.startsAtSeconds < end) return true
        end = maxOf(end, programme.endsAtSeconds)
    }
    return false
}

internal fun unambiguousCurrentProgramme(programmes: List<Programme>, now: Long): Programme? =
    programmes.filter { it.startsAtSeconds <= now && it.endsAtSeconds > now }.singleOrNull()
