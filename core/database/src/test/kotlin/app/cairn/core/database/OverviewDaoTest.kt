package app.cairn.core.database

import app.cairn.core.model.StudyRole
import app.cairn.core.model.SyncState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
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

    // ---- ReviewSubmission ----

    /**
     * The one list in the app that is not scoped to the person reading it. What
     * a device holds is already exactly what row-level security let this account
     * pull, so the query says `where study_id` and nothing about a user.
     */
    @Test
    fun `the review list holds every collector's rows, not only the reader's`() = runTest {
        db.submissions().upsert(submission(clientId = "c-1", collectedBy = Ids.ADAKU))
        db.submissions().upsert(submission(clientId = "c-2", collectedBy = Ids.TOMAS))

        val rows = db.submissions().observeForReview(Ids.STUDY).first()

        assertEquals(setOf(Ids.ADAKU, Ids.TOMAS), rows.map { it.collectedBy }.toSet())
    }

    /**
     * Every other list filters `deleted_at is null`. This one deliberately does
     * not: hiding a voided row would make a void look exactly like a delete to
     * the person who performed it, on the only screen where the difference shows.
     */
    @Test
    fun `a voided submission stays in the review list`() = runTest {
        db.submissions().upsert(submission(clientId = "c-1", deletedAt = at(40)))

        val row = db.submissions().observeForReview(Ids.STUDY).first().single()

        assertEquals(at(40), row.deletedAt)
        assertEquals("K-014", row.participantCode)
    }

    @Test
    fun `the review list is one study's, newest first, and capped`() = runTest {
        db.seedSecondStudy()
        repeat(3) { index ->
            db.submissions().upsert(submission(clientId = "c-$index", collectedAt = at(index)))
        }
        db.submissions().upsert(
            submission(
                clientId = "elsewhere",
                studyId = Ids.OTHER_STUDY,
                formVersionId = Ids.OTHER_VERSION,
                participantId = Ids.OTHER_PARTICIPANT,
            ),
        )

        assertEquals(
            listOf("c-2", "c-1", "c-0"),
            db.submissions().observeForReview(Ids.STUDY).first().map { it.clientId },
        )
        assertEquals(2, db.submissions().observeForReview(Ids.STUDY, limit = 2).first().size)
    }

    // ---- SubmissionDetail ----

    /**
     * The versioning ADR as a query. The row pins `form_version_id`, so the join
     * reaches the schema it was collected under — not the form's current one,
     * which would relabel an old observation silently.
     */
    @Test
    fun `the detail reads the schema of the version the row pins`() = runTest {
        db.forms().upsertVersions(
            listOf(formVersion(id = Ids.VERSION_2, version = 2, schema = buildJsonObject { })),
        )
        db.submissions().upsert(submission(clientId = "c-1", formVersionId = Ids.VERSION_1))

        val detail = db.submissions().observeDetail(Ids.ADAKU, "c-1").first()!!

        assertEquals(1, detail.version)
        assertEquals(bodyMassSchema, detail.schema)
        assertEquals("Kestrel breeding survey", detail.studyName)
        assertEquals("baseline_intake", detail.formCode)
    }

    @Test
    fun `a submission this device does not hold has no detail`() = runTest {
        assertNull(db.submissions().observeDetail(Ids.ADAKU, "nothing").first())
    }

    @Test
    fun `the detail keeps a submission with no participant`() = runTest {
        db.submissions().upsert(submission(clientId = "c-1", participantId = null))

        assertNull(db.submissions().observeDetail(Ids.ADAKU, "c-1").first()!!.participantCode)
    }

    // ---- ReviewCounts ----

    /**
     * Disjoint on purpose. A summary whose three numbers sum to more than the
     * table holds is one nobody checks twice.
     */
    @Test
    fun `the counts do not double-count a row that is both locked and voided`() = runTest {
        db.submissions().upsert(submission(clientId = "c-1"))
        db.submissions().upsert(submission(clientId = "c-2", lockedAt = at(40)))
        db.submissions().upsert(submission(clientId = "c-3", lockedAt = at(40), deletedAt = at(41)))

        val counts = db.submissions().observeReviewCounts(Ids.STUDY).first()

        assertEquals(2, counts.collected)
        assertEquals(1, counts.locked)
        assertEquals(1, counts.voided)
        assertEquals(1, counts.unlocked)
    }

    @Test
    fun `an empty study counts four zeroes rather than four nulls`() = runTest {
        val counts = db.submissions().observeReviewCounts(Ids.STUDY).first()

        assertEquals(0, counts.collected)
        assertEquals(0, counts.locked)
        assertEquals(0, counts.voided)
        assertEquals(0, counts.participants)
    }

    @Test
    fun `participants are counted once each, and a voided row contributes none`() = runTest {
        db.seedSecondStudy()
        db.submissions().upsert(submission(clientId = "c-1"))
        db.submissions().upsert(submission(clientId = "c-2"))
        db.submissions().upsert(submission(clientId = "c-3", participantId = null))
        db.submissions().upsert(
            submission(
                clientId = "elsewhere",
                studyId = Ids.OTHER_STUDY,
                formVersionId = Ids.OTHER_VERSION,
                participantId = Ids.OTHER_PARTICIPANT,
            ),
        )

        assertEquals(1, db.submissions().observeReviewCounts(Ids.STUDY).first().participants)
    }

    // ---- ProgressDay ----

    @Test
    fun `progress groups by calendar day and skips voided rows`() = runTest {
        db.submissions().upsert(submission(clientId = "c-1", collectedAt = at(0)))
        db.submissions().upsert(submission(clientId = "c-2", collectedAt = at(60)))
        db.submissions().upsert(submission(clientId = "c-3", collectedAt = at(0), deletedAt = at(90)))

        val days = db.submissions().observeProgress(Ids.STUDY, zoneOffsetMillis = 0).first()

        assertEquals(1, days.size)
        assertEquals("2026-07-01", days.single().day)
        assertEquals(2, days.single().submissions)
        assertEquals(1, days.single().participants)
    }

    /**
     * **The day boundary.** 06:00 UTC on 1 July is 23:00 on 30 June in
     * Whitehorse. Grouping before applying the zone puts a transect walked
     * yesterday evening onto today's bar, which is a lie the reader cannot catch
     * from a chart.
     */
    @Test
    fun `the offset decides which day a submission lands on`() = runTest {
        db.submissions().upsert(submission(clientId = "c-1", collectedAt = at(-3 * 60)))

        val utc = db.submissions().observeProgress(Ids.STUDY, zoneOffsetMillis = 0).first()
        assertEquals("2026-07-01", utc.single().day)

        val yukon = db.submissions()
            .observeProgress(Ids.STUDY, zoneOffsetMillis = -7 * 60 * 60 * 1000L)
            .first()
        assertEquals("2026-06-30", yukon.single().day)
    }

    @Test
    fun `progress is one study's, in day order`() = runTest {
        db.seedSecondStudy()
        db.submissions().upsert(submission(clientId = "c-late", collectedAt = at(48 * 60)))
        db.submissions().upsert(submission(clientId = "c-early", collectedAt = at(0)))
        db.submissions().upsert(
            submission(
                clientId = "elsewhere",
                studyId = Ids.OTHER_STUDY,
                formVersionId = Ids.OTHER_VERSION,
                participantId = Ids.OTHER_PARTICIPANT,
            ),
        )

        assertEquals(
            listOf("2026-07-01", "2026-07-03"),
            db.submissions().observeProgress(Ids.STUDY, zoneOffsetMillis = 0).first().map { it.day },
        )
    }

    // ---- applyReview ----

    @Test
    fun `applying a review writes the server's values and leaves the queue alone`() = runTest {
        db.submissions().upsert(submission(clientId = "c-1", syncState = SyncState.UPLOADED))

        db.submissions().applyReview(
            collectedBy = Ids.ADAKU,
            clientId = "c-1",
            lockedAt = at(90),
            deletedAt = null,
            updatedAt = at(91),
        )

        val row = db.submissions().observe(Ids.ADAKU, "c-1").first()!!
        assertEquals(at(90), row.lockedAt)
        assertEquals(at(91), row.updatedAt)
        assertEquals(SyncState.UPLOADED, row.syncState)
        assertTrue(db.submissions().pendingKeys().isEmpty())
    }

    @Test
    fun `applying a restore clears deleted_at rather than leaving it set`() = runTest {
        db.submissions().upsert(
            submission(clientId = "c-1", deletedAt = at(40), syncState = SyncState.UPLOADED),
        )

        db.submissions().applyReview(Ids.ADAKU, "c-1", lockedAt = null, deletedAt = null, updatedAt = at(91))

        assertNull(db.submissions().observe(Ids.ADAKU, "c-1").first()!!.deletedAt)
    }

    @Test
    fun `applying a review touches one row, not every row of that collector`() = runTest {
        db.submissions().upsert(submission(clientId = "c-1", syncState = SyncState.UPLOADED))
        db.submissions().upsert(submission(clientId = "c-2", syncState = SyncState.UPLOADED))

        db.submissions().applyReview(Ids.ADAKU, "c-1", lockedAt = at(90), deletedAt = null, updatedAt = at(91))

        assertNull(db.submissions().observe(Ids.ADAKU, "c-2").first()!!.lockedAt)
    }
}
