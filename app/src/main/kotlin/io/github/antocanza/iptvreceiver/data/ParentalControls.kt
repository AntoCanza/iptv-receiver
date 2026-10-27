package io.github.antocanza.iptvreceiver.data

import io.github.antocanza.iptvreceiver.model.CatalogItem
import io.github.antocanza.iptvreceiver.model.Category
import io.github.antocanza.iptvreceiver.model.ContentType
import io.github.antocanza.iptvreceiver.model.ProviderConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class ParentalPolicy(
    val passwordSet: Boolean = false,
    val blockAdult: Boolean = true,
    val blockedCategories: Set<String> = emptySet(),
    val storageFailed: Boolean = false,
    val revision: Long = 0,
) {
    fun blockedIds(connection: ProviderConnection, type: ContentType, categories: List<Category>): Set<String> {
        val prefix = "${accountKey(connection)}:${type.name}:"
        val blocked = blockedCategories.filter { it.startsWith(prefix) }.mapTo(mutableSetOf()) { it.removePrefix(prefix) }
        blocked += categories.filter { category ->
            "$prefix${category.id}" in blockedCategories ||
                (blockAdult && (category.isAdult || adultLabel(category.name)))
        }.map(Category::id)
        // Iterate to a fixed point; malformed cyclic provider trees cannot loop forever.
        do {
            val added = categories.filter { it.parentId in blocked && it.id !in blocked }.map(Category::id)
            blocked.addAll(added)
        } while (added.isNotEmpty())
        return blocked
    }

    fun blocks(item: CatalogItem, blockedIds: Set<String>): Boolean = storageFailed ||
        item.categoryId in blockedIds || (item.categoryId == null && blockedIds.isNotEmpty()) ||
        (blockAdult && (item.isAdult || adultLabel(item.title)))
}

// Explicit labels only. Providers can omit or mislabel adult content; manual category locks cover that gap.
private val adultLabels = Regex(
    "(?iu)(?<![\\p{L}\\p{N}])(xxx|adult(?:s|os?|i|es)?|porn(?:o|ography)?|erotic(?:a|o|i)?|erotico|hentai)(?![\\p{L}\\p{N}])|18\\s*\\+|\\+\\s*18|vietat[oi]\\s+ai\\s+minori",
)
internal fun adultLabel(value: String): Boolean = adultLabels.containsMatchIn(value)

internal fun accountKey(connection: ProviderConnection): String {
    val uri = URI(connection.serverUrl.trim()).normalize()
    val scheme = uri.scheme.lowercase(Locale.ROOT)
    val port = uri.port.takeUnless { it == -1 || (scheme == "https" && it == 443) || (scheme == "http" && it == 80) }
    val identity = "$scheme://${uri.host.lowercase(Locale.ROOT)}${port?.let { ":$it" }.orEmpty()}${uri.path.orEmpty().trimEnd('/')}\u0000${connection.username}"
    return MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
}

internal fun categoryKey(connection: ProviderConnection, type: ContentType, id: String) = "${accountKey(connection)}:${type.name}:$id"

/** App-private durable settings. The password itself is never serialized or placed in saved state. */
class ParentalControls(private val file: File, private val now: () -> Long = System::nanoTime) {
    private var salt = byteArrayOf()
    private var verifier = byteArrayOf()
    private var generation = 0L
    private var failures = 0
    private var retryAt = 0L
    private val mutablePolicy = MutableStateFlow(read())
    val policy = mutablePolicy.asStateFlow()

    @Synchronized fun create(password: String): Long {
        check(!policy.value.passwordSet && !policy.value.storageFailed)
        require(password.length in 6..128 && password.isNotBlank())
        val nextSalt = ByteArray(16).also(SecureRandom()::nextBytes)
        val nextVerifier = hash(password, nextSalt)
        persist(policy.value.copy(passwordSet = true), nextSalt, nextVerifier)
        return ++generation
    }

    @Synchronized fun unlock(password: String): Long? {
        check(!policy.value.storageFailed)
        if (now() < retryAt) throw PasswordThrottledException()
        if (!policy.value.passwordSet || !MessageDigest.isEqual(verifier, hash(password, salt))) {
            if (++failures >= 5) { retryAt = now() + 30_000_000_000L; failures = 0 }
            return null
        }
        failures = 0
        retryAt = 0L
        return ++generation
    }

    @Synchronized fun update(token: Long, blockAdult: Boolean = policy.value.blockAdult,
        category: String? = null, blocked: Boolean = false) {
        check(token == generation && policy.value.passwordSet && !policy.value.storageFailed)
        val selected = policy.value.blockedCategories.toMutableSet()
        if (category != null) { if (blocked) selected.add(category) else selected.remove(category) }
        persist(policy.value.copy(blockAdult = blockAdult, blockedCategories = selected))
    }

    @Synchronized fun changePassword(token: Long, password: String) {
        check(token == generation && policy.value.passwordSet && !policy.value.storageFailed)
        require(password.length in 6..128 && password.isNotBlank())
        val nextSalt = ByteArray(16).also(SecureRandom()::nextBytes)
        persist(policy.value, nextSalt, hash(password, nextSalt))
    }

    @Synchronized fun lock() { ++generation }

    /** A forgotten password can only be reset together with every protected setting. */
    @Synchronized fun reset() {
        persist(ParentalPolicy(), byteArrayOf(), byteArrayOf())
        ++generation
        failures = 0
        retryAt = 0L
    }

    private fun read(): ParentalPolicy {
        if (!file.exists()) return ParentalPolicy()
        return try {
            DataInputStream(file.inputStream().buffered()).use { input ->
                if (input.readInt() != 1) throw IOException("Unsupported parental settings")
                val configured = input.readBoolean()
                val adult = input.readBoolean()
                salt = ByteArray(if (configured) 16 else 0).also(input::readFully)
                verifier = ByteArray(if (configured) 32 else 0).also(input::readFully)
                val count = input.readInt()
                if (count !in 0..100_000 || (!configured && (!adult || count != 0))) throw IOException("Invalid parental settings")
                val categories = List(count) { input.readUTF() }.toSet()
                if (input.read() != -1) throw IOException("Invalid parental settings")
                ParentalPolicy(configured, adult, categories)
            }
        } catch (_: Exception) { ParentalPolicy(storageFailed = true) }
    }

    private fun persist(next: ParentalPolicy, nextSalt: ByteArray = salt, nextVerifier: ByteArray = verifier) {
        val directory = checkNotNull(file.parentFile)
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot save parental settings")
        val temporary = File.createTempFile("parental-", ".tmp", directory)
        try {
            FileOutputStream(temporary).use { stream ->
                val output = DataOutputStream(stream.buffered())
                output.writeInt(1)
                output.writeBoolean(next.passwordSet)
                output.writeBoolean(next.blockAdult)
                output.write(nextSalt)
                output.write(nextVerifier)
                output.writeInt(next.blockedCategories.size)
                next.blockedCategories.sorted().forEach(output::writeUTF)
                output.flush()
                stream.fd.sync()
            }
            Files.move(temporary.toPath(), file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
            salt = nextSalt
            verifier = nextVerifier
            mutablePolicy.value = next.copy(revision = policy.value.revision + 1)
        } finally { temporary.delete() }
    }

    private fun hash(password: String, salt: ByteArray): ByteArray {
        val chars = password.toCharArray()
        val spec = PBEKeySpec(chars, salt, 600_000, 256)
        chars.fill('\u0000')
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
}

class PasswordThrottledException : IllegalStateException()
