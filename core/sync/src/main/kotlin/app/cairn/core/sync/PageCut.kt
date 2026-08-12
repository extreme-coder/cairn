package app.cairn.core.sync

/**
 * What to keep from one page, and where that leaves the cursor.
 *
 * @property keep rows safe to apply now
 * @property cursor the value to persist once [keep] is applied, or null to leave the cursor alone
 * @property more whether another page is definitely waiting
 */
internal data class PageCut<out T>(
    val keep: List<T>,
    val cursor: String?,
    val more: Boolean,
)

/**
 * Decides how much of a page can be trusted.
 *
 * The problem this exists for: the pull asks for `updated_at > cursor` ordered
 * ascending, and sets the next cursor from the last row it saw. That is only
 * safe if the last row is the *last* row at its timestamp. It often is not.
 * `private.touch_updated_at()` uses `now()`, which in Postgres is transaction
 * start time — so a coordinator publishing forty form versions in one
 * transaction gives all forty the identical `updated_at`. Land in the middle of
 * that group at a page boundary and the next `gt(cursor)` steps over the rest of
 * it. Nothing errors. The rows are simply never offered again.
 *
 * So a full page gives up its trailing tie group: the cursor is set to the last
 * timestamp that is definitely complete, and the dropped rows arrive again on
 * the next page. The cost is re-fetching a few rows; the alternative is losing
 * them.
 *
 * A page that is entirely one timestamp has no complete timestamp to fall back
 * to, and that is [SyncException.CursorStalled] rather than a guess.
 */
internal fun <T> cutPage(
    rows: List<T>,
    pageSize: Int,
    table: String,
    updatedAt: (T) -> String,
): PageCut<T> {
    if (rows.isEmpty()) return PageCut(emptyList(), cursor = null, more = false)

    val last = updatedAt(rows.last())

    if (rows.size < pageSize) {
        return PageCut(rows, cursor = last, more = false)
    }

    val tieStart = rows.indexOfFirst { updatedAt(it) == last }
    if (tieStart == 0) {
        throw SyncException.CursorStalled(table, last, pageSize)
    }

    val keep = rows.subList(0, tieStart)
    return PageCut(keep, cursor = updatedAt(keep.last()), more = true)
}
