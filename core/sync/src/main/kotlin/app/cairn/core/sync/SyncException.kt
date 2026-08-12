package app.cairn.core.sync

/**
 * The ways sync refuses to continue.
 *
 * Every one of these exists so that a failure is *loud*. The failure mode that
 * matters in a field study is not a crash — it is a sync that reports success
 * and quietly leaves observations behind, discovered months later when the data
 * is analysed and a week of fieldwork is missing.
 */
public sealed class SyncException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** No signed-in user, so there is no `collected_by` to push as and no rows to pull. */
    public class NotSignedIn : SyncException("sync ran with no signed-in user")

    /** The server sent something this build cannot read. */
    public class Malformed(
        message: String,
        cause: Throwable? = null,
    ) : SyncException(message, cause)

    /**
     * A whole page of rows shares one `updated_at`, so the cursor cannot advance
     * without stepping over rows that were never fetched.
     *
     * `private.touch_updated_at()` uses `now()`, which is transaction start time,
     * so every row touched by one statement gets the *same* timestamp. Publish
     * more than a page of form versions in a single transaction and the tie group
     * is wider than the page. Paging by `updated_at > cursor` cannot express
     * "the rest of the rows at this timestamp", so this stops instead of
     * skipping them.
     *
     * The fix when it is ever hit is a compound cursor — `(updated_at, id)` —
     * which needs a change to [app.cairn.core.network.RemoteDataSource].
     */
    public class CursorStalled(
        table: String,
        timestamp: String,
        pageSize: Int,
    ) : SyncException(
        "$table has at least $pageSize rows sharing updated_at=$timestamp; " +
            "the cursor cannot advance past them without skipping some",
    )

    /**
     * Rows were handed to Room and are not there afterwards.
     *
     * Room's `@Upsert` updates by primary key, so a row with a new id that
     * collides on a unique index matches nothing to update and is dropped with no
     * error raised. Left undetected the cursor would advance past a row that
     * never landed, and it would never be offered again.
     */
    public class DeltaIncomplete(
        table: String,
        missing: List<String>,
    ) : SyncException(
        "${missing.size} of the $table rows in this delta did not land: " +
            missing.take(TRACE).joinToString() + if (missing.size > TRACE) " …" else "",
    )

    /**
     * Every queued row failed on its own, so this is the network rather than the
     * rows. They stay `QUEUED` and the worker backs off.
     */
    public class PushUnavailable(
        queued: Int,
        cause: Throwable?,
    ) : SyncException("all $queued queued submissions failed to send", cause)

    private companion object {
        const val TRACE = 5
    }
}
