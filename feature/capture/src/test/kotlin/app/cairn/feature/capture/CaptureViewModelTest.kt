package app.cairn.feature.capture

import app.cairn.core.database.CairnDatabase
import app.cairn.core.model.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class CaptureViewModelTest {

    private lateinit var db: CairnDatabase

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = testDatabase()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun viewModel(formId: String = Ids.FORM, now: Instant = at(30)) = CaptureViewModel(
        repository = CaptureRepository(db.forms(), db.submissions()),
        forms = db.forms(),
        submissions = db.submissions(),
        studyId = Ids.STUDY,
        formId = formId,
        collectedBy = Ids.ADAKU,
        now = { now },
    )

    @Test
    fun `opening publishes the current version, its number and its schema`() = runTest {
        db.seedKestrelStudy()
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }

        val state = vm.uiState.first { it is CaptureUiState.Editing } as CaptureUiState.Editing
        assertEquals("Baseline intake", state.form.title)
        assertEquals("v2", state.form.versionLabel)
        assertEquals(kestrelSchema, state.capture.schema)
    }

    @Test
    fun `a form with no published version reports why rather than showing an empty form`() =
        runTest {
            db.seedKestrelStudy()
            val vm = viewModel(formId = Ids.DRAFT_FORM)
            backgroundScope.launch { vm.uiState.collect {} }

            val state = vm.uiState.first { it is CaptureUiState.Unopenable }
            assertEquals(
                CaptureUiState.Unopenable(UnopenableReason.NO_PUBLISHED_VERSION),
                state,
            )
        }

    @Test
    fun `editing forwards to the capture state and nothing else`() = runTest {
        db.seedKestrelStudy()
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.first { it is CaptureUiState.Editing }

        vm.edit { it.setNumber("body_mass", "268") }

        val state = vm.uiState.value as CaptureUiState.Editing
        assertTrue(state.capture.payload.containsKey("body_mass"))
        assertNull(state.savedClientId)
    }

    @Test
    fun `saving an invalid form turns the errors on and writes nothing`() = runTest {
        db.seedKestrelStudy()
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.first { it is CaptureUiState.Editing }

        vm.save()

        val state = vm.uiState.value as CaptureUiState.Editing
        assertTrue(state.capture.hasAttemptedSave)
        assertEquals(
            listOf("Body mass is required.", "Sex is required."),
            state.capture.visibleErrors.map { it.message() },
        )
        assertNull(state.savedClientId)
        assertTrue(db.submissions().observeForStudy(Ids.STUDY).first().isEmpty())
    }

    @Test
    fun `saving a valid form queues it in Room`() = runTest {
        db.seedKestrelStudy()
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.first { it is CaptureUiState.Editing }

        vm.edit { it.setNumber("body_mass", "268").setChoice("sex", "female") }
        vm.save()

        val state = vm.uiState.value as CaptureUiState.Editing
        assertEquals(state.capture.clientId, state.savedClientId)

        val row = db.submissions().observeForStudy(Ids.STUDY).first().single()
        assertEquals(SyncState.QUEUED, row.syncState)
        assertEquals(Ids.VERSION_2, row.formVersionId)
        assertNull(row.id)
    }

    @Test
    fun `the queued count is read from the database, not counted in the screen`() = runTest {
        db.seedKestrelStudy()
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.first { it is CaptureUiState.Editing }

        assertEquals(0, (vm.uiState.value as CaptureUiState.Editing).queuedCount)

        vm.edit { it.setNumber("body_mass", "268").setChoice("sex", "female") }
        vm.save()

        assertEquals(1, (vm.uiState.value as CaptureUiState.Editing).queuedCount)
    }

    @Test
    fun `starting another submission mints a new client id`() = runTest {
        db.seedKestrelStudy()
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.first { it is CaptureUiState.Editing }
        val first = (vm.uiState.value as CaptureUiState.Editing).capture.clientId

        vm.openForm()
        vm.uiState.first { it is CaptureUiState.Editing }

        val second = (vm.uiState.value as CaptureUiState.Editing).capture.clientId
        assertNotEquals(first, second)
    }

    @Test
    fun `two submissions from one session are two rows, not an overwrite`() = runTest {
        db.seedKestrelStudy()
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.first { it is CaptureUiState.Editing }

        vm.edit { it.setNumber("body_mass", "268").setChoice("sex", "female") }
        vm.save()
        vm.openForm()
        vm.uiState.first { (it as? CaptureUiState.Editing)?.savedClientId == null }
        vm.edit { it.setNumber("body_mass", "301").setChoice("sex", "male") }
        vm.save()

        assertEquals(2, db.submissions().observeForStudy(Ids.STUDY).first().size)
    }
}
