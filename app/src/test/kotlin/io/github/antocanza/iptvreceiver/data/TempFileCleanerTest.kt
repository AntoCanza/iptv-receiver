package io.github.antocanza.iptvreceiver.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TempFileCleanerTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun `cleanup removes only expired cache temporary files and preserves recent work and account storage`() {
        val root = folder.newFolder("app")
        val temporary = root.resolve("cache/tmp").apply { mkdirs() }
        val old = temporary.resolve("old.part").apply { writeText("synthetic cache"); setLastModified(1_000L) }
        val recent = temporary.resolve("active.part").apply { writeText("active download"); setLastModified(9_000L) }
        val account = root.resolve("shared_prefs/connection.xml").apply { checkNotNull(parentFile).mkdirs(); writeText("synthetic encrypted storage"); setLastModified(1_000L) }
        val database = root.resolve("databases/catalog.db").apply { checkNotNull(parentFile).mkdirs(); writeText("synthetic database"); setLastModified(1_000L) }
        assertEquals(1, pruneExpiredTempFiles(temporary, 5_000L))
        assertFalse(old.exists())
        assertTrue(recent.exists())
        assertTrue(account.exists())
        assertTrue(database.exists())
    }

    @Test fun `missing temp directories are harmless and nested expired files are cleaned`() {
        val root = folder.newFolder("cache")
        assertEquals(0, pruneExpiredTempFiles(root.resolve("missing"), Long.MAX_VALUE))
        val nested = root.resolve("tmp/nested/old.part").apply { checkNotNull(parentFile).mkdirs(); writeText("synthetic"); setLastModified(1_000L) }
        assertEquals(1, pruneExpiredTempFiles(root.resolve("tmp"), 5_000L))
        assertFalse(nested.exists())
    }
}
