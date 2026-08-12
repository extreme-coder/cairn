package app.cairn.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The page-boundary rule, on its own.
 *
 * These are the cheapest tests in the project and they guard the most expensive
 * failure: rows that are fetched once, never applied, and never offered again.
 * Nothing about that failure is visible at the time — the sync reports success
 * and the data is simply not there later.
 */
class PageCutTest {

    private data class Row(val name: String, val at: String)

    private fun cut(rows: List<Row>, pageSize: Int) =
        cutPage(rows, pageSize, "forms") { it.at }

    private fun rows(vararg at: String) = at.mapIndexed { i, t -> Row("row$i", t) }

    @Test
    fun `an empty page leaves the cursor alone and stops`() {
        val cut = cut(emptyList(), pageSize = 3)

        assertEquals(emptyList<Row>(), cut.keep)
        assertEquals(null, cut.cursor)
        assertFalse(cut.more)
    }

    @Test
    fun `a short page is complete, so all of it is kept and paging stops`() {
        val cut = cut(rows(ts(1), ts(2)), pageSize = 3)

        assertEquals(listOf("row0", "row1"), cut.keep.map { it.name })
        assertEquals(ts(2), cut.cursor)
        assertFalse(cut.more)
    }

    /**
     * Even with every timestamp distinct, the last row of a full page is dropped.
     *
     * A full page means the server had at least this many rows to give, so there
     * may be more sharing the last row's timestamp just past the cut. There is no
     * way to tell from here, and guessing wrong loses them.
     */
    @Test
    fun `the last row of a full page is held back because more may share its timestamp`() {
        val cut = cut(rows(ts(1), ts(2), ts(3)), pageSize = 3)

        assertEquals(listOf("row0", "row1"), cut.keep.map { it.name })
        assertEquals(ts(2), cut.cursor)
        assertTrue(cut.more)
    }

    @Test
    fun `a trailing tie group is dropped whole and the cursor stops before it`() {
        val cut = cut(rows(ts(1), ts(2), ts(2)), pageSize = 3)

        assertEquals(listOf("row0"), cut.keep.map { it.name })
        assertEquals(ts(1), cut.cursor)
        assertTrue(cut.more)
    }

    /**
     * `private.touch_updated_at()` uses `now()`, which is transaction start time,
     * so one statement touching a page's worth of rows gives them all the same
     * value. There is no complete earlier timestamp to fall back to and no way to
     * ask for "the rest of the rows at this one", so this stops loudly.
     */
    @Test
    fun `a full page sharing one timestamp stalls rather than skipping rows`() {
        val stalled = assertThrows(SyncException.CursorStalled::class.java) {
            cut(rows(ts(7), ts(7), ts(7)), pageSize = 3)
        }

        assertTrue(stalled.message!!.contains("forms"))
        assertTrue(stalled.message!!.contains(ts(7)))
    }

    /** The tie is on the exact microsecond, so neighbouring values are not a tie. */
    @Test
    fun `microsecond differences are not a tie`() {
        val cut = cut(rows(ts(7, 1), ts(7, 2), ts(7, 3)), pageSize = 3)

        assertEquals(listOf("row0", "row1"), cut.keep.map { it.name })
        assertEquals(ts(7, 2), cut.cursor)
    }

    /** Dropping the tail must never drop everything: a kept page always advances the cursor. */
    @Test
    fun `whatever is kept ends at the cursor that is persisted`() {
        val cut = cut(rows(ts(1), ts(2), ts(3), ts(3), ts(3)), pageSize = 5)

        assertEquals(ts(2), cut.cursor)
        assertEquals(cut.cursor, cut.keep.last().at)
    }
}
