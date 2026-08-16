package app.cairn

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cairn.core.database.CairnDatabase
import app.cairn.core.database.entity.FormEntity
import app.cairn.core.database.entity.FormVersionEntity
import app.cairn.core.database.entity.ParticipantEntity
import app.cairn.core.database.entity.StudyEntity
import app.cairn.core.database.entity.StudyMemberEntity
import app.cairn.core.database.entity.SubmissionEntity
import app.cairn.core.designsystem.CairnTheme
import app.cairn.core.model.StudyRole
import app.cairn.core.model.SyncState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Instant

/**
 * The whole collector flow on a real device: studies → a study → a form →
 * saved → the queue.
 *
 * The JVM suite already drives this graph under Robolectric. What only a device
 * can show is that the same walk works with the real Compose runtime, real
 * touch dispatch and the SQLite that actually ships — and, at the end, that a
 * submission saved through the UI arrives in the Queue screen as a queued row.
 * That last step crosses four modules and is the one a unit test cannot claim.
 */
@RunWith(AndroidJUnit4::class)
class CairnNavigationTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var application: CairnApplication
    private lateinit var controller: TestNavHostController

    @Before
    fun setUp() {
        application = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as CairnApplication
        seed(application.database)
    }

    @After
    fun tearDown() = runBlocking {
        application.database.clearAllTables()
    }

    private fun seed(db: CairnDatabase) = runBlocking {
        db.clearAllTables()
        db.studies().upsert(
            listOf(
                StudyEntity(KLUANE, "Kluane ground squirrel survey", TOMAS, T0),
                StudyEntity(PEEL, "Peel watershed water quality", TOMAS, T0),
            ),
        )
        // Two roles for one person: Adaku collects in Kluane and coordinates
        // Peel. The review screens are a property of where you are standing,
        // not of who is signed in.
        db.members().upsert(
            listOf(
                StudyMemberEntity(KLUANE, ADAKU, StudyRole.COLLECTOR, T0),
                StudyMemberEntity(PEEL, ADAKU, StudyRole.COORDINATOR, T0),
            ),
        )
        db.forms().upsertForms(
            listOf(
                FormEntity(BASELINE, KLUANE, "baseline_intake", T0),
                FormEntity(WATER, PEEL, "water_sample", T0),
            ),
        )
        db.forms().upsertVersions(
            listOf(
                FormVersionEntity(BASELINE_V3, BASELINE, 3, schema, T0, T0),
                FormVersionEntity(WATER_V1, WATER, 1, schema, T0, T0),
            ),
        )
        db.participants().upsert(
            listOf(
                ParticipantEntity(KL_0148, KLUANE, "KL-0148", T0),
                ParticipantEntity(PW_0007, PEEL, "PW-0007", T0),
            ),
        )
        // Collected by someone else, already on the server. A coordinator
        // reviewing their own unsent work is the case that has no server id.
        db.submissions().upsert(
            SubmissionEntity(
                collectedBy = TOMAS,
                clientId = CLIENT,
                id = SERVER,
                studyId = PEEL,
                formVersionId = WATER_V1,
                participantId = PW_0007,
                collectedAt = T0,
                data = buildJsonObject { put("body_mass", 268.0) },
                updatedAt = T0,
                syncState = SyncState.UPLOADED,
            ),
        )
    }

    private fun start() {
        compose.setContent {
            val context = LocalContext.current
            controller = TestNavHostController(context).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
            }
            CairnTheme {
                CairnNavHost(
                    application = application,
                    userId = ADAKU,
                    email = "adaku@cairn.test",
                    stale = false,
                    onSignOut = {},
                    controller = controller,
                )
            }
        }
        compose.waitForIdle()
    }

    private fun route(): String? = controller.currentBackStackEntry?.destination?.route

    private fun tap(tag: String, scroll: Boolean = true) {
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasTestTag(tag) and hasClickAction()).fetchSemanticsNodes().size == 1
        }
        val node = compose.onNode(hasTestTag(tag) and hasClickAction())
        if (scroll) node.performScrollTo()
        node.performClick()
        compose.waitForIdle()
    }

    @Test
    fun aCollectorWalksFromStudiesToAFormAndBack() {
        start()
        assertEquals(CairnDestinations.STUDIES, route())

        tap("study_$KLUANE")
        assertEquals(CairnDestinations.STUDY_PATTERN, route())
        compose.onNodeWithTag("study_name").assertIsDisplayed()

        tap("form_$BASELINE")
        assertEquals(CairnDestinations.CAPTURE_PATTERN, route())
        compose.onNodeWithTag("form_title").assertIsDisplayed()

        tap("close", scroll = false)
        assertEquals(CairnDestinations.STUDY_PATTERN, route())
    }

    /**
     * Four modules in one gesture: the form renders from its schema, the value
     * is validated by `:core:model`, the row is written by `:feature:capture`
     * into Room, and the Queue screen reads it back through a different query
     * than the one that wrote it.
     */
    @Test
    fun aSubmissionSavedThroughTheUiAppearsInTheQueue() {
        start()
        tap("study_$KLUANE")
        tap("form_$BASELINE")

        compose.onNodeWithTag("input_body_mass").performScrollTo().performTextInput("268")
        // The Save button lives in the screen's bottom bar, outside the scroll.
        tap("primary_action", scroll = false)

        val saved = runBlocking { application.database.submissions().observePending(ADAKU).first() }
        assertEquals(1, saved.size)
        assertEquals(SyncState.QUEUED, saved.single().syncState)

        tap("close", scroll = false)
        tap("nav_queue", scroll = false)

        assertEquals(CairnDestinations.QUEUE, route())

        /*
         * Found by the client id the capture flow minted, not by a participant
         * code: capture does not attach a participant yet, so the row is
         * labelled by its client id — which is exactly the fallback the Queue
         * screen exists to make readable.
         */
        val clientId = saved.single().clientId
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasTestTag("row_$clientId")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("row_$clientId").assertIsDisplayed()
        compose.onNodeWithTag("stat_queued").assertTextEquals("1")
    }

    @Test
    fun theBottomBarReachesEveryDestination() {
        start()

        tap("nav_queue", scroll = false)
        assertEquals(CairnDestinations.QUEUE, route())

        tap("nav_settings", scroll = false)
        assertEquals(CairnDestinations.SETTINGS, route())
        compose.onNodeWithTag("signed_in_as").assertIsDisplayed()

        tap("nav_collect", scroll = false)
        assertEquals(CairnDestinations.STUDIES, route())
    }

    /**
     * The coordinator's walk: a study they coordinate → every submission in it,
     * whoever collected it → one of them, read against the version it pins.
     *
     * The collector's walk above proves a row can be *made*. This proves the
     * other direction — that a row collected by someone else arrives on a
     * screen that can act on it — with real touch dispatch and the SQLite that
     * ships. Kluane offers none of this, which is the same assertion from the
     * other side: the role is a row in `study_members`, not a property of the
     * person holding the phone.
     */
    @Test
    fun aCoordinatorWalksFromAStudyToOneSubmission() {
        start()

        tap("study_$KLUANE")
        awaitNode("form_$BASELINE")
        compose.onAllNodes(hasTestTag("open_submissions")).fetchSemanticsNodes().let {
            assertEquals(0, it.size)
        }
        tap("back", scroll = false)

        tap("study_$PEEL")
        tap("open_submissions")
        assertEquals(CairnDestinations.SUBMISSIONS_PATTERN, route())
        compose.onNodeWithTag("screen_title").assertTextEquals("Submissions")

        tap("row_$CLIENT")
        assertEquals(CairnDestinations.SUBMISSION_PATTERN, route())
        compose.onNodeWithTag("submission_label").assertTextEquals("PW-0007")
        compose.onNodeWithTag("version").assertTextEquals("v1")
        compose.onNodeWithText("Body mass").assertIsDisplayed()
    }

    /**
     * **The honest refusal, end to end on a device.**
     *
     * This build has no server — `CairnTestRunner` substitutes an application
     * with none — so a lock has nowhere to go. What is being proved is that it
     * says so rather than drawing a "Locked" chip: the row in Room is still
     * unlocked afterwards, which is the difference between a screen that
     * reports what the server did and one that reports what it was asked for.
     */
    @Test
    fun lockingWithNoServerSaysSoAndChangesNothing() {
        start()
        tap("study_$PEEL")
        tap("open_submissions")
        tap("row_$CLIENT")

        tap("action_lock")
        compose.onNodeWithTag("dialog_title").assertTextEquals("Lock submission?")
        tap("dialog_confirm", scroll = false)

        awaitNode("problem")
        compose.onNodeWithText(
            "Not locked — the server could not be reached. Try again when you reconnect.",
        ).assertIsDisplayed()

        val row = runBlocking {
            application.database.submissions().observeDetail(TOMAS, CLIENT).first()
        }
        assertNull(row?.lockedAt)
    }

    /**
     * **A month name, on a device.**
     *
     * `d MMM` under `Locale.ROOT` renders `Aug` on the JVM and `M08` on
     * Android: the JVM's root locale falls back to English, Android's ICU has
     * no month abbreviations for it at all. Every unit test passed and the
     * phone read "12 M08 18:44" until a real study was opened on the emulator.
     * Only an instrumented assertion can hold this, which is why it is here and
     * not in `CollectTimeTest`.
     */
    @Test
    fun aMonthIsAMonthNameOnADevice() {
        start()
        tap("study_$PEEL")
        tap("open_submissions")

        awaitNode("row_$CLIENT")
        // The month, not the whole line: the emulator's zone shifts the clock
        // and the seeded row's age depends on the day the suite is run. What is
        // being pinned is that `MMM` produces a name.
        compose.onNode(hasTestTag("row_$CLIENT") and hasClickAction())
            .assertTextContains("Aug", substring = true)

        tap("row_$CLIENT")
        compose.onNodeWithTag("collected_at").assertTextContains("Aug", substring = true)
    }

    @Test
    fun theProgressChartRendersOnADevice() {
        start()
        tap("study_$PEEL")

        tap("open_progress")

        assertEquals(CairnDestinations.PROGRESS_PATTERN, route())
        compose.onNodeWithTag("stat_collected").assertTextEquals("1")
        compose.onNodeWithTag("chart", useUnmergedTree = true).assertExists()
    }

    /** Waits for a node fed by a Room flow, without tapping it. */
    private fun awaitNode(tag: String) {
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true)
                .fetchSemanticsNodes().size == 1
        }
    }

    private companion object {
        const val TIMEOUT = 10_000L

        const val KLUANE = "11111111-1111-1111-1111-111111111111"
        const val PEEL = "11111111-1111-1111-1111-111111111112"
        const val BASELINE = "22222222-2222-2222-2222-222222222221"
        const val WATER = "22222222-2222-2222-2222-222222222222"
        const val BASELINE_V3 = "33333333-3333-3333-3333-333333333331"
        const val WATER_V1 = "33333333-3333-3333-3333-333333333332"
        const val KL_0148 = "44444444-4444-4444-4444-444444444448"
        const val PW_0007 = "44444444-4444-4444-4444-444444444407"
        const val ADAKU = "55555555-5555-5555-5555-555555555551"
        const val TOMAS = "55555555-5555-5555-5555-555555555552"
        const val CLIENT = "cccccccc-0000-0000-0000-000000000001"
        const val SERVER = "99999999-9999-9999-9999-999999999999"

        val T0: Instant = Instant.parse("2026-08-12T09:00:00Z")

        val schema = buildJsonObject {
            putJsonArray("fields") {
                addJsonObject {
                    put("key", "body_mass")
                    put("type", "number")
                    put("label", "Body mass")
                    put("required", true)
                }
            }
        }
    }
}
