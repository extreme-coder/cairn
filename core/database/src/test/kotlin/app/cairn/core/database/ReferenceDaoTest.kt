package app.cairn.core.database

import app.cairn.core.model.StudyRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReferenceDaoTest {

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

    /**
     * Pins a sharp edge rather than a guarantee.
     *
     * `@Upsert` inserts, and on any constraint conflict updates *by primary key*.
     * A second form with a new id but a taken `(study_id, code)` therefore
     * matches no row to update and is dropped without raising. No duplicate
     * appears, which is right, but nothing announces the loss either — so
     * `:core:sync` must apply a reference-data delta as one transaction rather
     * than assume every row it upserted landed.
     */
    @Test
    fun `a form code conflicting on (study_id, code) is dropped, not duplicated`() = runTest {
        db.forms().upsertForms(
            listOf(form(id = "22222222-0000-0000-0000-000000000002", code = "baseline_intake")),
        )

        val forms = db.forms().observeForms(Ids.STUDY).first()
        assertEquals(listOf(Ids.FORM), forms.map { it.id })
    }

    @Test
    fun `the current version is the newest published one, never a draft`() = runTest {
        db.forms().upsertVersions(
            listOf(formVersion(id = Ids.VERSION_2, version = 2, publishedAt = null)),
        )

        val current = db.forms().observeCurrentVersion(Ids.FORM).first()
        assertEquals(1, current?.version)
    }

    @Test
    fun `drafts stay out of the version list`() = runTest {
        db.forms().upsertVersions(
            listOf(formVersion(id = Ids.VERSION_2, version = 2, publishedAt = null)),
        )

        val published = db.forms().observePublishedVersions(Ids.FORM).first()
        assertEquals(listOf(1), published.map { it.version })
    }

    @Test
    fun `a pinned version is readable by id after a newer one is published`() = runTest {
        db.forms().upsertVersions(
            listOf(formVersion(id = Ids.VERSION_2, version = 2, publishedAt = at(200))),
        )

        assertEquals(1, db.forms().version(Ids.VERSION_1)?.version)
    }

    @Test
    fun `a role is stored in the spelling the server uses`() = runTest {
        db.members().upsert(listOf(member(role = StudyRole.COORDINATOR)))

        assertEquals(StudyRole.COORDINATOR, db.members().observeRole(Ids.STUDY, Ids.ADAKU).first())
        assertNull(db.members().observeRole(Ids.STUDY, Ids.TOMAS).first())
    }

    @Test
    fun `a study is observable by id`() = runTest {
        assertEquals("Kestrel breeding survey", db.studies().observe(Ids.STUDY).first()?.name)
    }

    @Test
    fun `a study the device has not pulled observes as null rather than failing`() = runTest {
        assertNull(db.studies().observe(Ids.OTHER_STUDY).first())
    }

    @Test
    fun `the roster holds every member of the study`() = runTest {
        db.members().upsert(
            listOf(
                member(userId = Ids.ADAKU, role = StudyRole.PI),
                member(userId = Ids.TOMAS, role = StudyRole.COLLECTOR),
                member(userId = Ids.NOOR, role = StudyRole.VIEWER),
            ),
        )

        val roster = db.members().observeRoster(Ids.STUDY).first()
        assertEquals(
            mapOf(
                Ids.ADAKU to StudyRole.PI,
                Ids.TOMAS to StudyRole.COLLECTOR,
                Ids.NOOR to StudyRole.VIEWER,
            ),
            roster.associate { it.userId to it.role },
        )
    }

    @Test
    fun `the roster does not leak the members of another study`() = runTest {
        db.seedSecondStudy()
        db.members().upsert(
            listOf(
                member(studyId = Ids.STUDY, userId = Ids.ADAKU),
                member(studyId = Ids.OTHER_STUDY, userId = Ids.TOMAS),
            ),
        )

        assertEquals(
            listOf(Ids.ADAKU),
            db.members().observeRoster(Ids.STUDY).first().map { it.userId },
        )
    }

    @Test
    fun `one person can hold different roles in two studies`() = runTest {
        db.seedSecondStudy()
        db.members().upsert(
            listOf(
                member(studyId = Ids.STUDY, userId = Ids.ADAKU, role = StudyRole.COLLECTOR),
                member(studyId = Ids.OTHER_STUDY, userId = Ids.ADAKU, role = StudyRole.PI),
            ),
        )

        assertEquals(StudyRole.COLLECTOR, db.members().observeRole(Ids.STUDY, Ids.ADAKU).first())
        assertEquals(StudyRole.PI, db.members().observeRole(Ids.OTHER_STUDY, Ids.ADAKU).first())
    }

    @Test
    fun `participants are listed in code order`() = runTest {
        db.participants().upsert(
            listOf(
                participant(id = "44444444-0000-0000-0000-000000000003", code = "K-102"),
                participant(id = "44444444-0000-0000-0000-000000000002", code = "K-007"),
            ),
        )

        assertEquals(
            listOf("K-007", "K-014", "K-102"),
            db.participants().observeAll(Ids.STUDY).first().map { it.code },
        )
    }

    @Test
    fun `participants are scoped to their study`() = runTest {
        db.seedSecondStudy()

        assertEquals(
            listOf("K-014"),
            db.participants().observeAll(Ids.STUDY).first().map { it.code },
        )
        assertEquals(
            listOf("P-001"),
            db.participants().observeAll(Ids.OTHER_STUDY).first().map { it.code },
        )
    }

    @Test
    fun `forms are scoped to their study even when two studies use the same code`() = runTest {
        db.seedSecondStudy()

        assertEquals(listOf(Ids.FORM), db.forms().observeForms(Ids.STUDY).first().map { it.id })
        assertEquals(
            listOf(Ids.OTHER_FORM),
            db.forms().observeForms(Ids.OTHER_STUDY).first().map { it.id },
        )
    }

    @Test
    fun `an unreviewed translation never reaches a screen`() = runTest {
        db.translations().upsert(listOf(translation(lang = "fr", reviewedAt = null)))

        assertNull(db.translations().observeReviewed(Ids.VERSION_1, "fr").first())
        assertEquals(1, db.translations().observeAll(Ids.VERSION_1).first().size)
    }

    @Test
    fun `a reviewed translation does`() = runTest {
        db.translations().upsert(
            listOf(translation(lang = "fr", reviewedAt = at(300), reviewedBy = Ids.TOMAS)),
        )

        val reviewed = db.translations().observeReviewed(Ids.VERSION_1, "fr").first()
        assertEquals("fr", reviewed?.lang)
    }

    @Test
    fun `a participant carries a study code and nothing that identifies a person`() = runTest {
        val found = db.participants().byCode(Ids.STUDY, "K-014")
        assertEquals(Ids.PARTICIPANT, found?.id)
    }

    @Test
    fun `deleting a study takes its forms with it`() = runTest {
        db.studies().delete(listOf(Ids.STUDY))

        assertEquals(0, db.forms().observeForms(Ids.STUDY).first().size)
        assertNull(db.forms().version(Ids.VERSION_1))
    }

    @Test
    fun `a schema holding a field type this build does not know still stores`() = runTest {
        val futureSchema = buildJsonObject {
            put(
                "fields",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("key", "nest_location")
                            put("type", "gps")
                            put("label", "Nest location")
                        },
                    )
                },
            )
        }
        db.forms().upsertVersions(
            listOf(formVersion(id = Ids.VERSION_2, version = 2, schema = futureSchema)),
        )

        val stored = db.forms().version(Ids.VERSION_2)
        assertEquals(futureSchema, stored?.schema)
        assertThrows(SerializationException::class.java) { stored!!.schema.toFormSchema() }
    }

    @Test
    fun `a known schema decodes to the model type`() = runTest {
        val schema = db.forms().version(Ids.VERSION_1)!!.schema.toFormSchema()
        assertEquals("body_mass", schema.fields.single().key)
        assertEquals(90.0, schema.field("body_mass")?.min)
    }
}
