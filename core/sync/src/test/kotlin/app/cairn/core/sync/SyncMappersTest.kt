package app.cairn.core.sync

import app.cairn.core.model.StudyRole
import app.cairn.core.model.SyncState
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

class SyncMappersTest {

    @Test
    fun `a postgres timestamp with microseconds and an offset parses`() {
        val parsed = "2026-07-01T09:30:00.123456+00:00".toStoredInstant()

        assertEquals(Instant.parse("2026-07-01T09:30:00.123456Z"), parsed)
    }

    /**
     * The documented cost of epoch-millis columns, asserted rather than assumed.
     *
     * Room stores milliseconds so rows sort numerically, and the three digits
     * below that are lost. Nothing depends on them: the cursor keeps the server's
     * exact string and is never rebuilt from an [Instant].
     */
    @Test
    fun `storing a timestamp truncates to milliseconds, and the cursor is why that is safe`() {
        val stored = "2026-07-01T09:30:00.123456+00:00".toStoredInstant()

        assertEquals(1782898200123L, stored.toEpochMilliseconds())
    }

    @Test
    fun `a non-offset zulu timestamp parses too`() {
        assertEquals(
            Instant.parse("2026-07-01T09:30:00Z"),
            "2026-07-01T09:30:00Z".toStoredInstant(),
        )
    }

    @Test
    fun `an unreadable timestamp names itself in the failure`() {
        val failure = assertThrows(SyncException.Malformed::class.java) {
            "the day before yesterday".toStoredInstant()
        }

        assertTrue(failure.message!!.contains("the day before yesterday"))
    }

    @Test
    fun `a nullable timestamp maps to null rather than to the epoch`() {
        assertNull(null.toStoredInstantOrNull())
    }

    @Test
    fun `a pulled submission lands uploaded with no pending marker`() {
        val entity = submissionDto(updatedAt = ts(9)).toEntity()

        assertEquals(SyncState.UPLOADED, entity.syncState)
        assertNull(entity.pendingSince)
        assertEquals("server-existing", entity.id)
    }

    @Test
    fun `a pulled submission keeps its tombstone`() {
        val entity = submissionDto(deletedAt = ts(9)).toEntity()

        assertTrue(entity.isVoided)
    }

    @Test
    fun `a submission with no updated_at is refused rather than given the epoch`() {
        val failure = assertThrows(SyncException.Malformed::class.java) {
            submissionDto(updatedAt = null).toEntity()
        }

        assertTrue(failure.message!!.contains("updated_at"))
    }

    /**
     * The server owns `updated_at`, and it is what last-write-wins compares. A
     * device that could write it could win every conflict for as long as its
     * clock was wrong, so the mapping upward does not carry it at all.
     */
    @Test
    fun `a queued row going up carries no updated_at and no queue state`() {
        val dto = queued().toDto()

        assertNull(dto.updatedAt)
        assertEquals("cccccccc-0000-0000-0000-000000000001", dto.clientId)
        assertNull(dto.id)
    }

    /**
     * A schema this build cannot decode still has to reach the disk. Decoding
     * here would stall the pull for the whole study over one form.
     */
    @Test
    fun `an unrecognised field type survives the mapping verbatim`() {
        val future = buildJsonObject {
            put(
                "fields",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("key", "nest_location")
                            put("type", "gps")
                        },
                    )
                },
            )
        }

        val entity = versionDto(schema = future).toEntity()

        assertEquals(future, entity.schema)
    }

    @Test
    fun `a role round-trips through its wire spelling`() {
        val entity = memberDto(role = StudyRole.PI).toEntity()

        assertEquals(StudyRole.PI, entity.role)
    }

    @Test
    fun `a draft form version keeps a null published_at`() {
        assertNull(versionDto(publishedAt = null).toEntity().publishedAt)
    }

    @Test
    fun `translation labels are carried across untouched`() {
        val entity = translationDto().toEntity()

        assertEquals(JsonPrimitive("Masse corporelle"), entity.labels["body_mass"])
        assertEquals(Instant.parse("2026-07-01T09:04:00Z"), entity.reviewedAt)
    }
}
