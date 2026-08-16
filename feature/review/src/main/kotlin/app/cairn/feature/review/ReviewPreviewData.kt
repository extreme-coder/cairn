package app.cairn.feature.review

import app.cairn.core.database.dao.ReviewCounts
import app.cairn.core.model.ReviewState

/**
 * Fixtures for `@Preview` and the screenshot tests.
 *
 * In `main` rather than `test` because previews are compiled into the debug
 * build and cannot see a test source set — the same reason `CollectPreviewData`
 * and `CapturePreviewData` live where they do. The numbers match the mockups so
 * a screenshot can be held against the design without arithmetic.
 */

internal fun previewSubmissions(): SubmissionsUiState.Ready = SubmissionsUiState.Ready(
    studyName = "Kluane ground squirrel survey",
    rows = listOf(
        ReviewRow("u-adaku", "c-148", "KL-0148", "Baseline intake v3 · 09:14", ReviewState.OPEN),
        ReviewRow("u-adaku", "c-147", "KL-0147", "Trap check v5 · 08:52", ReviewState.OPEN),
        ReviewRow("u-marta", "c-146", "KL-0146", "Trap check v5 · Yesterday 16:31", ReviewState.LOCKED),
        ReviewRow("u-marta", "c-145", "KL-0145", "Weekly follow-up v2 · Yesterday 15:04", ReviewState.LOCKED),
        ReviewRow("u-adaku", "c-141", "KL-0141", "Trap check v5 · 11 Aug 07:20", ReviewState.VOIDED),
    ),
    counts = ReviewCounts(collected = 147, locked = 96, voided = 1, participants = 42),
)

internal fun previewHeader(state: ReviewState = ReviewState.OPEN): DetailHeader = DetailHeader(
    label = "KL-0148",
    studyName = "Kluane ground squirrel survey",
    formTitle = "Baseline intake",
    versionLabel = "v3",
    collected = "13 Aug 2026 · 09:14",
    state = state,
    locked = state == ReviewState.LOCKED,
    voided = state == ReviewState.VOIDED,
)

internal fun previewDetail(): DetailUiState.Ready = DetailUiState.Ready(
    header = previewHeader(),
    fields = listOf(
        AnsweredField("Body mass", "268 g", mono = true),
        AnsweredField("Sex", "Female"),
        AnsweredField("Burrow condition", "Intact, recently dug"),
        AnsweredField("Date first seen", "2026-08-11", mono = true),
        AnsweredField("Ectoparasites", "Fleas, ticks"),
        AnsweredField("Notes", "Not answered"),
    ),
    actions = listOf(ReviewAction.LOCK, ReviewAction.VOID),
)

internal fun previewLockedDetail(): DetailUiState.Ready = previewDetail().copy(
    header = previewHeader(ReviewState.LOCKED),
    actions = emptyList(),
    note = "Locked submissions cannot be changed. Unlocking is not possible from Cairn.",
)

internal fun previewProgress(): ProgressUiState = ProgressUiState(
    studyName = "Kluane ground squirrel survey",
    counts = ReviewCounts(collected = 147, locked = 96, voided = 1, participants = 42),
    bars = listOf(
        4 to "31 Jul", 7 to "1 Aug", 0 to "2 Aug", 0 to "3 Aug", 11 to "4 Aug",
        9 to "5 Aug", 12 to "6 Aug", 3 to "7 Aug", 0 to "8 Aug", 6 to "9 Aug",
        8 to "10 Aug", 14 to "11 Aug", 5 to "12 Aug", 2 to "13 Aug",
    ).map { (count, label) -> ProgressBar(day = label, axisLabel = label, count = count) },
    caption = studyProgressCaption(14),
    loaded = true,
)
