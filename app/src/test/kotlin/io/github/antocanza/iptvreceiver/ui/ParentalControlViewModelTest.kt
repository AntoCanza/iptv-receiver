package io.github.antocanza.iptvreceiver.ui

import io.github.antocanza.iptvreceiver.R
import io.github.antocanza.iptvreceiver.data.ParentalControls
import io.github.antocanza.iptvreceiver.model.ProviderConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ParentalControlViewModelTest {
    @get:Rule val folder = TemporaryFolder()
    private val dispatcher = StandardTestDispatcher()
    private val connection = ProviderConnection("Test", "https://provider.example.test", "demo", "synthetic")
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `setup validates confirmation relocks and reset requires creating a new password`() = runTest(dispatcher) {
        val controls = ParentalControls(folder.root.resolve("settings.bin"))
        val model = ParentalControlViewModel(controls, FakeRepository(), connection, dispatcher)
        model.submit("abc", "abc")
        assertEquals(R.string.parental_password_length, model.state.value.error)
        model.submit("parent-password", "different")
        assertEquals(R.string.parental_password_mismatch, model.state.value.error)
        assertFalse(controls.policy.value.passwordSet)
        model.submit("parent-password", "parent-password")
        advanceUntilIdle()
        assertTrue(model.state.value.unlocked)
        assertTrue(model.state.value.policy.blockAdult)
        model.setAdultBlock(false)
        advanceUntilIdle()
        model.lock()
        assertFalse(model.state.value.unlocked)
        model.submit("wrong-password", "")
        advanceUntilIdle()
        assertFalse(model.state.value.unlocked)
        assertEquals(R.string.parental_wrong_password, model.state.value.error)
        model.submit("parent-password", "")
        advanceUntilIdle()
        assertTrue(model.state.value.unlocked)
        model.reset()
        advanceUntilIdle()
        assertFalse(model.state.value.unlocked)
        assertFalse(model.state.value.policy.passwordSet)
        assertTrue(model.state.value.policy.blockAdult)
    }

    @Test fun `leaving during pending password check never unlocks the panel later`() = runTest(dispatcher) {
        val controls = ParentalControls(folder.root.resolve("settings.bin"))
        controls.create("parent-password")
        val model = ParentalControlViewModel(controls, FakeRepository(), connection, dispatcher)
        model.submit("parent-password", "")
        model.lock()
        advanceUntilIdle()
        assertFalse(model.state.value.unlocked)
        model.setAdultBlock(false)
        advanceUntilIdle()
        assertTrue(controls.policy.value.blockAdult)
    }
}
