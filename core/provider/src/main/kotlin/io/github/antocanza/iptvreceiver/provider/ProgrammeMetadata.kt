package io.github.antocanza.iptvreceiver.provider

import io.github.antocanza.iptvreceiver.model.Programme
import kotlinx.serialization.json.JsonObject
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Base64

private val localClock = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT)
private val archiveClock = DateTimeFormatter.ofPattern("uuuu-MM-dd:HH-mm")

internal fun archiveStart(value: String?): String? = value?.let {
    runCatching { LocalDateTime.parse(it, localClock).format(archiveClock) }.getOrNull()
}

internal fun programme(values: JsonObject?): Programme? {
    if (values == null) return null
    val start = values.metadataText("start_timestamp")?.toLongOrNull()?.takeIf { it in 1..7_258_118_400 } ?: return null
    val stop = values.metadataText("stop_timestamp", "end_timestamp")?.toLongOrNull()?.takeIf { it > start && it - start <= 86_400 } ?: return null
    val title = guideText(values.metadataText("title"))?.takeIf(String::isNotBlank) ?: return null
    val local = values.metadataText("start")?.takeIf { archiveStart(it) != null }
    return Programme(values.metadataText("id", "epg_id") ?: "$start", title,
        guideText(values.metadataText("description")).orEmpty(), start, stop,
        values.metadataText("has_archive") in setOf("1", "true"), local)
}

private fun guideText(value: String?): String? {
    if (value == null) return null
    val decoded = runCatching {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(Base64.getDecoder().decode(value))).toString()
            .takeUnless { text -> text.any { it.isISOControl() && it !in "\n\r\t" } }
    }.getOrNull()
    return (decoded ?: value).trim()
}
