package io.github.antocanza.iptvreceiver.ui

import io.github.antocanza.iptvreceiver.data.SavedConnectionStore
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiverViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val connection = ProviderConnection("Test", "https://provider.example.test", "demo", "synthetic-secret")
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `remembered login stores connection and sign out removes it`() = runTest(dispatcher) {
        val store = FakeStore()
        val model = ReceiverViewModel(FakeRepository(), store, dispatcher)
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
        val model = ReceiverViewModel(FakeRepository(), store, dispatcher)
        advanceUntilIdle()
        model.connect(connection, remember = false)
        advanceUntilIdle()
        assertNull(store.saved)
        assertNotNull(model.state.value.session)
    }

    @Test fun `secure storage failure exposes a translatable code and does not create a session`() = runTest(dispatcher) {
        val store = FakeStore().apply { failSave = true }
        val model = ReceiverViewModel(FakeRepository(), store, dispatcher)
        advanceUntilIdle()
        model.connect(connection, remember = true)
        advanceUntilIdle()
        assertNull(model.state.value.session)
        assertEquals(FailureReason.SAVE_FAILED, model.state.value.error)
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
