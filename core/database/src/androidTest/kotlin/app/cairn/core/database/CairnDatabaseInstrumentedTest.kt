package app.cairn.core.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cairn.core.database.entity.FormEntity
import app.cairn.core.database.entity.FormVersionEntity
import app.cairn.core.database.entity.ParticipantEntity
import app.cairn.core.database.entity.StudyEntity
import app.cairn.core.database.entity.StudyMemberEntity
import app.cairn.core.database.entity.SubmissionEntity
import app.cairn.core.model.StudyRole
import app.cairn.core.model.SyncState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The database on a real device, against the SQLite that actually ships.
 *
 * The JVM suite runs Room over `BundledSQLiteDriver`, which is deliberate and
 * fast — but it is not the engine on the phone. Everything here is a query
 * whose result could differ between the two: correlated subqueries, a left join
 * that has to keep its left side, `coalesce` over an empty aggregate, and the
 * `!= 'UPLOADED'` comparison that decides whether a collector's morning is
 * safe. If those agree here and on the JVM, the JVM suite can be trusted for
 * the rest.
 */
@RunWith(AndroidJUnit4::class)
class CairnDatabaseInstrumentedTest {

    /**
     * Validates the exported schema against what Room creates on-device.
     *
     * There is no version 2 yet, so there is no migration to run. What this
     * still proves is that `schemas/1.json` — the file every future migration
     * test will be checked against — describes the database this build actually
     * creates on a device.
     */
    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        databaseClass = CairnDatabase::class.java,
    )

    private lateinit var db: CairnDatabase

    @Before
    fun setUp() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, CairnDatabase::class.java).build()
        seed()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun theExportedSchemaMatchesWhatRoomCreatesOnADevice() {
        helper.createDatabase(TEST_DB, 1).close()
        helper.runMigrationsAndValidate(TEST_DB, 1, true).close()
    }

    @Test
    fun aStudyWithNoFormsAndNoSubmissionsStillAppears() = runTest {
        db.studies().upsert(listOf(StudyEntity(PEEL, "Peel watershed water quality", TOMAS, T0)))

        val bare = db.studies().observeSummaries(ADAKU).first().single { it.id == PEEL }

        assertEquals(0, bare.formCount)
        assertEquals(0, bare.submissionCount)
        assertNull(bare.role)
    }

    @Test
    fun studyCountsAreScopedAndExcludeVoidedRows() = runTest {
        db.submissions().upsert(submission("c-1"))
        db.submissions().upsert(submission("c-2", syncState = SyncState.UPLOADED))
        db.submissions().upsert(submission("c-3", deletedAt = at(40)))

        val row = db.studies().observeSummaries(ADAKU).first().single { it.id == KLUANE }

        assertEquals(2, row.submissionCount)
        assertEquals(1, row.pendingCount)
        assertEquals(StudyRole.COLLECTOR, row.role)
    }

    @Test
    fun aFormWithNoPublishedVersionKeepsItsRow() = runTest {
        val rows = db.forms().observeFormSummaries(KLUANE).first()

        val baseline = rows.single { it.id == BASELINE }
        assertEquals(3, baseline.version)
        assertEquals(1, baseline.schema?.toFormSchema()?.fields?.size)

        val trap = rows.single { it.id == TRAP }
        assertNull(trap.version)
        assertNull(trap.schema)
    }

    @Test
    fun anEmptyDeviceCountsThreeZeroes() = runTest {
        val counts = db.submissions().observeCounts(ADAKU).first()

        assertEquals(0, counts.queued)
        assertEquals(0, counts.failed)
        assertEquals(0, counts.uploaded)
    }

    @Test
    fun theQueueHoldsWhatHasNotUploadedAndNamesIt() = runTest {
        db.submissions().upsert(submission("c-1", collectedAt = at(10)))
        db.submissions().upsert(submission("c-2", collectedAt = at(20), syncState = SyncState.FAILED))
        db.submissions().upsert(submission("c-3", collectedAt = at(30), syncState = SyncState.UPLOADED))
        db.submissions().upsert(submission("c-4", collectedBy = TOMAS))

        val rows = db.submissions().observePending(ADAKU).first()

        assertEquals(listOf("c-2", "c-1"), rows.map { it.clientId })
        assertEquals("Kluane ground squirrel survey", rows.first().studyName)
        assertEquals("baseline_intake", rows.first().formCode)
        assertEquals(3, rows.first().version)
        assertEquals("KL-0148", rows.first().participantCode)

        val counts = db.submissions().observeCounts(ADAKU).first()
        assertEquals(1, counts.queued)
        assertEquals(1, counts.failed)
        assertEquals(1, counts.uploaded)
    }

    @Test
    fun aSubmissionWithNoParticipantIsStillInTheQueue() = runTest {
        db.submissions().upsert(submission("c-1", participantId = null))

        val row = db.submissions().observePending(ADAKU).first().single()

        assertNull(row.participantCode)
        assertEquals("c-1", row.clientId)
    }

    @Test
    fun theUploadedListHonoursItsLimit() = runTest {
        repeat(4) { index ->
            db.submissions().upsert(
                submission("up-$index", collectedAt = at(index), syncState = SyncState.UPLOADED),
            )
        }

        val rows = db.submissions().observeUploaded(ADAKU, limit = 2).first()

        assertEquals(listOf("up-3", "up-2"), rows.map { it.clientId })
        assertTrue(rows.all { it.syncState == SyncState.UPLOADED })
    }

    private suspend fun seed() {
        db.studies().upsert(
            listOf(StudyEntity(KLUANE, "Kluane ground squirrel survey", TOMAS, T0)),
        )
        db.members().upsert(listOf(StudyMemberEntity(KLUANE, ADAKU, StudyRole.COLLECTOR, T0)))
        db.forms().upsertForms(
            listOf(
                FormEntity(BASELINE, KLUANE, "baseline_intake", T0),
                FormEntity(TRAP, KLUANE, "trap_check", T0),
            ),
        )
        db.forms().upsertVersions(
            listOf(FormVersionEntity(BASELINE_V3, BASELINE, 3, schema, T0, T0)),
        )
        db.participants().upsert(listOf(ParticipantEntity(KL_0148, KLUANE, "KL-0148", T0)))
    }

    private fun submission(
        clientId: String,
        collectedBy: String = ADAKU,
        participantId: String? = KL_0148,
        collectedAt: Instant = at(14),
        syncState: SyncState = SyncState.QUEUED,
        deletedAt: Instant? = null,
    ) = SubmissionEntity(
        collectedBy = collectedBy,
        clientId = clientId,
        studyId = KLUANE,
        formVersionId = BASELINE_V3,
        participantId = participantId,
        collectedAt = collectedAt,
        data = buildJsonObject { put("body_mass", 268.0) },
        updatedAt = collectedAt,
        deletedAt = deletedAt,
        syncState = syncState,
        pendingSince = collectedAt,
    )

    private companion object {
        const val TEST_DB = "cairn-migration-test.db"

        const val KLUANE = "11111111-1111-1111-1111-111111111111"
        const val PEEL = "11111111-1111-1111-1111-111111111112"
        const val BASELINE = "22222222-2222-2222-2222-222222222221"
        const val TRAP = "22222222-2222-2222-2222-222222222222"
        const val BASELINE_V3 = "33333333-3333-3333-3333-333333333331"
        const val KL_0148 = "44444444-4444-4444-4444-444444444448"
        const val ADAKU = "55555555-5555-5555-5555-555555555551"
        const val TOMAS = "55555555-5555-5555-5555-555555555552"

        val T0: Instant = Instant.parse("2026-08-12T09:00:00Z")

        fun at(minutes: Int): Instant = T0 + minutes.minutes

        val schema = buildJsonObject {
            putJsonArray("fields") {
                addJsonObject {
                    put("key", "body_mass")
                    put("type", "number")
                    put("label", "Body mass")
                }
            }
        }
    }
}
