package io.github.antocanza.iptvreceiver.ui

import io.github.antocanza.iptvreceiver.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GuideViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val connection = ProviderConnection("Fixture", "https://example.test", "demo", "synthetic")
    private val historic = CatalogItem("1", "Channel", ContentType.LIVE)
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `guide refresh enriches historical channel capabilities and removes stale schedules after errors`() = runTest(dispatcher) {
        val current = historic.copy(catchupDays = 7)
        var fail = false
        val repository = object : CatalogRepository by FakeRepository() {
            override suspend fun items(connection: ProviderConnection, type: ContentType, categoryId: String?) = listOf(current)
            override suspend fun guide(connection: ProviderConnection, item: CatalogItem): List<Programme> {
                assertEquals(7, item.catchupDays)
                if (fail) throw ProviderException(FailureReason.UNAVAILABLE)
                return listOf(Programme("p", "Fixture", "", 1, 2, false, null))
            }
        }
        val model = GuideViewModel(repository, connection, historic)
        advanceUntilIdle()
        assertEquals(current, model.state.value.channel)
        assertEquals(1, model.state.value.programmes.size)
        fail = true
        model.load()
        advanceUntilIdle()
        assertEquals(FailureReason.UNAVAILABLE, model.state.value.error)
        assertTrue(model.state.value.programmes.isEmpty())
    }

    @Test fun `retry cancels old guide work and removed channels never receive a guide request`() = runTest(dispatcher) {
        var calls = 0
        var cancellations = 0
        var removed = false
        val repository = object : CatalogRepository by FakeRepository() {
            override suspend fun items(connection: ProviderConnection, type: ContentType, categoryId: String?) = if (removed) emptyList() else listOf(historic)
            override suspend fun guide(connection: ProviderConnection, item: CatalogItem): List<Programme> {
                calls++
                try { awaitCancellation() } finally { cancellations++ }
            }
        }
        val model = GuideViewModel(repository, connection, historic)
        runCurrent()
        removed = true
        model.load()
        advanceUntilIdle()
        assertEquals(1, calls)
        assertEquals(1, cancellations)
        assertEquals(FailureReason.UNAVAILABLE, model.state.value.error)
    }
}
