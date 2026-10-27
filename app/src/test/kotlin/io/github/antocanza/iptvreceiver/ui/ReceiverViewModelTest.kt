package io.github.antocanza.iptvreceiver.ui

import io.github.antocanza.iptvreceiver.data.SavedConnectionStore
import io.github.antocanza.iptvreceiver.data.FileWatchHistoryStore
import io.github.antocanza.iptvreceiver.data.WatchTarget
import io.github.antocanza.iptvreceiver.data.FilePlaybackProgressStore
import io.github.antocanza.iptvreceiver.data.PlaybackProgress
import io.github.antocanza.iptvreceiver.data.SubscriptionReminders
import io.github.antocanza.iptvreceiver.model.PlaybackSource
import io.github.antocanza.iptvreceiver.model.CatalogItem
import io.github.antocanza.iptvreceiver.model.ContentType
import io.github.antocanza.iptvreceiver.model.FailureReason
import io.github.antocanza.iptvreceiver.model.Account
import io.github.antocanza.iptvreceiver.model.CatalogRepository
import io.github.antocanza.iptvreceiver.model.ProviderConnection
import io.github.antocanza.iptvreceiver.model.ProviderException
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

    @Test fun `rejected credentials show an authentication error and a corrected login can succeed`() = runTest(dispatcher) {
        val store = FakeStore()
        var rejection: FailureReason? = FailureReason.AUTHENTICATION_FAILED
        val repository = object : CatalogRepository by FakeRepository() {
            override suspend fun authenticate(connection: ProviderConnection): Account {
                rejection?.let { throw ProviderException(it) }
                return Account("Active", null, listOf("ts"))
            }
        }
        val model = ReceiverViewModel(repository, store, historyStore, dispatcher)
        advanceUntilIdle()
        for (reason in listOf(FailureReason.AUTHENTICATION_FAILED, FailureReason.ACCESS_DENIED)) {
            rejection = reason
            model.connect(connection, remember = true)
            advanceUntilIdle()
            assertEquals(FailureReason.AUTHENTICATION_FAILED, model.state.value.error)
            assertNull(model.state.value.session)
            assertNull(store.saved)
        }
        rejection = null
        model.connect(connection, remember = true)
        advanceUntilIdle()
        assertNotNull(model.state.value.session)
        assertNull(model.state.value.error)
        assertEquals(connection, store.saved)
    }

    @Test fun `ambiguous login failures do not claim the provider is offline or overwrite a saved account`() = runTest(dispatcher) {
        for (failure in listOf(FailureReason.UNAVAILABLE, FailureReason.UNREACHABLE, FailureReason.UNSUPPORTED_RESPONSE,
            FailureReason.RESPONSE_UNREADABLE, FailureReason.EMPTY_RESPONSE, FailureReason.UNKNOWN)) {
            val store = FakeStore()
            var rejected = false
            val repository = object : CatalogRepository by FakeRepository() {
                override suspend fun authenticate(connection: ProviderConnection): Account {
                    if (rejected) throw ProviderException(failure)
                    return Account("Active", null, listOf("ts"))
                }
            }
            val model = ReceiverViewModel(repository, store, historyStore, dispatcher)
            advanceUntilIdle()
            model.connect(connection, remember = true)
            advanceUntilIdle()
            rejected = true
            model.connect(ProviderConnection("Retry", connection.serverUrl, "demo", "wrong-synthetic-password"), remember = true)
            advanceUntilIdle()
            assertEquals(FailureReason.LOGIN_FAILED, model.state.value.error)
            assertFalse(model.state.value.busy)
            assertNull(model.state.value.session)
            assertEquals(connection, store.saved)
        }
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

    @Test fun `history shows durable progress and ignores old session and replaced player callbacks`() = runTest(dispatcher) {
        val progress = FilePlaybackProgressStore(folder.root.resolve("progress"))
        val model = ReceiverViewModel(FakeRepository(), FakeStore(), historyStore, dispatcher, progress)
        advanceUntilIdle(); model.connect(connection, false); advanceUntilIdle()
        val session = checkNotNull(model.state.value.session)
        val target = WatchTarget(CatalogItem("movie", "Movie", ContentType.MOVIE))
        val first = PlaybackSource("https://fixture.example.test/first.mp4", "Movie", false)
        val replacement = PlaybackSource("https://fixture.example.test/replacement.mp4", "Movie", false)
        model.preparePlayback(first, target)
        model.recordWatch(target, session); advanceUntilIdle()
        val point = PlaybackProgress(900_000, 6_600_000)
        model.recordProgress(target, session, point, first); advanceUntilIdle()
        val recorded = checkNotNull(model.resumeProgress(target, session))
        assertEquals(point, recorded.copy(lastPlayedAtMillis = null))
        assertEquals(recorded, model.history.value.entries.single().progress)
        model.preparePlayback(replacement, target, 900_000)
        model.recordProgress(target, session, point.copy(positionMs = 0), first); advanceUntilIdle()
        assertEquals(recorded, model.resumeProgress(target, session))
        model.signOut(); advanceUntilIdle()
        model.connect(connection.with(username = "other"), false); advanceUntilIdle()
        model.recordProgress(target, session, point.copy(positionMs = 0)); advanceUntilIdle()
        assertNull(model.resumeProgress(target, checkNotNull(model.state.value.session)))
        model.connect(connection, false); advanceUntilIdle()
        assertEquals(recorded, model.history.value.entries.single().progress)
        assertEquals(recorded, FilePlaybackProgressStore(folder.root.resolve("progress")).load(connection)[target.key])
    }

    @Test fun `expiry popup OK persists and signout hides it`() = runTest(dispatcher) {
        val now = java.time.Instant.parse("2026-10-27T12:00:00Z")
        val expiry = now.plusSeconds(20 * 86400).epochSecond
        val repository = object : CatalogRepository by FakeRepository() {
            override suspend fun authenticate(connection: ProviderConnection) = Account("Active", expiry, listOf("ts"))
        }
        val reminderDirectory = folder.root.resolve("reminders")
        fun create() = ReceiverViewModel(repository, FakeStore(), historyStore, dispatcher,
            reminders = SubscriptionReminders(reminderDirectory, { now }))
        val model = create(); advanceUntilIdle(); model.connect(connection, false); advanceUntilIdle()
        model.checkExpiryReminder(); advanceUntilIdle()
        assertNotNull(model.expiryNotice.value)
        model.acknowledgeExpiryReminder()
        assertNull(model.expiryNotice.value)
        advanceUntilIdle(); model.checkExpiryReminder(); advanceUntilIdle()
        assertNull(model.expiryNotice.value)
        val reopened = create(); advanceUntilIdle(); reopened.connect(connection, false); advanceUntilIdle()
        reopened.checkExpiryReminder(); advanceUntilIdle()
        assertNull(reopened.expiryNotice.value)
        reopened.connect(connection.with(username = "other"), false); advanceUntilIdle()
        reopened.checkExpiryReminder(); advanceUntilIdle()
        assertNotNull(reopened.expiryNotice.value)
        reopened.signOut(); advanceUntilIdle()
        assertNull(reopened.expiryNotice.value)
    }

    @Test fun `history is paged and clear cannot be undone by checkpoints queued before the user action`() = runTest(dispatcher) {
        val history = historyStore
        val progress = FilePlaybackProgressStore(folder.root.resolve("progress"))
        repeat(60) { history.record(connection, WatchTarget(CatalogItem("$it", "Movie $it", ContentType.MOVIE))) }
        val model = ReceiverViewModel(FakeRepository(), FakeStore(), history, dispatcher, progress)
        advanceUntilIdle(); model.connect(connection, false); advanceUntilIdle()
        assertEquals(50, model.history.value.entries.size)
        assertTrue(model.history.value.hasMore)
        model.loadMoreHistory(); advanceUntilIdle()
        assertEquals(60, model.history.value.entries.size)
        assertFalse(model.history.value.hasMore)
        val session = checkNotNull(model.state.value.session)
        val target = WatchTarget(CatalogItem("42", "Movie", ContentType.MOVIE))
        model.recordProgress(target, session, PlaybackProgress(60_000, null))
        model.recordWatch(target, session)
        model.clearViewingData(); advanceUntilIdle()
        assertTrue(model.viewingData.value.cleared)
        assertTrue(model.history.value.entries.isEmpty())
        assertTrue(progress.load(connection).isEmpty())
        assertTrue(history.load(connection).isEmpty())
    }

    @Test fun `restart preserves watched state and duration and updates details revision`() = runTest(dispatcher) {
        val progress = FilePlaybackProgressStore(folder.root.resolve("progress"))
        val target = WatchTarget(CatalogItem("42", "Movie", ContentType.MOVIE))
        progress.save(connection, target, PlaybackProgress.checkpoint(100_000, 100_000))
        val model = ReceiverViewModel(FakeRepository(), FakeStore(), historyStore, dispatcher, progress)
        advanceUntilIdle(); model.connect(connection, false); advanceUntilIdle()
        model.restartProgress(target, checkNotNull(model.state.value.session))
        val point = checkNotNull(progress.read(connection, target))
        assertTrue(point.watched)
        assertFalse(point.completed)
        assertEquals(0L, point.positionMs)
        assertEquals(100_000L, point.durationMs)
        assertTrue(model.progressRevision.value > 0)
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

private fun ProviderConnection.with(name: String = this.name, serverUrl: String = this.serverUrl, username: String = this.username, password: String = this.password) = ProviderConnection(name, serverUrl, username, password)
