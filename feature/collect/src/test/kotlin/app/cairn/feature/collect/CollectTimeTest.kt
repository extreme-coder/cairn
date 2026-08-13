package app.cairn.feature.collect

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Pure functions, so no Robolectric and no database.
 *
 * Every case here is a sentence a collector reads, and the two that matter most
 * are the boundaries: a row collected at 23:59 must not render as today's when
 * read at 00:01, and a device that has never synced must say so rather than
 * showing a plausible-looking dash.
 */
class CollectTimeTest {

    private val now = Instant.parse("2026-08-12T09:00:00Z")
    private val vancouver = ZoneId.of("America/Vancouver")

    @Test
    fun `something collected today is a time alone`() {
        assertEquals("08:52", collectedLabel(Instant.parse("2026-08-12T08:52:00Z"), now, UTC))
    }

    @Test
    fun `yesterday is named, because eight fifty-two alone would be a lie`() {
        assertEquals(
            "Yesterday 08:52",
            collectedLabel(Instant.parse("2026-08-11T08:52:00Z"), now, UTC),
        )
    }

    @Test
    fun `anything older carries its day`() {
        assertEquals("3 Aug 08:52", collectedLabel(Instant.parse("2026-08-03T08:52:00Z"), now, UTC))
    }

    /**
     * The boundary. "Today" is a local calendar day, not the last 24 hours, so
     * the zone has to be applied before the comparison rather than after.
     */
    @Test
    fun `today is decided in the collector's own zone`() {
        // 06:00 UTC on the 12th is 23:00 on the 11th in Vancouver.
        val lateLastNight = Instant.parse("2026-08-12T06:00:00Z")

        assertEquals("06:00", collectedLabel(lateLastNight, now, UTC))
        assertEquals("Yesterday 23:00", collectedLabel(lateLastNight, now, vancouver))
    }

    @Test
    fun `a device that has never synced says so`() {
        assertEquals("Not synced on this device yet", lastSyncedLabel(null, now))
    }

    @Test
    fun `elapsed time is quantified, never vague`() {
        assertEquals("Less than a minute ago", lastSyncedLabel(now - 30.seconds, now))
        assertEquals("1 minute ago", lastSyncedLabel(now - 1.minutes, now))
        assertEquals("14 minutes ago", lastSyncedLabel(now - 14.minutes, now))
        assertEquals("1 hour ago", lastSyncedLabel(now - 1.hours, now))
        assertEquals("5 hours ago", lastSyncedLabel(now - 5.hours, now))
        assertEquals("2 days ago", lastSyncedLabel(now - 2.days, now))
    }

    @Test
    fun `the minute and hour boundaries do not fall through`() {
        assertEquals("59 minutes ago", lastSyncedLabel(now - 59.minutes, now))
        assertEquals("1 hour ago", lastSyncedLabel(now - 60.minutes, now))
        assertEquals("23 hours ago", lastSyncedLabel(now - 23.hours, now))
        assertEquals("1 day ago", lastSyncedLabel(now - 24.hours, now))
    }

    /**
     * A participant code is what the collector wrote on the bag. Without one the
     * row still has to be identifiable, because an unlabelled row in a queue of
     * unsent observations is worse than an ugly one.
     */
    @Test
    fun `a row without a participant is labelled by its client id`() {
        assertEquals("KL-0148", submissionLabel("KL-0148", "cccccccc-0000-0000-0000-000000000001"))
        assertEquals("CCCCCCCC", submissionLabel(null, "cccccccc-0000-0000-0000-000000000001"))
    }

    @Test
    fun `a form title is derived from its code until the server has a name column`() {
        assertEquals("Baseline intake", formTitle("baseline_intake"))
        assertEquals("Trap check", formTitle("trap_check"))
        assertEquals("Weekly", formTitle("weekly"))
    }

    @Test
    fun `one of something is singular`() {
        assertEquals("form", plural(1, "form"))
        assertEquals("forms", plural(0, "form"))
        assertEquals("forms", plural(2, "form"))
    }
}
