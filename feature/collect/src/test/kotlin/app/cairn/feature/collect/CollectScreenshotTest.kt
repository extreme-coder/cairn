package app.cairn.feature.collect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import app.cairn.core.database.dao.QueueCounts
import app.cairn.core.designsystem.CairnBottomBar
import app.cairn.core.designsystem.CairnDestination
import app.cairn.core.designsystem.CairnTheme
import app.cairn.core.model.SyncState
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders each screen to a PNG on the JVM.
 *
 * No emulator and no device: Robolectric's native graphics mode draws the real
 * Compose tree, fonts included, so the output is what the app looks like rather
 * than an approximation. Files land in `build/outputs/roborazzi`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class CollectScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.setContent { CairnTheme { content() } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun studies() {
        shoot("studies") { StudiesScreen(previewStudies(), onStudy = {}) }
    }

    @Test
    fun studies_with_none() {
        shoot("studies-empty") {
            StudiesScreen(StudiesUiState.Empty(synced = true), onStudy = {})
        }
    }

    @Test
    fun collect() {
        shoot("collect") { CollectScreen(previewCollect(), onForm = {}, onBack = {}) }
    }

    @Test
    fun collect_with_no_forms() {
        shoot("collect-no-forms") {
            CollectScreen(
                previewCollect().copy(forms = emptyList(), recent = emptyList(), pendingCount = 0),
                onForm = {},
                onBack = {},
            )
        }
    }

    @Test
    fun collect_when_the_study_is_gone() {
        shoot("collect-gone") { CollectScreen(CollectUiState.Gone, onForm = {}, onBack = {}) }
    }

    @Test
    fun queue() {
        shoot("queue") { QueueScreen(previewQueue(), onUploadNow = {}, onToggleUploaded = {}) }
    }

    @Test
    fun queue_showing_uploaded() {
        shoot("queue-uploaded") {
            QueueScreen(
                previewQueue().copy(
                    showingUploaded = true,
                    uploaded = listOf(
                        SubmissionRow("c-1", "KL-0100", "Trap check v5 · 07:00", SyncState.UPLOADED),
                        SubmissionRow("c-2", "KL-0099", "Trap check v5 · 06:41", SyncState.UPLOADED),
                    ),
                ),
                onUploadNow = {},
                onToggleUploaded = {},
            )
        }
    }

    @Test
    fun queue_when_empty() {
        shoot("queue-empty") {
            QueueScreen(QueueUiState(loaded = true), onUploadNow = {}, onToggleUploaded = {})
        }
    }

    @Test
    fun queue_with_nothing_to_upload() {
        shoot("queue-all-uploaded") {
            QueueScreen(
                QueueUiState(counts = QueueCounts(0, 0, 148), loaded = true),
                onUploadNow = {},
                onToggleUploaded = {},
            )
        }
    }

    @Test
    fun settings() {
        shoot("settings") { SettingsScreen(previewSettings(), onSignOut = {}) }
    }

    @Test
    fun settings_when_stale_with_work_unsent() {
        shoot("settings-stale") {
            SettingsScreen(
                previewSettings().copy(stale = true, pendingCount = 3, lastSynced = "2 hours ago"),
                onSignOut = {},
            )
        }
    }

    /** The bar itself, at both of its states, since no screen owns it. */
    @Test
    fun bottom_navigation() {
        shoot("bottom-bar") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                CairnBottomBar(
                    destinations = listOf(
                        CairnDestination("Collect"),
                        CairnDestination("Queue", badge = 6),
                        CairnDestination("Settings"),
                    ),
                    selected = 0,
                    onSelect = {},
                )
                CairnBottomBar(
                    destinations = listOf(
                        CairnDestination("Collect"),
                        CairnDestination("Queue"),
                        CairnDestination("Settings"),
                    ),
                    selected = 1,
                    onSelect = {},
                )
            }
        }
    }
}
