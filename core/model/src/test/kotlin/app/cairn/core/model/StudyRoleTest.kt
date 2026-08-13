package app.cairn.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StudyRoleTest {

    /**
     * The voice guide fixes these four words. A screen that spelled one of them
     * differently would be a second vocabulary for the same concept, which is
     * exactly what the lexicon exists to prevent.
     */
    @Test
    fun `every role is written the way the voice guide writes it`() {
        assertEquals("PI", StudyRole.PI.label)
        assertEquals("Coordinator", StudyRole.COORDINATOR.label)
        assertEquals("Collector", StudyRole.COLLECTOR.label)
        assertEquals("Viewer", StudyRole.VIEWER.label)
    }

    @Test
    fun `a viewer is offered no way to collect`() {
        assertFalse(StudyRole.VIEWER.showsCollectAction)
        assertTrue(StudyRole.COLLECTOR.showsCollectAction)
        assertTrue(StudyRole.COORDINATOR.showsCollectAction)
        assertTrue(StudyRole.PI.showsCollectAction)
    }

    @Test
    fun `only a collector is limited to their own submissions`() {
        assertFalse(StudyRole.COLLECTOR.showsAllSubmissions)
        assertTrue(StudyRole.VIEWER.showsAllSubmissions)
    }
}
