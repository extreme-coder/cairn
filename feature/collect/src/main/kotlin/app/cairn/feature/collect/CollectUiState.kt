package app.cairn.feature.collect

import app.cairn.core.database.dao.QueueCounts
import app.cairn.core.database.dao.QueuedSubmission
import app.cairn.core.database.toFormSchema
import app.cairn.core.model.StudyRole
import app.cairn.core.model.SyncState
import java.time.ZoneId
import kotlin.time.Instant

/**
 * What the collector's three list screens draw.
 *
 * Every one of these is finished text. A composable in this module reads
 * [SubmissionRow.detail] and puts it on screen; it does not know that a detail
 * line is a form title, a version and a time, and it never formats a date. That
 * is what makes the screens renderable in a preview and assertable in a JVM
 * test without a database — and it is where the copy rules are enforced, since
 * a sentence assembled in a composable is a sentence nothing tests.
 */

/** A study on the Studies screen. */
public data class StudyRow(
    public val id: String,
    public val name: String,
    public val role: StudyRole?,
    public val detail: String,
    public val status: String,
    public val pendingCount: Int,
) {
    /** A viewer may open the study and read it; there is nothing for them to collect. */
    public val collectable: Boolean get() = role?.showsCollectAction != false
}

/** A form on the Collect screen. */
public data class FormRow(
    public val id: String,
    public val title: String,
    public val detail: String,
    public val versionLabel: String?,
    public val openable: Boolean,
)

/** A submission on the Queue screen, and on the Collect screen's recent list. */
public data class SubmissionRow(
    public val clientId: String,
    public val label: String,
    public val detail: String,
    public val state: SyncState,
)

public sealed interface StudiesUiState {

    /** The database has not answered yet. Distinct from having answered "none". */
    public data object Loading : StudiesUiState

    /**
     * No studies, and the two reasons for that need different sentences. Both
     * are zero rows, so the rows cannot tell them apart — only whether a sync
     * has finished on this device can.
     */
    public data class Empty(public val synced: Boolean) : StudiesUiState

    public data class Ready(public val studies: List<StudyRow>) : StudiesUiState
}

public sealed interface CollectUiState {

    public data object Loading : CollectUiState

    /** The study was removed from this device while its screen was open. */
    public data object Gone : CollectUiState

    public data class Ready(
        public val studyName: String,
        public val role: StudyRole?,
        public val forms: List<FormRow>,
        public val recent: List<SubmissionRow>,
        public val pendingCount: Int,
    ) : CollectUiState
}

public data class QueueUiState(
    public val counts: QueueCounts = QueueCounts(0, 0, 0),
    public val queued: List<SubmissionRow> = emptyList(),
    public val failed: List<SubmissionRow> = emptyList(),
    public val uploaded: List<SubmissionRow> = emptyList(),
    public val showingUploaded: Boolean = false,
    /**
     * False until the database has answered. Three zeroes are otherwise
     * indistinguishable from "not asked yet", and the difference is a screen
     * that says "Nothing collected yet" for a frame to someone who has
     * collected all morning.
     */
    public val loaded: Boolean = false,
) {
    public val isEmpty: Boolean get() = loaded && counts.total == 0

    /**
     * Nothing to upload means nothing for the button to do, and a button that
     * does nothing when pressed teaches people to stop believing this screen.
     */
    public val canUpload: Boolean get() = counts.pending > 0
}

public data class SettingsUiState(
    public val email: String? = null,
    public val userId: String = "",
    public val server: String = "",
    public val stale: Boolean = false,
    public val lastSynced: String = "",
    public val pendingCount: Int = 0,
    public val version: String = "",
) {
    /**
     * Two letters in a tonal circle — the design forbids photo avatars, so
     * initials are the only representation of a person anywhere in this app.
     * Taken from the address rather than a display name because a display name
     * is not a column the server has.
     */
    public val initials: String
        get() = email
            ?.substringBefore('@')
            ?.split('.', '_', '-')
            ?.filter { it.isNotBlank() }
            ?.take(2)
            ?.joinToString("") { it.first().uppercase() }
            ?.ifBlank { null }
            ?: "?"

    /** What a person recognises. Falls back to the id, which is at least true. */
    public val name: String get() = email ?: userId
}

// ---- Mapping, in one place ----

internal fun QueuedSubmission.toRow(now: Instant, zone: ZoneId): SubmissionRow = SubmissionRow(
    clientId = clientId,
    label = submissionLabel(participantCode, clientId),
    detail = "${formTitle(formCode)} v$version · ${collectedLabel(collectedAt, now, zone)}",
    state = syncState,
)

/**
 * A study's second line and its status.
 *
 * The status is the one number a collector is actually looking for: whether
 * anything is still on the phone. "All uploaded" is the reassuring case and is
 * stated rather than left blank, because a blank says nothing was checked.
 */
internal fun studyDetail(formCount: Int, submissionCount: Int): String =
    "$formCount ${plural(formCount, "form")} · $submissionCount ${plural(submissionCount, "submission")}"

internal fun studyStatus(role: StudyRole?, pendingCount: Int): String = when {
    role?.showsCollectAction == false -> "Read only"
    pendingCount > 0 -> "$pendingCount queued"
    else -> "All uploaded"
}

/**
 * A form's second line.
 *
 * Three states, and they are genuinely different: a form that can be filled in,
 * one whose version has not arrived yet, and one this build cannot render. The
 * third is the raw-JSON decision from `:core:database` surfacing — the row is
 * safely on disk and one form will not open, rather than the study failing to
 * sync at all.
 */
internal fun formDetail(version: Int?, schema: kotlinx.serialization.json.JsonObject?): String {
    if (version == null || schema == null) return "No published version yet"
    val fields = runCatching { schema.toFormSchema().fields.size }.getOrNull()
        ?: return "Not supported by this version of Cairn"
    // The version is on the chip beside the title; repeating it here would be
    // the same fact twice on one row.
    return "$fields ${plural(fields, "field")}"
}

internal fun formOpenable(version: Int?, schema: kotlinx.serialization.json.JsonObject?): Boolean =
    version != null && schema != null && runCatching { schema.toFormSchema() }.isSuccess
