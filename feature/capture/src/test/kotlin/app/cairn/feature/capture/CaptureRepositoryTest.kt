package app.cairn.feature.capture

import app.cairn.core.database.CairnDatabase
import app.cairn.core.model.SyncState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CaptureRepositoryTest {

    private lateinit var db: CairnDatabase
    private lateinit var repository: CaptureRepository

    @Before
    fun setUp() = runTest {
        db = testDatabase()
        db.seedKestrelStudy()
        repository = CaptureRepository(db.forms(), db.submissions())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun open(
        formId: String = Ids.FORM,
        collectedBy: String = Ids.ADAKU,
        participantId: String? = Ids.PARTICIPANT,
        clientId: String? = null,
    ): FormOpening = if (clientId == null) {
        repository.openForm(
            studyId = Ids.STUDY,
            formId = formId,
            collectedBy = collectedBy,
            openedAt = at(30),
            participantId = participantId,
        )
    } else {
        repository.openForm(
            studyId = Ids.STUDY,
            formId = formId,
            collectedBy = collectedBy,
            openedAt = at(30),
            participantId = participantId,
            clientId = clientId,
        )
    }

    private suspend fun openReady(formId: String = Ids.FORM, clientId: String? = null): CaptureState =
        (open(formId = formId, clientId = clientId) as FormOpening.Ready).state

    @Test
    fun `opening a form gives the highest published version`() = runTest {
        val state = openReady()

        assertEquals(Ids.VERSION_2, state.formVersionId)
        assertEquals(kestrelSchema, state.schema)
    }

    @Test
    fun `a form whose only version is unpublished cannot be opened`() = runTest {
        val opening = open(formId = Ids.DRAFT_FORM)

        assertEquals(
            FormOpening.Unopenable(UnopenableReason.NO_PUBLISHED_VERSION),
            opening,
        )
    }

    @Test
    fun `a schema this build cannot decode fails one form, not the study`() = runTest {
        val opening = open(formId = Ids.FUTURE_FORM)

        assertEquals(
            FormOpening.Unopenable(UnopenableReason.SCHEMA_NOT_UNDERSTOOD),
            opening,
        )
        assertEquals(Ids.VERSION_2, openReady().formVersionId)
    }

    @Test
    fun `each open mints its own client id`() = runTest {
        assertNotEquals(openReady().clientId, openReady().clientId)
    }

    @Test
    fun `saving queues the submission with no server id yet`() = runTest {
        val outcome = repository.save(openReady().filledIn(), at(45))

        assertTrue(outcome is SaveOutcome.Queued)
        val row = db.submissions().observeForStudy(Ids.STUDY).first().single()
        assertNull(row.id)
        assertEquals(SyncState.QUEUED, row.syncState)
        assertEquals(at(45), row.pendingSince)
        assertEquals(Ids.VERSION_2, row.formVersionId)
        assertEquals(Ids.PARTICIPANT, row.participantId)
    }

    @Test
    fun `collected at is when the form opened, not when it was saved`() = runTest {
        repository.save(openReady().filledIn(), at(45))

        val row = db.submissions().observeForStudy(Ids.STUDY).first().single()
        assertEquals(at(30), row.collectedAt)
        assertEquals(at(45), row.updatedAt)
    }

    @Test
    fun `the queued row carries the schema ordered payload`() = runTest {
        val filled = openReady()
            .setText("notes", "Recaptured at dusk.")
            .setChoice("sex", "female")
            .setNumber("body_mass", "268")

        repository.save(filled, at(45))

        val row = db.submissions().observeForStudy(Ids.STUDY).first().single()
        assertEquals(listOf("body_mass", "sex", "notes"), row.data.keys.toList())
        assertEquals(JsonPrimitive(268.0), row.data["body_mass"])
    }

    @Test
    fun `saving twice from one open updates a single row`() = runTest {
        val state = openReady(clientId = "cccccccc-0000-0000-0000-000000000001").filledIn()

        repository.save(state, at(45))
        repository.save(state.setText("notes", "Recaptured at dusk."), at(50))

        val rows = db.submissions().observeForStudy(Ids.STUDY).first()
        assertEquals(1, rows.size)
        assertEquals(JsonPrimitive("Recaptured at dusk."), rows.single().data["notes"])
        assertEquals(at(50), rows.single().updatedAt)
    }

    @Test
    fun `an invalid submission is refused and nothing is written`() = runTest {
        val outcome = repository.save(openReady().setNumber("body_mass", "512"), at(45))

        val invalid = outcome as SaveOutcome.Invalid
        assertEquals(
            listOf(
                "Body mass must be between 90 and 400 g.",
                "Sex is required.",
            ),
            invalid.errors.map { it.message() },
        )
        assertTrue(db.submissions().observeForStudy(Ids.STUDY).first().isEmpty())
    }

    @Test
    fun `a refused save turns the errors on for the screen`() = runTest {
        val outcome = repository.save(openReady(), at(45)) as SaveOutcome.Invalid

        assertTrue(outcome.state.hasAttemptedSave)
        assertEquals(outcome.errors, outcome.state.visibleErrors)
    }

    @Test
    fun `two collectors filling the same form keep separate rows`() = runTest {
        val clientId = "cccccccc-0000-0000-0000-000000000002"
        val adaku = (open(clientId = clientId) as FormOpening.Ready).state
        val tomas = (
            open(collectedBy = Ids.TOMAS, clientId = clientId) as FormOpening.Ready
            ).state

        repository.save(adaku.filledIn(), at(45))
        repository.save(tomas.filledIn(), at(46))

        assertEquals(2, db.submissions().observeForStudy(Ids.STUDY).first().size)
        assertEquals(1, db.submissions().observeForCollector(Ids.STUDY, Ids.ADAKU).first().size)
    }

    @Test
    fun `a submission with no participant is queued all the same`() = runTest {
        val state = (open(participantId = null) as FormOpening.Ready).state

        repository.save(state.filledIn(), at(45))

        assertNull(db.submissions().observeForStudy(Ids.STUDY).first().single().participantId)
    }

    @Test
    fun `what capture queues is what the sync worker will drain`() = runTest {
        repository.save(openReady().filledIn(), at(45))

        assertEquals(1, db.submissions().awaiting().size)
        assertEquals(1, db.submissions().observeUnsyncedCount().first())
    }
}
