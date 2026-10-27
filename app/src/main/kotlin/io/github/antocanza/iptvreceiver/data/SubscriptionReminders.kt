package io.github.antocanza.iptvreceiver.data

import io.github.antocanza.iptvreceiver.model.ProviderConnection
import java.io.DataInputStream
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class SubscriptionNotice(val expiresAtSeconds: Long, val date: LocalDate, val daysRemaining: Long, val expired: Boolean)
private data class ExpiryAcknowledgement(val expiry: Long, val timeMillis: Long)

/** Foreground pop-ups only. Acknowledgement is isolated by account and by the provider's expiry value. */
class SubscriptionReminders(private val directory: File, private val now: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault) {
    private val acknowledged = mutableMapOf<String, ExpiryAcknowledgement>()

    @Synchronized fun due(connection: ProviderConnection, expiry: Long?): SubscriptionNotice? {
        val seconds = expiry?.takeIf { it in 1..7_258_118_400L } ?: return null
        val time = now()
        val today = time.atZone(zone()).toLocalDate()
        val date = Instant.ofEpochSecond(seconds).atZone(zone()).toLocalDate()
        if (date > today.plusMonths(2)) return null
        val days = ChronoUnit.DAYS.between(today, date)
        val interval = intervalMillis(days)
        val key = accountKey(connection)
        val last = acknowledged[key] ?: read(directory.resolve("$key.bin"))?.also { acknowledged[key] = it }
        if (last != null && last.expiry == seconds && time.toEpochMilli() >= last.timeMillis &&
            time.toEpochMilli() - last.timeMillis < interval) return null
        return SubscriptionNotice(seconds, date, days, time.epochSecond >= seconds)
    }

    @Synchronized fun acknowledge(connection: ProviderConnection, notice: SubscriptionNotice) {
        val key = accountKey(connection)
        val value = ExpiryAcknowledgement(notice.expiresAtSeconds, now().toEpochMilli())
        acknowledged[key] = value
        // Closing OK must remain possible even if storage is temporarily unavailable.
        runCatching { writePrivateData(directory.resolve("$key.bin")) { output ->
            output.writeInt(1); output.writeLong(value.expiry); output.writeLong(value.timeMillis)
        } }
    }

    private fun read(file: File): ExpiryAcknowledgement? = runCatching {
        if (!file.exists()) return null
        DataInputStream(file.inputStream().buffered()).use { input ->
            if (input.readInt() != 1) return null
            val result = ExpiryAcknowledgement(input.readLong(), input.readLong())
            result.takeIf { it.expiry > 0 && it.timeMillis > 0 && input.read() == -1 }
        }
    }.getOrNull()

    companion object {
        fun intervalMillis(daysRemaining: Long): Long = when {
            daysRemaining > 30 -> 14 * 86_400_000L
            daysRemaining > 14 -> 7 * 86_400_000L
            daysRemaining > 7 -> 3 * 86_400_000L
            daysRemaining > 3 -> 86_400_000L
            else -> 12 * 3_600_000L
        }
    }
}
