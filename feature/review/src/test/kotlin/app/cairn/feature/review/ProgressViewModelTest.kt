package app.cairn.feature.review

import app.cairn.core.database.CairnDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneId
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * The device-side reading of `v_study_progress`.
 *
 * A view cannot be pulled, and does not need to be: a coordinator's pull already
 * brings every submission in the study down. What has to be right is the day a
 * row lands on and the days with nothing on them.
 */
@RunWith(RobolectricTestRunner::class)
class ProgressViewModelTest {

    private lateinit var db: CairnDatabase

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = testDatabase()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun model(zone: ZoneId = UTC, window: Int = 14) = ProgressViewModel(
        studies = db.studies(),
        submissions = db.submissions(),
        studyId = Ids.KLUANE,
        now = { T0 },
        zone = zone,
        window = window,
    )

    private suspend fun TestScope.loaded(
        model: ProgressViewModel = model(),
    ): ProgressUiState = watching(model.uiState).awaiting { it.loaded }

    @Test
    fun `the window is the whole window, including the days nothing happened on`() = runTest {
        db.seedKluane()
        db.submissions().upsert(submission("c-1", collectedAt = T0))
        db.submissions().upsert(submission("c-2", collectedAt = T0 - 3.days, id = "server-2"))

        val bars = loaded().bars

        assertEquals(14, bars.size)
        assertEquals("2026-08-13", bars.last().day)
        assertEquals("2026-07-31", bars.first().day)
        assertEquals(1, bars.last().count)
        assertEquals(1, bars.single { it.day == "2026-08-10" }.count)
        // Eleven days with nothing on them, all still drawn.
        assertEquals(12, bars.count { it.count == 0 })
    }

    @Test
    fun `two submissions on one day are one bar of two`() = runTest {
        db.seedKluane()
        db.submissions().upsert(submission("c-1", collectedAt = T0))
        db.submissions().upsert(submission("c-2", collectedAt = T0 + 2.hours, id = "server-2"))

        assertEquals(2, loaded().bars.last().count)
    }

    /**
     * The view filters `deleted_at is null` and so does this. A voided
     * observation is out of the analysis, and a chart that still counted it
     * would be the one place the void did not take effect.
     */
    @Test
    fun `a voided submission is not on the chart`() = runTest {
        db.seedKluane()
        db.submissions().upsert(submission("c-1", collectedAt = T0))
        db.submissions().upsert(submission("c-2", collectedAt = T0, deletedAt = T0, id = "server-2"))

        assertEquals(1, loaded().bars.last().count)
        assertEquals(1, loaded().counts.voided)
    }

    /**
     * **The day boundary.** 18:00 in Whitehorse on the 12th is 01:00 UTC on the
     * 13th. Grouping before applying the zone puts a transect walked yesterday
     * evening onto today's bar — a lie the reader has no way to catch from a
     * chart.
     */
    @Test
    fun `the day a submission lands on is a calendar day in the reader's zone`() = runTest {
        db.seedKluane()
        val evening = kotlin.time.Instant.parse("2026-08-13T01:00:00Z")
        db.submissions().upsert(submission("c-1", collectedAt = evening))

        val utc = loaded(model(zone = UTC))
        assertEquals(1, utc.bars.single { it.day == "2026-08-13" }.count)

        val yukon = loaded(model(zone = YUKON))
        assertEquals(1, yukon.bars.single { it.day == "2026-08-12" }.count)
        assertEquals(0, yukon.bars.single { it.day == "2026-08-13" }.count)
    }

    @Test
    fun `the chart is this study's, not the device's`() = runTest {
        db.seedKluane()
        db.seedPeel()
        db.submissions().upsert(submission("c-1", collectedAt = T0))
        db.submissions().upsert(
            submission(
                "c-2",
                collectedAt = T0,
                studyId = Ids.PEEL,
                formVersionId = Ids.PEEL_V1,
                participantId = null,
                id = "server-2",
            ),
        )

        assertEquals(1, loaded().bars.last().count)
    }

    @Test
    fun `a submission older than the window is counted in the totals but not drawn`() = runTest {
        db.seedKluane()
        db.submissions().upsert(submission("c-old", collectedAt = T0 - 30.days))

        val state = loaded()

        assertTrue(state.bars.all { it.count == 0 })
        assertEquals(1, state.counts.collected)
        assertFalse(state.isEmpty)
    }

    @Test
    fun `an empty study says so rather than drawing an empty chart`() = runTest {
        db.seedKluane()

        val state = loaded()

        assertTrue(state.isEmpty)
        assertEquals("Kluane ground squirrel survey", state.studyName)
    }

    /**
     * Four zeroes and "not asked yet" are otherwise indistinguishable, and the
     * difference is an empty state shown for a frame to someone with a season's
     * work behind them.
     */
    @Test
    fun `nothing is empty before the database has answered`() = runTest {
        assertFalse(ProgressUiState().isEmpty)
    }

    @Test
    fun `the axis labels a day without repeating the year the caption implies`() = runTest {
        db.seedKluane()
        db.submissions().upsert(submission("c-1", collectedAt = T0))

        assertEquals("13 Aug", loaded().bars.last().axisLabel)
    }

    @Test
    fun `the participant line is honest about there being none`() = runTest {
        db.seedKluane()
        db.submissions().upsert(submission("c-1", participantId = null, collectedAt = T0))

        assertEquals("No participants recorded · 1 still open", participantLine(loaded()))
    }

    @Test
    fun `the participant line counts each participant once`() = runTest {
        db.seedKluane()
        db.submissions().upsert(submission("c-1", collectedAt = T0))
        db.submissions().upsert(submission("c-2", collectedAt = T0, lockedAt = T0, id = "server-2"))

        assertEquals("1 participant · 1 still open", participantLine(loaded()))
    }

    @Test
    fun `the caption names the window it is actually drawing`() = runTest {
        db.seedKluane()
        db.submissions().upsert(submission("c-1", collectedAt = T0))

        val week = loaded(model(window = 7))

        assertEquals(7, week.bars.size)
        assertEquals("Submissions per day · last 7 days", week.caption)
    }
}
