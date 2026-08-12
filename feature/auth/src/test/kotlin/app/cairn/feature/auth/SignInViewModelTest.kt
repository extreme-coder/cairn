package app.cairn.feature.auth

import app.cairn.core.network.SignInOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * What the screen is told after an attempt.
 *
 * The distinction being defended is the one the session layer went to trouble to
 * preserve: a refusal and an unreachable server must not arrive as the same
 * sentence, because the first means retype your password and the second means
 * your password is probably fine.
 */
class SignInViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun install() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun remove() {
        Dispatchers.resetMain()
    }

    private val attempts = mutableListOf<Pair<String, String>>()

    private fun viewModel(
        server: String = "cairn.psych.ubc.ca",
        outcome: SignInOutcome = SignInOutcome.Success,
    ) = SignInViewModel(
        signIn = { email, password ->
            attempts += email to password
            outcome
        },
        server = server,
    )

    @Test
    fun `nothing can be submitted until both fields are filled`() {
        val model = viewModel()

        assertFalse(model.state.value.canSubmit)
        model.setEmail("adaku@cairn.test")
        assertFalse(model.state.value.canSubmit)
        model.setPassword("cairn-dev-password")
        assertTrue(model.state.value.canSubmit)
    }

    @Test
    fun `a refusal is not the same as an unreachable server`() = runTest(dispatcher) {
        val refused = viewModel(outcome = SignInOutcome.Rejected("Invalid login credentials"))
        refused.setEmail("adaku@cairn.test")
        refused.setPassword("wrong")
        refused.signIn()
        dispatcher.scheduler.advanceUntilIdle()

        val offline = viewModel(outcome = SignInOutcome.Unreachable)
        offline.setEmail("adaku@cairn.test")
        offline.setPassword("cairn-dev-password")
        offline.signIn()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(SignInProblem.REFUSED, refused.state.value.problem)
        assertEquals(SignInProblem.UNREACHABLE, offline.state.value.problem)
        assertTrue(refused.state.value.problem?.message() != offline.state.value.problem?.message())
    }

    @Test
    fun `the password is dropped once it has worked`() = runTest(dispatcher) {
        val model = viewModel()
        model.setEmail("adaku@cairn.test")
        model.setPassword("cairn-dev-password")

        model.signIn()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("", model.state.value.password)
        assertNull(model.state.value.problem)
    }

    /** A rejected password is still on screen to be corrected, not silently cleared. */
    @Test
    fun `a refused password is kept so it can be fixed`() = runTest(dispatcher) {
        val model = viewModel(outcome = SignInOutcome.Rejected(null))
        model.setEmail("adaku@cairn.test")
        model.setPassword("wrongish")

        model.signIn()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("wrongish", model.state.value.password)
    }

    @Test
    fun `editing clears the last problem`() = runTest(dispatcher) {
        val model = viewModel(outcome = SignInOutcome.Rejected(null))
        model.setEmail("adaku@cairn.test")
        model.setPassword("wrong")
        model.signIn()
        dispatcher.scheduler.advanceUntilIdle()

        model.setPassword("wrong2")

        assertNull(model.state.value.problem)
    }

    @Test
    fun `submitting twice sends one attempt`() = runTest(dispatcher) {
        val model = viewModel()
        model.setEmail("adaku@cairn.test")
        model.setPassword("cairn-dev-password")

        model.signIn()
        model.signIn()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, attempts.size)
    }

    /**
     * A build with no server address says so and stays saying so. Typing does not
     * make a missing `BuildConfig` value appear.
     */
    @Test
    fun `an unconfigured build cannot be submitted`() {
        val model = viewModel(server = "")

        model.setEmail("adaku@cairn.test")
        model.setPassword("cairn-dev-password")

        assertFalse(model.state.value.canSubmit)
        assertEquals(SignInProblem.UNCONFIGURED, model.state.value.problem)
    }

    @Test
    fun `revealing the password is a toggle`() {
        val model = viewModel()

        assertFalse(model.state.value.revealPassword)
        model.toggleReveal()
        assertTrue(model.state.value.revealPassword)
        model.toggleReveal()
        assertFalse(model.state.value.revealPassword)
    }
}
