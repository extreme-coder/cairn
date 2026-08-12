package app.cairn.feature.auth

import app.cairn.core.session.SignOutOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The two-step sign-out.
 *
 * Nobody is forced past the repository's refusal by the same tap that ran into
 * it. Someone who has not been told they are about to delete three observations
 * has not agreed to delete three observations.
 */
class SignOutViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun install() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun remove() {
        Dispatchers.resetMain()
    }

    private val forced = mutableListOf<Boolean>()

    private fun viewModel(pending: Int = 0) = SignOutViewModel { force ->
        forced += force
        if (pending > 0 && !force) SignOutOutcome.HeldBack(pending) else SignOutOutcome.SignedOut
    }

    @Test
    fun `asking shows the confirmation and signs nobody out`() {
        val model = viewModel()

        model.ask()

        assertEquals(SignOutUiState.Confirming, model.state.value)
        assertTrue(forced.isEmpty())
    }

    @Test
    fun `confirming with an empty queue signs out`() = runTest(dispatcher) {
        val model = viewModel()
        model.ask()

        model.confirm()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(false), forced)
        assertEquals(SignOutUiState.Hidden, model.state.value)
    }

    @Test
    fun `a queue turns the confirmation into a warning that counts it`() = runTest(dispatcher) {
        val model = viewModel(pending = 3)
        model.ask()

        model.confirm()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(SignOutUiState.WouldDiscard(3), model.state.value)
        assertEquals(listOf(false), forced)
    }

    @Test
    fun `only the second confirmation forces the wipe`() = runTest(dispatcher) {
        val model = viewModel(pending = 3)
        model.ask()
        model.confirm()
        dispatcher.scheduler.advanceUntilIdle()

        model.confirm()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(false, true), forced)
        assertEquals(SignOutUiState.Hidden, model.state.value)
    }

    @Test
    fun `dismissing the warning signs nobody out`() = runTest(dispatcher) {
        val model = viewModel(pending = 2)
        model.ask()
        model.confirm()
        dispatcher.scheduler.advanceUntilIdle()

        model.dismiss()

        assertEquals(SignOutUiState.Hidden, model.state.value)
        assertEquals(listOf(false), forced)
    }

    /** The count on the screen is the count the repository refused on, not a second read. */
    @Test
    fun `the warning carries the repository's own count`() = runTest(dispatcher) {
        val model = viewModel(pending = 11)
        model.ask()

        model.confirm()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(11, (model.state.value as SignOutUiState.WouldDiscard).pending)
    }

    @Test
    fun `one submission is described in the singular`() {
        assertEquals("1 submission has", 1.submissions())
        assertEquals("2 submissions have", 2.submissions())
    }
}
