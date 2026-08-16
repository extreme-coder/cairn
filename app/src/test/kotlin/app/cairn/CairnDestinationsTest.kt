package app.cairn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The routing table, on its own.
 *
 * Routes are strings, so the thing that breaks silently is a path built in one
 * place and parsed in another. These are cheap and they are the reason
 * [CairnDestinations] is the only file that spells a route.
 */
class CairnDestinationsTest {

    @Test
    fun `a built route matches the pattern it will be parsed by`() {
        assertEquals("study/abc", CairnDestinations.study("abc"))
        assertEquals("capture/abc/def", CairnDestinations.capture("abc", "def"))
        assertEquals("submissions/abc", CairnDestinations.submissions("abc"))
        assertEquals("progress/abc", CairnDestinations.progress("abc"))
        assertEquals("submission/abc/def/ghi", CairnDestinations.submission("abc", "def", "ghi"))
    }

    @Test
    fun `the patterns name the arguments the routes carry`() {
        assertTrue(CairnDestinations.STUDY_PATTERN.contains("{${CairnDestinations.ARG_STUDY}}"))
        assertTrue(CairnDestinations.CAPTURE_PATTERN.contains("{${CairnDestinations.ARG_STUDY}}"))
        assertTrue(CairnDestinations.CAPTURE_PATTERN.contains("{${CairnDestinations.ARG_FORM}}"))
        assertTrue(CairnDestinations.SUBMISSIONS_PATTERN.contains("{${CairnDestinations.ARG_STUDY}}"))
        assertTrue(CairnDestinations.PROGRESS_PATTERN.contains("{${CairnDestinations.ARG_STUDY}}"))
        assertTrue(CairnDestinations.SUBMISSION_PATTERN.contains("{${CairnDestinations.ARG_STUDY}}"))
        assertTrue(
            CairnDestinations.SUBMISSION_PATTERN.contains("{${CairnDestinations.ARG_COLLECTED_BY}}"),
        )
        assertTrue(CairnDestinations.SUBMISSION_PATTERN.contains("{${CairnDestinations.ARG_CLIENT}}"))
    }

    /**
     * Every route builder produces a path with the same number of segments as
     * the pattern that parses it. A route built in one place and parsed in
     * another is exactly what breaks silently.
     */
    @Test
    fun `every built route has the shape of its pattern`() {
        listOf(
            CairnDestinations.study("a") to CairnDestinations.STUDY_PATTERN,
            CairnDestinations.capture("a", "b") to CairnDestinations.CAPTURE_PATTERN,
            CairnDestinations.submissions("a") to CairnDestinations.SUBMISSIONS_PATTERN,
            CairnDestinations.progress("a") to CairnDestinations.PROGRESS_PATTERN,
            CairnDestinations.submission("a", "b", "c") to CairnDestinations.SUBMISSION_PATTERN,
        ).forEach { (built, pattern) ->
            assertEquals(pattern, pattern.split("/").size, built.split("/").size)
            assertEquals(pattern, pattern.substringBefore("/"), built.substringBefore("/"))
        }
    }

    /**
     * Walking into a study must not leave the bar looking as though nothing is
     * selected: every route inside the Collect stack lights Collect.
     */
    @Test
    fun `every route in the collect stack lights the collect tab`() {
        assertEquals(0, CairnDestinations.tabOf(CairnDestinations.STUDIES))
        assertEquals(0, CairnDestinations.tabOf(CairnDestinations.STUDY_PATTERN))
        assertEquals(0, CairnDestinations.tabOf(CairnDestinations.CAPTURE_PATTERN))
        assertEquals(0, CairnDestinations.tabOf(CairnDestinations.COLLECT_GRAPH))
        assertEquals(0, CairnDestinations.tabOf(CairnDestinations.SUBMISSIONS_PATTERN))
        assertEquals(0, CairnDestinations.tabOf(CairnDestinations.PROGRESS_PATTERN))
        assertEquals(0, CairnDestinations.tabOf(CairnDestinations.SUBMISSION_PATTERN))
    }

    @Test
    fun `the sibling destinations light their own tabs`() {
        assertEquals(1, CairnDestinations.tabOf(CairnDestinations.QUEUE))
        assertEquals(2, CairnDestinations.tabOf(CairnDestinations.SETTINGS))
    }

    /** The bar always has exactly one selection; "none" is not a state it can draw. */
    @Test
    fun `an unknown route still selects something`() {
        assertEquals(0, CairnDestinations.tabOf(null))
        assertEquals(0, CairnDestinations.tabOf("something/else"))
    }

    /**
     * Capture takes the whole window: it carries its own primary action, and
     * someone filling in a form should not be one mis-tap from losing the
     * `client_id` they are working under. The review screens keep the bar —
     * their destructive action is behind a confirmation, so a mis-tap costs a
     * dismissed dialog rather than an observation.
     */
    @Test
    fun `capture hides the bottom bar and nothing else does`() {
        assertFalse(CairnDestinations.showsBottomBar(CairnDestinations.CAPTURE_PATTERN))
        assertTrue(CairnDestinations.showsBottomBar(CairnDestinations.STUDIES))
        assertTrue(CairnDestinations.showsBottomBar(CairnDestinations.STUDY_PATTERN))
        assertTrue(CairnDestinations.showsBottomBar(CairnDestinations.QUEUE))
        assertTrue(CairnDestinations.showsBottomBar(CairnDestinations.SETTINGS))
        assertTrue(CairnDestinations.showsBottomBar(CairnDestinations.SUBMISSIONS_PATTERN))
        assertTrue(CairnDestinations.showsBottomBar(CairnDestinations.PROGRESS_PATTERN))
        assertTrue(CairnDestinations.showsBottomBar(CairnDestinations.SUBMISSION_PATTERN))
    }
}
