package app.cairn

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import app.cairn.core.database.CairnDatabase
import app.cairn.core.database.entity.FormEntity
import app.cairn.core.database.entity.FormVersionEntity
import app.cairn.core.database.entity.StudyEntity
import app.cairn.core.database.entity.StudyMemberEntity
import app.cairn.core.designsystem.CairnTheme
import app.cairn.core.model.StudyRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

/**
 * The back stack, driven through the real graph.
 *
 * A `TestNavHostController` over the app's own `NavHost`, so what is asserted is
 * the route the app actually lands on rather than a callback a test wired up
 * itself. The three things worth pinning are the ones a reader cannot check by
 * eye: that a form opens under the study it was tapped in, that a tab keeps its
 * position across a switch, and that capture takes the whole window.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = CairnNavHostTest.NoServerApplication::class)
class CairnNavHostTest {

    /**
     * The app graph with no server behind it.
     *
     * Not a stub: this is the build a machine with no `local.properties`
     * produces, and everything under test here — the database, the routes, the
     * screens — is identical either way. What it avoids is `onCreate`
     * constructing a Supabase client, whose session storage cannot initialise
     * off a device.
     */
    class NoServerApplication : CairnApplication() {
        override val serverUrl: String get() = ""
        override val serverKey: String get() = ""
    }

    @get:Rule
    val compose = createComposeRule()

    private lateinit var application: CairnApplication
    private lateinit var controller: TestNavHostController

    private val t0 = Instant.parse("2026-08-12T09:00:00Z")

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        seed(application.database)
    }

    @After
    fun tearDown() = runBlocking(Dispatchers.IO) {
        application.database.clearAllTables()
    }

    // Off the main thread: Room refuses a clear on it, and Robolectric runs
    // tests *on* the main looper.
    private fun seed(db: CairnDatabase) = runBlocking(Dispatchers.IO) {
        db.clearAllTables()
        db.studies().upsert(
            listOf(
                StudyEntity(KLUANE, "Kluane ground squirrel survey", TOMAS, t0),
                StudyEntity(PEEL, "Peel watershed water quality", TOMAS, t0),
            ),
        )
        db.members().upsert(
            listOf(StudyMemberEntity(KLUANE, ADAKU, StudyRole.COLLECTOR, t0)),
        )
        db.forms().upsertForms(listOf(FormEntity(BASELINE, KLUANE, "baseline_intake", t0)))
        db.forms().upsertVersions(
            listOf(FormVersionEntity(BASELINE_V3, BASELINE, 3, schema, t0, t0)),
        )
    }

    private fun start() {
        compose.setContent {
            val context = LocalContext.current
            var signOuts by remember { mutableStateOf(0) }
            controller = remember {
                TestNavHostController(context).apply {
                    navigatorProvider.addNavigator(ComposeNavigator())
                }
            }
            CairnTheme {
                CairnNavHost(
                    application = application,
                    userId = ADAKU,
                    email = "adaku@cairn.test",
                    stale = false,
                    onSignOut = { signOuts++ },
                    controller = controller,
                )
            }
        }
        compose.waitForIdle()
    }

    private fun route(): String? = controller.currentBackStackEntry?.destination?.route

    /**
     * Waits for a row to exist *and* be tappable before tapping it.
     *
     * These lists are fed by Room flows, so `waitForIdle` is not enough: it
     * returns once Compose has nothing left to draw, which happens before the
     * database has answered. A form row also becomes clickable only once its
     * published version has arrived, which is a second emission.
     */
    private fun tap(tag: String, scroll: Boolean = true) {
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasTestTag(tag) and hasClickAction())
                .fetchSemanticsNodes().size == 1
        }
        val node = compose.onNode(hasTestTag(tag) and hasClickAction())
        // App-bar controls have no scrollable ancestor, and asking to scroll to
        // one fails on the missing parent rather than on the node.
        if (scroll) node.performScrollTo()
        node.performClick()
        compose.waitForIdle()
    }

    @Test
    fun `the app opens on the studies list`() {
        start()

        assertEquals(CairnDestinations.STUDIES, route())
        compose.onNodeWithText("Kluane ground squirrel survey").assertIsDisplayed()
    }

    @Test
    fun `tapping a study opens that study`() {
        start()

        tap("study_$KLUANE")

        assertEquals(CairnDestinations.STUDY_PATTERN, route())
        assertEquals(
            KLUANE,
            controller.currentBackStackEntry?.arguments?.getString(CairnDestinations.ARG_STUDY),
        )
    }

    /**
     * The whole reason a study is a level of its own: a submission carries the
     * study it was collected in, and there is no moving it afterwards.
     */
    @Test
    fun `a form opens under the study it was tapped in`() {
        start()
        tap("study_$KLUANE")

        tap("form_$BASELINE")

        assertEquals(CairnDestinations.CAPTURE_PATTERN, route())
        val arguments = controller.currentBackStackEntry?.arguments
        assertEquals(KLUANE, arguments?.getString(CairnDestinations.ARG_STUDY))
        assertEquals(BASELINE, arguments?.getString(CairnDestinations.ARG_FORM))
    }

    @Test
    fun `closing a form returns to the study it was opened from`() {
        start()
        tap("study_$KLUANE")
        tap("form_$BASELINE")

        tap("close", scroll = false)

        assertEquals(CairnDestinations.STUDY_PATTERN, route())
    }

    @Test
    fun `the bottom bar reaches the queue and settings`() {
        start()

        compose.onNodeWithTag("nav_queue").performClick()
        compose.waitForIdle()
        assertEquals(CairnDestinations.QUEUE, route())

        compose.onNodeWithTag("nav_settings").performClick()
        compose.waitForIdle()
        assertEquals(CairnDestinations.SETTINGS, route())
    }

    /**
     * Checking the queue mid-study and coming back should land where it was
     * left, not at the top of the studies list. That is what `saveState` and
     * `restoreState` are for, and it is invisible until it is wrong.
     */
    @Test
    fun `a tab keeps its position across a switch`() {
        start()
        tap("study_$KLUANE")

        compose.onNodeWithTag("nav_queue").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("nav_collect").performClick()
        compose.waitForIdle()

        assertEquals(CairnDestinations.STUDY_PATTERN, route())
    }

    /** Tapping a tab six times must not build six of it to press back through. */
    @Test
    fun `tapping the same tab twice does not stack it`() {
        start()

        compose.onNodeWithTag("nav_queue").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("nav_queue").performClick()
        compose.waitForIdle()

        assertEquals(CairnDestinations.QUEUE, route())
        assertEquals(1, controller.currentBackStack.value.count { it.destination.route == CairnDestinations.QUEUE })
    }

    @Test
    fun `capture takes the whole window`() {
        start()
        compose.onNodeWithTag("nav_collect").assertIsDisplayed()

        tap("study_$KLUANE")
        tap("form_$BASELINE")

        compose.onNodeWithTag("nav_collect").assertDoesNotExist()
    }

    private companion object {
        const val KLUANE = "11111111-1111-1111-1111-111111111111"
        const val PEEL = "11111111-1111-1111-1111-111111111112"
        const val BASELINE = "22222222-2222-2222-2222-222222222221"
        const val BASELINE_V3 = "33333333-3333-3333-3333-333333333331"
        const val ADAKU = "55555555-5555-5555-5555-555555555551"
        const val TOMAS = "55555555-5555-5555-5555-555555555552"

        const val TIMEOUT = 5_000L

        val schema = buildJsonObject {
            putJsonArray("fields") {
                addJsonObject {
                    put("key", "body_mass")
                    put("type", "number")
                    put("label", "Body mass")
                }
            }
        }
    }
}
