package app.cairn.core.model

/**
 * Where a submission has got to on its way to the server.
 *
 * Device-local: the server has no such column, because from its side a row
 * either arrived or did not.
 */
public enum class SyncState {
    QUEUED,
    UPLOADED,
    FAILED,
    ;

    /**
     * The word that travels with the status dot.
     *
     * `DESIGN.md` fixes these — status is never colour alone, and it is never a
     * different word on a different screen. Written here so the Queue, the
     * Collect screen and review all read from the same three strings.
     */
    public val label: String
        get() = when (this) {
            QUEUED -> "Queued"
            UPLOADED -> "Uploaded"
            FAILED -> "Failed"
        }
}
