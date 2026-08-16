package app.cairn.core.model

import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * The phrasings more than one feature depends on.
 *
 * `:feature:collect` has its own suite over the abbreviated forms, from when
 * these lived there. What is pinned here is the public surface `:feature:review`
 * reads — the two forms it added, and the reading of a submission's two
 * timestamps as one state.
 */
class LabelsTest {

    private val utc = ZoneId.of("UTC")
    private val yukon = ZoneId.of("America/Whitehorse")

    private val at = Instant.parse("2026-08-13T09:14:00Z")

    /**
     * The list forms abbreviate because the reader is comparing rows against
     * each other. Someone deciding whether to lock one submission is comparing
     * it against a field notebook, and "Yesterday 08:52" does not survive that.
     */
    @Test
    fun `a single submission carries its full date, not a relative one`() {
        assertEquals("13 Aug 2026 · 09:14", collectedFullLabel(at, utc))
    }

    @Test
    fun `the full date is in the reader's zone, not the server's`() {
        assertEquals("13 Aug 2026 · 02:14", collectedFullLabel(at, yukon))
    }

    /** No year: the caption above the chart already says the period. */
    @Test
    fun `an axis day is short enough to fit under a bar`() {
        assertEquals("13 Aug", axisDayLabel(LocalDate.of(2026, 8, 13)))
        assertEquals("1 Sep", axisDayLabel(LocalDate.of(2026, 9, 1)))
    }

    @Test
    fun `the three review states are three words`() {
        assertEquals(listOf("Open", "Locked", "Voided"), ReviewState.entries.map { it.label })
    }

    /**
     * A submission can be both. Voiding does not lock, so a voided row is still
     * unlocked and can then be locked — and the fact that matters to anyone
     * reading the study is that the observation is out of the analysis.
     */
    @Test
    fun `voided wins over locked when a row is both`() {
        assertEquals(ReviewState.OPEN, reviewStateOf(lockedAt = null, deletedAt = null))
        assertEquals(ReviewState.LOCKED, reviewStateOf(lockedAt = at, deletedAt = null))
        assertEquals(ReviewState.VOIDED, reviewStateOf(lockedAt = null, deletedAt = at))
        assertEquals(ReviewState.VOIDED, reviewStateOf(lockedAt = at, deletedAt = at))
    }
}
