package app.cairn.feature.collect

import app.cairn.core.database.dao.QueueCounts
import app.cairn.core.model.StudyRole
import app.cairn.core.model.SyncState

/**
 * Fixtures for `@Preview` and the screenshot tests.
 *
 * In `main` rather than `test` because previews are compiled into the debug
 * build and cannot see a test source set — the same reason
 * `CapturePreviewData.kt` lives where it does. Numbers match the mockups so a
 * screenshot can be held against the design without arithmetic.
 */

internal fun previewStudies(): StudiesUiState.Ready = StudiesUiState.Ready(
    studies = listOf(
        StudyRow(
            id = "study-kluane",
            name = "Kluane ground squirrel survey",
            role = StudyRole.COLLECTOR,
            detail = "3 forms · 148 submissions",
            status = "6 queued",
            pendingCount = 6,
        ),
        StudyRow(
            id = "study-peel",
            name = "Peel watershed water quality",
            role = StudyRole.COORDINATOR,
            detail = "5 forms · 482 submissions",
            status = "All uploaded",
            pendingCount = 0,
        ),
        StudyRow(
            id = "study-haida",
            name = "Haida Gwaii intertidal transects",
            role = StudyRole.VIEWER,
            detail = "2 forms · 96 submissions",
            status = "Read only",
            pendingCount = 0,
        ),
    ),
)

internal fun previewCollect(): CollectUiState.Ready = CollectUiState.Ready(
    studyName = "Kluane ground squirrel survey",
    role = StudyRole.COLLECTOR,
    forms = listOf(
        FormRow("form-baseline", "Baseline intake", "12 fields", "v3", openable = true),
        FormRow("form-weekly", "Weekly follow-up", "8 fields", "v2", openable = true),
        FormRow("form-trap", "Trap check", "No published version yet", null, openable = false),
    ),
    recent = listOf(
        SubmissionRow("c-148", "KL-0148", "Baseline intake v3 · 09:14", SyncState.QUEUED),
        SubmissionRow("c-147", "KL-0147", "Trap check v5 · 08:52", SyncState.UPLOADED),
    ),
    pendingCount = 6,
)

/** The same study seen by someone who reviews it: the Review section appears. */
internal fun previewCoordinatorCollect(): CollectUiState.Ready = previewCollect().copy(
    role = StudyRole.COORDINATOR,
    pendingCount = 0,
)

internal fun previewQueue(): QueueUiState = QueueUiState(
    counts = QueueCounts(queued = 4, failed = 1, uploaded = 148),
    queued = listOf(
        SubmissionRow("c-148", "KL-0148", "Baseline intake v3 · 09:14", SyncState.QUEUED),
        SubmissionRow("c-147", "KL-0147", "Trap check v5 · 08:52", SyncState.QUEUED),
        SubmissionRow("c-146", "KL-0146", "Trap check v5 · 08:31", SyncState.QUEUED),
        SubmissionRow("c-145", "KL-0145", "Weekly follow-up v2 · 08:04", SyncState.QUEUED),
    ),
    failed = listOf(
        SubmissionRow("c-141", "KL-0141", "Trap check v5 · 07:20", SyncState.FAILED),
    ),
)

internal fun previewSettings(): SettingsUiState = SettingsUiState(
    email = "adaku.obi@cairn.test",
    userId = "55555555-5555-5555-5555-555555555551",
    server = "cairn.psych.ubc.ca",
    lastSynced = "14 minutes ago",
    pendingCount = 0,
    version = "0.1.0 (1)",
)
