package io.github.antocanza.iptvreceiver.ui

import io.github.antocanza.iptvreceiver.data.SavedConnectionStore
import io.github.antocanza.iptvreceiver.data.FileWatchHistoryStore
import io.github.antocanza.iptvreceiver.data.WatchTarget
import io.github.antocanza.iptvreceiver.model.CatalogItem
import io.github.antocanza.iptvreceiver.model.ContentType
import io.github.antocanza.iptvreceiver.model.FailureReason
import io.github.antocanza.iptvreceiver.model.ProviderConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiverViewModelTest {
    @get:Rule val folder = TemporaryFolder()
    private val historyStore get() = FileWatchHistoryStore(folder.root.resolve("history"))
    private val dispatcher = StandardTestDispatcher()
    private val connection = ProviderConnection("Test", "https://provider.example.test", "demo", "synthetic-secret")
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `remembered login stores connection and sign out removes it`() = runTest(dispatcher) {
        val store = FakeStore()
        val model = ReceiverViewModel(FakeRepository(), store, historyStore, dispatcher)
        advanceUntilIdle()
        model.connect(connection, remember = true)
        advanceUntilIdle()
        assertEquals(connection, store.saved)
        assertNotNull(model.state.value.session)
        model.signOut()
        advanceUntilIdle()
        assertNull(store.saved)
        assertNull(model.state.value.session)
        assertFalse(model.state.value.busy)
    }

    @Test fun `login without remembering does not persist account`() = runTest(dispatcher) {
        val store = FakeStore()
        val model = ReceiverViewModel(FakeRepository(), store, historyStore, dispatcher)
        advanceUntilIdle()
        model.connect(connection, remember = false)
        advanceUntilIdle()
        assertNull(store.saved)
        assertNotNull(model.state.value.session)
    }

    @Test fun `secure storage failure exposes a translatable code and does not create a session`() = runTest(dispatcher) {
        val store = FakeStore().apply { failSave = true }
        val model = ReceiverViewModel(FakeRepository(), store, historyStore, dispatcher)
        advanceUntilIdle()
        model.connect(connection, remember = true)
        advanceUntilIdle()
        assertNull(model.state.value.session)
        assertEquals(FailureReason.SAVE_FAILED, model.state.value.error)
    }

    @Test fun `sign out hides history without deleting it and reconnecting restores only that account`() = runTest(dispatcher) {
        val model = ReceiverViewModel(FakeRepository(), FakeStore(), historyStore, dispatcher)
        advanceUntilIdle()
        model.connect(connection, remember = false)
        advanceUntilIdle()
        val session = checkNotNull(model.state.value.session)
        val target = WatchTarget(CatalogItem("movie", "Synthetic movie", ContentType.MOVIE, extension = "mp4"))
        model.recordWatch(target, session)
        advanceUntilIdle()
        assertEquals(listOf(target), model.history.value.entries.map { it.target })
        model.signOut()
        advanceUntilIdle()
        assertTrue(model.history.value.entries.isEmpty())
        model.connect(ProviderConnection("Other", connection.serverUrl, "other", "synthetic-secret"), remember = false)
        advanceUntilIdle()
        model.recordWatch(target, session) // A delayed callback from the old session is ignored.
        advanceUntilIdle()
        assertTrue(model.history.value.entries.isEmpty())
        model.connect(connection, remember = false)
        advanceUntilIdle()
        assertEquals(listOf(target), model.history.value.entries.map { it.target })
    }

    @Test fun `history storage failure does not prevent login and a retry recovers`() = runTest(dispatcher) {
        val store = historyStore
        store.record(connection, WatchTarget(CatalogItem("movie", "Synthetic movie", ContentType.MOVIE)))
        val file = folder.root.resolve("history").listFiles()!!.single()
        val original = file.readBytes()
        file.writeBytes(byteArrayOf(1))
        val model = ReceiverViewModel(FakeRepository(), FakeStore(), store, dispatcher)
        advanceUntilIdle()
        model.connect(connection, remember = false)
        advanceUntilIdle()
        assertNotNull(model.state.value.session)
        assertTrue(model.history.value.failed)
        file.writeBytes(original)
        model.refreshHistory()
        advanceUntilIdle()
        assertFalse(model.history.value.failed)
        assertEquals(1, model.history.value.entries.size)
    }

    private class FakeStore : SavedConnectionStore {
        var saved: ProviderConnection? = null
        var failSave = false
        override fun load() = saved
        override fun save(connection: ProviderConnection) {
            if (failSave) throw IllegalStateException("Synthetic storage failure")
            saved = connection
        }
        override fun clear() { saved = null }
    }
}
