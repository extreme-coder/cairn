package app.cairn.core.database

import app.cairn.core.model.StudyRole
import app.cairn.core.model.SyncState
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Instant

class CairnConvertersTest {

    @Test
    fun `roles are stored in the server's spelling`() {
        assertEquals("pi", CairnConverters.roleToText(StudyRole.PI))
        assertEquals("coordinator", CairnConverters.roleToText(StudyRole.COORDINATOR))
        assertEquals("collector", CairnConverters.roleToText(StudyRole.COLLECTOR))
        assertEquals("viewer", CairnConverters.roleToText(StudyRole.VIEWER))
    }

    @Test
    fun `every role survives a round trip`() {
        StudyRole.entries.forEach { role ->
            assertEquals(role, CairnConverters.textToRole(CairnConverters.roleToText(role)))
        }
    }

    @Test
    fun `sync state is local, so it keeps its Kotlin spelling`() {
        assertEquals("QUEUED", CairnConverters.syncStateToText(SyncState.QUEUED))
        assertEquals(SyncState.FAILED, CairnConverters.textToSyncState("FAILED"))
    }

    @Test
    fun `timestamps round trip through epoch milliseconds`() {
        val instant = Instant.parse("2026-07-01T09:30:15.250Z")
        assertEquals(instant, CairnConverters.millisToInstant(CairnConverters.instantToMillis(instant)))
    }

    @Test
    fun `millisecond ordering matches chronological ordering`() {
        val early = CairnConverters.instantToMillis(Instant.parse("2026-07-01T09:00:00Z"))!!
        val late = CairnConverters.instantToMillis(Instant.parse("2026-07-01T09:00:00.500Z"))!!
        assert(early < late) { "$early should sort before $late" }
    }

    @Test
    fun `nulls stay null`() {
        assertNull(CairnConverters.instantToMillis(null))
        assertNull(CairnConverters.textToRole(null))
        assertNull(CairnConverters.jsonToText(null))
    }

    @Test
    fun `json round trips without reordering keys`() {
        val payload = buildJsonObject {
            put("body_mass", 268.0)
            put("ring_number", "AX-4471")
        }
        assertEquals(payload, CairnConverters.textToJson(CairnConverters.jsonToText(payload)))
    }
}
