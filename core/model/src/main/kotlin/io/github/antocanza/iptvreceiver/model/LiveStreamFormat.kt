package io.github.antocanza.iptvreceiver.model

/** Prefer the provider's segmented live feed so the player can maintain a rolling buffer. */
val Account.preferredLiveFormat: String
    get() {
        val formats = liveFormats.map { it.trim().lowercase(java.util.Locale.ROOT) }.filter { it.isNotEmpty() }
        return formats.firstOrNull { it == "m3u8" } ?: formats.firstOrNull { it == "ts" } ?: formats.firstOrNull() ?: "ts"
    }
