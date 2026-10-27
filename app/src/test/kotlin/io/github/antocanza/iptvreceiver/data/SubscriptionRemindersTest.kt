package io.github.antocanza.iptvreceiver.data

import io.github.antocanza.iptvreceiver.model.ProviderConnection
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class SubscriptionRemindersTest {
    @get:Rule val folder = TemporaryFolder()
    private val directory get() = folder.root.resolve("reminders")
    private val connection = ProviderConnection("Account", "https://provider.example.test", "demo", "synthetic-secret")
    private val zone = ZoneId.of("Europe/Rome")
    private var now = Instant.parse("2026-10-27T11:00:00Z")
    private fun expiry(days: Long) = now.atZone(zone).toLocalDate().plusDays(days).atStartOfDay(zone).toEpochSecond()
    private fun store() = SubscriptionReminders(directory, { now }, { zone })

    @Test fun `reminders begin exactly two calendar months before expiry including month ends`() {
        val expiry = LocalDate.of(2026, 12, 27).atStartOfDay(zone).toEpochSecond()
        now = Instant.parse("2026-10-26T11:00:00Z")
        assertNull(store().due(connection, expiry))
        now = Instant.parse("2026-10-27T11:00:00Z")
        assertNotNull(store().due(connection, expiry))
        now = Instant.parse("2026-12-31T11:00:00Z")
        assertNotNull(store().due(connection, LocalDate.of(2027, 2, 28).atStartOfDay(zone).toEpochSecond()))
        assertNull(store().due(connection, LocalDate.of(2027, 3, 1).atStartOfDay(zone).toEpochSecond()))
    }

    @Test fun `all urgency bands repeat at their exact interval and persist OK across process restart`() {
        for ((days, hours) in listOf(45L to 336L, 30L to 168L, 14L to 72L, 7L to 24L, 3L to 12L, 0L to 12L, -2L to 12L)) {
            // Reset time so advancing does not cross this band's boundary before the next check.
            now = Instant.parse("2026-10-27T11:00:00Z")
            val conn = connection.with(username = "band-$days")
            val expiry = expiry(days)
            val store = store()
            val notice = checkNotNull(store.due(conn, expiry))
            store.acknowledge(conn, notice)
            assertNull(store().due(conn, expiry))
            val interval = SubscriptionReminders.intervalMillis(days)
            assertEquals(hours * 3_600_000, interval)
            // Test exact persisted scheduling within each band using elapsed time and a matching expiry shift.
            now = now.plusMillis(interval - 1)
            val currentInterval = SubscriptionReminders.intervalMillis(java.time.temporal.ChronoUnit.DAYS.between(now.atZone(zone).toLocalDate(), Instant.ofEpochSecond(expiry).atZone(zone).toLocalDate()))
            if (interval == currentInterval) assertNull(store().due(conn, expiry))
            now = now.plusMillis(1)
            assertNotNull(store().due(conn, expiry))
        }
    }

    @Test fun `increasing urgency can bring the next popup forward`() {
        val store = store()
        val expiry = expiry(31)
        store.acknowledge(connection, checkNotNull(store.due(connection, expiry)))
        now = now.plusSeconds(7 * 86_400)
        assertNotNull(store().due(connection, expiry)) // Now seven-day cadence instead of fourteen.
    }

    @Test fun `expiry renewal account isolation and connection rename are handled independently`() {
        val store = store()
        val expiry = expiry(20)
        store.acknowledge(connection, checkNotNull(store.due(connection, expiry)))
        assertNull(store().due(connection.with(name = "Renamed", password = "Changed"), expiry))
        assertNotNull(store().due(connection.with(username = "another"), expiry))
        assertNotNull(store().due(connection.with(serverUrl = "https://another.example.test"), expiry))
        assertNotNull(store().due(connection, expiry(21)))
        assertNull(store().due(connection, expiry(100)))
    }

    @Test fun `unknown dates are ignored expired dates and clock rollback remain usable`() {
        val store = store()
        for (expiry in listOf(null, 0L, -1L, Long.MAX_VALUE)) assertNull(store.due(connection, expiry))
        assertFalse(checkNotNull(store.due(connection, expiry(1))).expired)
        val notice = checkNotNull(store.due(connection, expiry(-1)))
        assertTrue(notice.expired)
        store.acknowledge(connection, notice)
        now = now.minusSeconds(3600)
        assertNotNull(store.due(connection, notice.expiresAtSeconds))
    }

    @Test fun `OK still closes when disk is unavailable and corrupt acknowledgements do not suppress reminders`() {
        directory.writeText("not a directory")
        val store = store()
        val expiry = expiry(10)
        store.acknowledge(connection, checkNotNull(store.due(connection, expiry)))
        assertNull(store.due(connection, expiry))
        assertNotNull(store().due(connection, expiry))
        directory.delete(); directory.mkdirs()
        store().acknowledge(connection, checkNotNull(store().due(connection, expiry)))
        directory.listFiles()!!.single().writeBytes(byteArrayOf(1))
        assertNotNull(store().due(connection, expiry))
    }

    @Test fun `calendar day count follows local timezone across daylight saving`() {
        now = Instant.parse("2026-10-24T22:30:00Z") // 25 October locally; DST changes that night.
        val expiry = LocalDate.of(2026, 10, 27).atStartOfDay(zone).toEpochSecond()
        assertEquals(2L, store().due(connection, expiry)!!.daysRemaining)
    }
}

private fun ProviderConnection.with(name: String = this.name, serverUrl: String = this.serverUrl, username: String = this.username, password: String = this.password) = ProviderConnection(name, serverUrl, username, password)
