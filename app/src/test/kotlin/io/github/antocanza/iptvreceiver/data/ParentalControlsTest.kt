package io.github.antocanza.iptvreceiver.data

import io.github.antocanza.iptvreceiver.model.CatalogItem
import io.github.antocanza.iptvreceiver.model.Category
import io.github.antocanza.iptvreceiver.model.ContentType
import io.github.antocanza.iptvreceiver.model.ProviderConnection
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ParentalControlsTest {
    @get:Rule val folder = TemporaryFolder()
    private val file get() = folder.root.resolve("parental/settings.bin")
    private val connection = ProviderConnection("Demo", "https://provider.example.test", "demo", "synthetic")

    @Test fun `adult blocking is on before password creation and default policy recognizes flags and labels`() {
        val policy = ParentalControls(file).policy.value
        assertFalse(policy.passwordSet)
        assertTrue(policy.blockAdult)
        val categories = listOf(Category("adult", "18+", null), Category("child", "Nested", "adult"),
            Category("flag", "Provider category", null, true), Category("normal", "News", null))
        assertEquals(setOf("adult", "child", "flag"), policy.blockedIds(connection, ContentType.LIVE, categories))
        assertTrue(policy.blocks(CatalogItem("1", "Regular title", ContentType.LIVE, isAdult = true), emptySet()))
        listOf("XXX HD", "IT | Adulti", "Adultos", "Film 18+", "Vietati ai minori").forEach { assertTrue(it, adultLabel(it)) }
        listOf("News", "Adventure", "Sussex", "Scunthorpe", "18 channels").forEach { assertFalse(it, adultLabel(it)) }
    }

    @Test fun `password verification settings and account scoped blocks survive restart without plaintext`() {
        val controls = ParentalControls(file)
        val access = controls.create("synthetic-parent-password")
        controls.update(access, blockAdult = false, category = categoryKey(connection, ContentType.LIVE, "7"), blocked = true)
        val restarted = ParentalControls(file)
        assertNull(restarted.unlock("incorrect"))
        assertNotNull(restarted.unlock("synthetic-parent-password"))
        assertFalse(restarted.policy.value.blockAdult)
        val categories = listOf(Category("7", "News", null), Category("8", "Child", "7"))
        assertEquals(setOf("7", "8"), restarted.policy.value.blockedIds(connection, ContentType.LIVE, categories))
        assertTrue(restarted.policy.value.blockedIds(connection, ContentType.MOVIE, categories).isEmpty())
        val other = ProviderConnection("Other", connection.serverUrl, "other", "synthetic")
        assertTrue(restarted.policy.value.blockedIds(other, ContentType.LIVE, categories).isEmpty())
        val renamed = ProviderConnection("Renamed", "https://PROVIDER.example.test:443/", "demo", "changed")
        assertEquals(setOf("7", "8"), restarted.policy.value.blockedIds(renamed, ContentType.LIVE, categories))
        val bytes = file.readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(bytes.contains("synthetic-parent-password"))
        assertFalse(bytes.contains("provider.example.test"))
        assertFalse(bytes.contains("demo"))
    }

    @Test fun `reset discards password and every protected setting and invalidates existing access`() {
        val controls = ParentalControls(file)
        val access = controls.create("first-password")
        controls.update(access, false, categoryKey(connection, ContentType.LIVE, "7"), true)
        controls.reset()
        assertFalse(controls.policy.value.passwordSet)
        assertTrue(controls.policy.value.blockAdult)
        assertTrue(controls.policy.value.blockedCategories.isEmpty())
        assertTrue(runCatching { controls.update(access, blockAdult = false) }.isFailure)
        val restarted = ParentalControls(file)
        assertFalse(restarted.policy.value.passwordSet)
        assertTrue(restarted.policy.value.blockAdult)
        restarted.create("second-password")
        assertNull(restarted.unlock("first-password"))
        assertNotNull(restarted.unlock("second-password"))
    }

    @Test fun `password changes preserve settings but lock invalidates access`() {
        val controls = ParentalControls(file)
        val access = controls.create("old-password")
        controls.update(access, blockAdult = false)
        controls.changePassword(access, "new-password")
        assertFalse(controls.policy.value.blockAdult)
        controls.lock()
        assertTrue(runCatching { controls.update(access) }.isFailure)
        assertNull(controls.unlock("old-password"))
        assertNotNull(controls.unlock("new-password"))
    }

    @Test fun `repeated wrong passwords are throttled and correct password works after timeout`() {
        var now = 0L
        val controls = ParentalControls(file) { now }
        controls.create("parent-password")
        repeat(5) { assertNull(controls.unlock("wrong-password")) }
        assertTrue(runCatching { controls.unlock("parent-password") }.exceptionOrNull() is PasswordThrottledException)
        now = 30_000_000_000L
        assertNotNull(controls.unlock("parent-password"))
    }

    @Test fun `corrupt settings fail closed and explicit reset recovers safe defaults`() {
        file.parentFile!!.mkdirs()
        file.writeText("corrupt")
        val controls = ParentalControls(file)
        assertTrue(controls.policy.value.storageFailed)
        assertTrue(controls.policy.value.blocks(CatalogItem("1", "News", ContentType.LIVE), emptySet()))
        assertTrue(runCatching { controls.create("password") }.isFailure)
        assertEquals("corrupt", file.readText())
        controls.reset()
        assertFalse(ParentalControls(file).policy.value.storageFailed)
        assertTrue(controls.policy.value.blockAdult)
    }

    @Test fun `cycles and missing provider categories retain manual blocks and unclassified items fail closed`() {
        val key = categoryKey(connection, ContentType.LIVE, "missing")
        val policy = ParentalPolicy(blockedCategories = setOf(key))
        val categories = listOf(Category("missing", "A", "child"), Category("child", "B", "missing"))
        assertEquals(setOf("missing", "child"), policy.blockedIds(connection, ContentType.LIVE, categories))
        assertEquals(setOf("missing"), policy.blockedIds(connection, ContentType.LIVE, emptyList()))
        assertTrue(policy.blocks(CatalogItem("1", "Unknown", ContentType.LIVE), setOf("missing")))
    }
}
