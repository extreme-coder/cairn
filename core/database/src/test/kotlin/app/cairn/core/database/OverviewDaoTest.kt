package app.cairn.core.database

import app.cairn.core.model.StudyRole
import app.cairn.core.model.SyncState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The projections the list screens read.
 *
 * Every one of these is a query the UI cannot check for itself: a screen showing
 * "3 forms" has no way to notice that the number came from the wrong study, or
 * that a voided submission was counted. That is what this file is for.
 */
@RunWith(RobolectricTestRunner::class)
class OverviewDaoTest {

    private lateinit var db: CairnDatabase

    @Before
    fun setUp() = runTest {
        db = testDatabase()
        db.seedReferenceData()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ---- StudySummary ----

    @Test
    fun `a study with no forms and no submissions still appears`() = runTest {
        val rows = db.studies().observeSummaries(Ids.ADAKU).first()

        assertEquals(1, rows.size)
        assertEquals(1, rows.single().formCount)
        assertEquals(0, rows.single().submissionCount)
        assertEquals(0, rows.single().pendingCount)
    }

    @Test
    fun `a study the device holds with nothing else in it is still listed`() = runTest {
        db.studies().upsert(listOf(study(id = Ids.OTHER_STUDY, name = "Alpine pika transects")))

        val bare = db.studies().observeSummaries(Ids.ADAKU).first()
            .single { it.id == Ids.OTHER_STUDY }

        assertEquals(0, bare.formCount)
        assertEquals(0, bare.submissionCount)
        assertNull(bare.role)
    }

    @Test
    fun `counts belong to their own study`() = runTest {
        db.seedSecondStudy()
        db.submissions().upsert(submission(clientId = "c-1"))
        db.submissions().upsert(submission(clientId = "c-2"))
        db.submissions().upsert(
            submission(
                clientId = "c-3",
                studyId = Ids.OTHER_STUDY,
                formVersionId = Ids.OTHER_VERSION,
                participantId = Ids.OTHER_PARTICIPANT,
            ),
        )

        val byId = db.studies().observeSummaries(Ids.ADAKU).first().associateBy { it.id }

        assertEquals(2, byId.getValue(Ids.STUDY).submissionCount)
        assertEquals(1, byId.getValue(Ids.OTHER_STUDY).submissionCount)
    }

    @Test
    fun `the role shown is the signed-in user's, not another member's`() = runTest {
        db.members().upsert(listOf(member(userId = Ids.ADAKU, role = StudyRole.COLLECTOR)))
        db.members().upsert(listOf(member(userId = Ids.TOMAS, role = StudyRole.PI)))

        val adaku = db.studies().observeSummaries(Ids.ADAKU).first().single()
        val tomas = db.studies().observeSummaries(Ids.TOMAS).first().single()

        assertEquals(StudyRole.COLLECTOR, adaku.role)
        assertEquals(StudyRole.PI, tomas.role)
    }

    @Test
    fun `a study whose membership row has not arrived yet has no role`() = runTest {
        assertNull(db.studies().observeSummaries(Ids.ADAKU).first().single().role)
    }

    @Test
    fun `pending counts queued and failed but not uploaded`() = runTest {
        db.submissions().upsert(submission(clientId = "c-1", syncState = SyncState.QUEUED))
        db.submissions().upsert(submission(clientId = "c-2", syncState = SyncState.FAILED))
        db.submissions().upsert(submission(clientId = "c-3", syncState = SyncState.UPLOADED))

        val row = db.studies().observeSummaries(Ids.ADAKU).first().single()

        assertEquals(3, row.submissionCount)
        assertEquals(2, row.pendingCount)
    }

    @Test
    fun `a voided submission is counted by neither total nor pending`() = runTest {
        db.submissions().upsert(submission(clientId = "c-1", deletedAt = at(40)))

        val row = db.studies().observeSummaries(Ids.ADAKU).first().single()

        assertEquals(0, row.submissionCount)
        assertEquals(0, row.pendingCount)
    }

    @Test
    fun `studies are ordered by name`() = runTest {
        db.studies().upsert(listOf(study(id = Ids.OTHER_STUDY, name = "Alpine pika transects")))

        val names = db.studies().observeSummaries(Ids.ADAKU).first().map { it.name }

        assertEquals(listOf("Alpine pika transects", "Kestrel breeding survey"), names)
    }

    // ---- FormSummary ----

    @Test
    fun `a form offers its highest published version`() = runTest {
        db.forms().upsertVersions(listOf(formVersion(id = Ids.VERSION_2, version = 2)))

        val row = db.forms().observeFormSummaries(Ids.STUDY).first().single()

        assertEquals(2, row.version)
        assertEquals(Ids.VERSION_2, row.versionId)
    }

    @Test
    fun `a draft version is never offered, however high its number`() = runTest {
        db.forms().upsertVersions(
            listOf(formVersion(id = Ids.VERSION_2, version = 9, publishedAt = null)),
        )

        val row = db.forms().observeFormSummaries(Ids.STUDY).first().single()

        assertEquals(1, row.version)
        assertEquals(Ids.VERSION_1, row.versionId)
    }

    /**
     * The left join earning its keep. Forms and versions arrive in separate
     * pulls, so this is what a first sync looks like for a second or two — and
     * an inner join would show an empty study instead of a form that is not
     * ready yet.
     */
    @Test
    fun `a form whose versions have not arrived still appears, with no version`() = runTest {
        db.forms().upsertForms(listOf(form(id = "form-new", code = "trap_check")))

        val row = db.forms().observeFormSummaries(Ids.STUDY).first().single { it.id == "form-new" }

        assertNull(row.version)
        assertNull(row.versionId)
        assertNull(row.schema)
    }

    @Test
    fun `the schema comes back whole, so a field count can be taken from it`() = runTest {
        val row = db.forms().observeFormSummaries(Ids.STUDY).first().single()

        assertEquals(1, row.schema?.toFormSchema()?.fields?.size)
    }

    @Test
    fun `forms are scoped to their study and ordered by code`() = runTest {
        db.seedSecondStudy()
        db.forms().upsertForms(listOf(form(id = "form-a", code = "aardvark_check")))

        val codes = db.forms().observeFormSummaries(Ids.STUDY).first().map { it.code }

        assertEquals(listOf("aardvark_check", "baseline_intake"), codes)
    }

    // ---- The queue ----

    @Test
    fun `the queue holds what has not been uploaded, newest first`() = runTest {
        db.submissions().upsert(
            submission(clientId = "c-1", collectedAt = at(10), syncState = SyncState.QUEUED),
        )
        db.submissions().upsert(
            submission(clientId = "c-2", collectedAt = at(20), syncState = SyncState.FAILED),
        )
        db.submissions().upsert(
            submission(clientId = "c-3", collectedAt = at(30), syncState = SyncState.UPLOADED),
        )

        val rows = db.submissions().observePending(Ids.ADAKU).first()

        assertEquals(listOf("c-2", "c-1"), rows.map { it.clientId })
    }

    @Test
    fun `the queue spans studies and says which study each row belongs to`() = runTest {
        db.seedSecondStudy()
        db.submissions().upsert(submission(clientId = "c-1"))
        db.submissions().upsert(
            submission(
                clientId = "c-2",
                collectedAt = at(40),
                studyId = Ids.OTHER_STUDY,
                formVersionId = Ids.OTHER_VERSION,
                participantId = Ids.OTHER_PARTICIPANT,
            ),
        )

        val rows = db.submissions().observePending(Ids.ADAKU).first()

        assertEquals(
            listOf("Alpine pika transects", "Kestrel breeding survey"),
            rows.map { it.studyName }.sorted(),
        )
    }

    @Test
    fun `a queue row carries the form code and the version it was collected under`() = runTest {
        db.forms().upsertVersions(listOf(formVersion(id = Ids.VERSION_2, version = 7)))
        db.submissions().upsert(submission(clientId = "c-1", formVersionId = Ids.VERSION_2))

        val row = db.submissions().observePending(Ids.ADAKU).first().single()

        assertEquals("baseline_intake", row.formCode)
        assertEquals(7, row.version)
        assertEquals("K-014", row.participantCode)
    }

    /**
     * The left join on participants. A submission with no participant is
     * ordinary — not every observation is of a tagged animal — and an inner join
     * would drop exactly the rows the collector is trying to account for.
     */
    @Test
    fun `a submission with no participant is still in the queue`() = runTest {
        db.submissions().upsert(submission(clientId = "c-1", participantId = null))

        val row = db.submissions().observePending(Ids.ADAKU).first().single()

        assertNull(row.participantCode)
        assertEquals("c-1", row.clientId)
    }

    @Test
    fun `the queue is this collector's own, not the whole device's`() = runTest {
        db.submissions().upsert(submission(collectedBy = Ids.ADAKU, clientId = "c-1"))
        db.submissions().upsert(submission(collectedBy = Ids.TOMAS, clientId = "c-2"))

        assertEquals(1, db.submissions().observePending(Ids.ADAKU).first().size)
        assertEquals(1, db.submissions().observePending(Ids.TOMAS).first().size)
    }

    @Test
    fun `a voided submission is not in the queue`() = runTest {
        db.submissions().upsert(submission(clientId = "c-1", deletedAt = at(40)))

        assertTrue(db.submissions().observePending(Ids.ADAKU).first().isEmpty())
    }

    /**
     * A fresh device is zero rows, and an aggregate over zero rows is null.
     * Room happens to read that back as 0 even without the query's `coalesce`,
     * so this pins the behaviour rather than the `coalesce` — see the note on
     * `observeCounts`.
     */
    @Test
    fun `an empty device counts three zeroes rather than failing`() = runTest {
        val counts = db.submissions().observeCounts(Ids.ADAKU).first()

        assertEquals(0, counts.queued)
        assertEquals(0, counts.failed)
        assertEquals(0, counts.uploaded)
        assertEquals(0, counts.pending)
    }

    @Test
    fun `counts split queued, failed and uploaded`() = runTest {
        db.submissions().upsert(submission(clientId = "c-1", syncState = SyncState.QUEUED))
        db.submissions().upsert(submission(clientId = "c-2", syncState = SyncState.QUEUED))
        db.submissions().upsert(submission(clientId = "c-3", syncState = SyncState.FAILED))
        db.submissions().upsert(submission(clientId = "c-4", syncState = SyncState.UPLOADED))

        val counts = db.submissions().observeCounts(Ids.ADAKU).first()

        assertEquals(2, counts.queued)
        assertEquals(1, counts.failed)
        assertEquals(1, counts.uploaded)
        assertEquals(3, counts.pending)
        assertEquals(4, counts.total)
    }

    @Test
    fun `counts are this collector's own and exclude voided rows`() = runTest {
        db.submissions().upsert(submission(collectedBy = Ids.TOMAS, clientId = "c-1"))
        db.submissions().upsert(submission(clientId = "c-2", deletedAt = at(40)))

        assertEquals(0, db.submissions().observeCounts(Ids.ADAKU).first().total)
    }

    @Test
    fun `the uploaded list is uploaded only, newest first, and limited`() = runTest {
        repeat(4) { index ->
            db.submissions().upsert(
                submission(
                    clientId = "up-$index",
                    collectedAt = at(index),
                    syncState = SyncState.UPLOADED,
                ),
            )
        }
        db.submissions().upsert(submission(clientId = "queued", syncState = SyncState.QUEUED))

        val rows = db.submissions().observeUploaded(Ids.ADAKU, limit = 2).first()

        assertEquals(listOf("up-3", "up-2"), rows.map { it.clientId })
    }

    @Test
    fun `recent is scoped to one study, whatever the state, newest first`() = runTest {
        db.seedSecondStudy()
        db.submissions().upsert(
            submission(clientId = "c-1", collectedAt = at(10), syncState = SyncState.UPLOADED),
        )
        db.submissions().upsert(
            submission(clientId = "c-2", collectedAt = at(20), syncState = SyncState.QUEUED),
        )
        db.submissions().upsert(
            submission(
                clientId = "elsewhere",
                collectedAt = at(30),
                studyId = Ids.OTHER_STUDY,
                formVersionId = Ids.OTHER_VERSION,
                participantId = Ids.OTHER_PARTICIPANT,
            ),
        )

        val rows = db.submissions().observeRecent(Ids.STUDY, Ids.ADAKU, limit = 5).first()

        assertEquals(listOf("c-2", "c-1"), rows.map { it.clientId })
    }

    @Test
    fun `recent honours its limit`() = runTest {
        repeat(4) { index ->
            db.submissions().upsert(submission(clientId = "c-$index", collectedAt = at(index)))
        }

        val rows = db.submissions().observeRecent(Ids.STUDY, Ids.ADAKU, limit = 2).first()

        assertEquals(listOf("c-3", "c-2"), rows.map { it.clientId })
    }
}
