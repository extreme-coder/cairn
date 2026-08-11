package app.cairn.core.network

/**
 * Everything Cairn asks of a server, in one interface.
 *
 * `:core:sync` depends on this and not on Supabase, so the Firestore adapter on
 * the cut list stays an adapter rather than a rewrite, and so sync can be tested
 * against a fake without a network.
 *
 * **Every read is incremental and every read pages the same way.** Pass the
 * server's own `updated_at` string from the last row of the previous page back
 * as [since]; null starts from the beginning. Rows come back oldest first, and a
 * page shorter than the limit means the caller has caught up.
 *
 * That uniformity only became possible on 2026-08-11, when `updated_at` and its
 * touch triggers were added to the five reference tables. Before that they had
 * `created_at` only, which cannot express "this row changed".
 */
public interface RemoteDataSource {

    public suspend fun signIn(email: String, password: String)

    public suspend fun signOut()

    /** Null when signed out. This is the `collected_by` every submission carries. */
    public suspend fun currentUserId(): String?

    public suspend fun studies(since: String? = null, limit: Int = PAGE): List<StudyDto>

    public suspend fun members(
        studyId: String,
        since: String? = null,
        limit: Int = PAGE,
    ): List<StudyMemberDto>

    public suspend fun forms(
        studyId: String,
        since: String? = null,
        limit: Int = PAGE,
    ): List<FormDto>

    public suspend fun formVersions(
        formIds: List<String>,
        since: String? = null,
        limit: Int = PAGE,
    ): List<FormVersionDto>

    public suspend fun participants(
        studyId: String,
        since: String? = null,
        limit: Int = PAGE,
    ): List<ParticipantDto>

    public suspend fun translations(
        formVersionIds: List<String>,
        since: String? = null,
        limit: Int = PAGE,
    ): List<FormTranslationDto>

    public suspend fun submissions(
        studyId: String,
        since: String? = null,
        limit: Int = PAGE,
    ): List<SubmissionDto>

    /**
     * Upserts on `(collected_by, client_id)` and returns what the server stored,
     * including the `id` it assigned and its authoritative `updated_at`.
     *
     * Replaying a push is a no-op rather than a duplicate. That is the whole
     * reason `client_id` is minted when a form opens.
     */
    public suspend fun push(submissions: List<SubmissionDto>): List<SubmissionDto>

    public companion object {
        public const val PAGE: Int = 500
    }
}
