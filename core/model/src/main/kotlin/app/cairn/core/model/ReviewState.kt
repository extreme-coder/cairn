package app.cairn.core.model

import kotlin.time.Instant

/**
 * What has been done to a submission, which is the one thing a review list is
 * scanned for.
 *
 * Not a column. `locked_at` and `deleted_at` are two nullable timestamps and the
 * server keeps them that way, because the *when* is part of the record. This is
 * the reading of them that a screen needs, derived in one place so a chip on the
 * review list and a line on the detail screen cannot disagree.
 */
public enum class ReviewState {

    /** Collected, not yet verified. Still amendable by its collector. */
    OPEN,

    /** Verified. The database refuses every further client update, including unlocking. */
    LOCKED,

    /** Excluded from analysis, row kept. Nothing in Cairn is ever hard-deleted. */
    VOIDED,
    ;

    /**
     * Here rather than in a screen for the same reason [StudyRole.label] is: the
     * voice guide fixes these three words, and one-term-per-concept is only real
     * if there is one place they are written.
     */
    public val label: String
        get() = when (this) {
            OPEN -> "Open"
            LOCKED -> "Locked"
            VOIDED -> "Voided"
        }
}

/**
 * How a row's two timestamps read as one state.
 *
 * **Voided wins.** A submission can be both — voiding a row does not lock it, and
 * a voided row is still unlocked, so it can then be locked. When both are true
 * the fact that matters to anyone reading the study is that the observation is
 * out of the analysis, so that is what the chip says. The detail screen has room
 * for both and states both.
 */
public fun reviewStateOf(lockedAt: Instant?, deletedAt: Instant?): ReviewState = when {
    deletedAt != null -> ReviewState.VOIDED
    lockedAt != null -> ReviewState.LOCKED
    else -> ReviewState.OPEN
}
