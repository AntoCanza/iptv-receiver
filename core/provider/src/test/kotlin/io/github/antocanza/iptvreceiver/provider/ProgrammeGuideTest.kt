package io.github.antocanza.iptvreceiver.provider

import io.github.antocanza.iptvreceiver.model.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64
import okhttp3.HttpUrl.Companion.toHttpUrl

class ProgrammeGuideTest {
    private val connection = ProviderConnection("Fixture", "https://provider.example.test/base", "user/name", "synthetic ?password")
    private val channel = CatalogItem("42", "Fixture channel", ContentType.LIVE, catchupDays = 7)
    private fun encoded(text: String) = Base64.getEncoder().encodeToString(text.toByteArray())
    private fun past() = Programme("p1", "Fixture programme", "", System.currentTimeMillis() / 1000 - 3600,
        System.currentTimeMillis() / 1000 - 1800, true, "2026-10-26 13:05:00")

    @Test fun `guide decodes UTF8 text sorts deduplicates and omits invalid timestamps`() = runTest {
        val repository = XtreamCatalogRepository(JsonTransport { url ->
            assertEquals("get_simple_data_table", url.queryParameter("action"))
            assertEquals("42", url.queryParameter("stream_id"))
            Json.parseToJsonElement("""{"epg_listings":[
                {"id":"2","title":"${encoded("Notizie città")}","description":"${encoded("Descrizione")}","start_timestamp":"2000","stop_timestamp":3000,"start":"2026-10-26 13:05:00","has_archive":"1"},
                {"id":"1","title":"News","start_timestamp":1000,"stop_timestamp":2000},
                {"id":"duplicate","title":"Other","start_timestamp":1000,"stop_timestamp":2000},
                {"title":"Broken","start_timestamp":3000,"stop_timestamp":2000},
                {"title":"Broken","start_timestamp":"not-a-time","stop_timestamp":3000}
            ]}""")
        })
        val guide = repository.guide(connection, channel)
        assertEquals(listOf("News", "Notizie città"), guide.map { it.title })
        assertEquals("Descrizione", guide.last().description)
        assertTrue(guide.last().archiveAvailable)
        assertFalse(guide.first().archiveAvailable)
    }

    @Test fun `missing guide and invalid local clocks do not invent archive availability`() = runTest {
        val empty = XtreamCatalogRepository(JsonTransport { Json.parseToJsonElement("{}") })
        assertTrue(empty.guide(connection, channel).isEmpty())
        assertNull(archiveStart("2026-02-30 13:05:00"))
        assertNull(archiveStart("../../private"))
        assertFalse(past().copy(providerStartLocal = null).canReplay(channel, System.currentTimeMillis() / 1000))
    }

    @Test fun `archive mapping requires both support flag and bounded retention`() = runTest {
        val repository = XtreamCatalogRepository(JsonTransport { Json.parseToJsonElement("""[
            {"stream_id":1,"name":"A","tv_archive":1,"tv_archive_duration":7},
            {"stream_id":2,"name":"B","tv_archive":0,"tv_archive_duration":7},
            {"stream_id":3,"name":"C","tv_archive":1,"tv_archive_duration":-1},
            {"stream_id":4,"name":"D","tv_archive":1,"tv_archive_duration":999999}
        ]""") })
        assertEquals(listOf(7, 0, 0, 0), repository.items(connection, ContentType.LIVE, null).map { it.catchupDays })
        assertTrue(repository.items(connection, ContentType.MOVIE, null).all { it.catchupDays == 0 })
    }

    @Test fun `archive path encodes credentials uses provider clock and clips to programme length`() {
        val repository = XtreamCatalogRepository()
        val programme = past().let { it.copy(endsAtSeconds = it.startsAtSeconds + 1801) }
        val source = repository.catchupStream(connection, channel, programme)
        val url = source.url.toHttpUrl()
        assertEquals(listOf("base", "timeshift", "user/name", "synthetic ?password", "31", "2026-10-26:13-05", "42.ts"), url.pathSegments)
        assertFalse(source.isLive)
        assertEquals(1_801_000L, source.durationMs)
        assertFalse(source.toString().contains("synthetic"))
    }

    @Test fun `future expired unsupported and corrupt archive requests fail closed`() {
        val repository = XtreamCatalogRepository()
        val now = System.currentTimeMillis() / 1000
        for (programme in listOf(past().copy(archiveAvailable = false), past().copy(startsAtSeconds = now + 100, endsAtSeconds = now + 200),
            past().copy(startsAtSeconds = now - 8 * 86400, endsAtSeconds = now - 8 * 86400 + 100),
            past().copy(providerStartLocal = "2026-02-30 13:00:00"))) {
            assertTrue(runCatching { repository.catchupStream(connection, channel, programme) }.isFailure)
        }
        assertFalse(past().canReplay(channel.copy(catchupDays = 0), now))
        assertFalse(past().canReplay(channel.copy(type = ContentType.MOVIE), now))
    }
}
