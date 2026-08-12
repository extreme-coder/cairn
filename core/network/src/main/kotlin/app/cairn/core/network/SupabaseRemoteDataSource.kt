package app.cairn.core.network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.PostgrestQueryBuilder
import io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder
import io.github.jan.supabase.postgrest.result.PostgrestResult
import io.ktor.client.engine.HttpClientEngine

/**
 * Builds the Supabase client.
 *
 * [key] must be the **publishable** key. RLS is the security boundary and the
 * APK ships this key to every device; a `service_role` key in a shipped binary
 * would be a total compromise.
 *
 * [engine] is injectable so tests can drive a `MockEngine` and assert on the
 * request that would have gone out.
 *
 * **The session is not loaded from storage.** This is a pure Kotlin JVM module,
 * so supabase-kt would fall back to its JVM session manager and write a token to
 * a file beside the process rather than anywhere an Android app should keep one.
 * Persisting the refresh token properly is its own piece of work; until then the
 * caller signs in explicitly and the session lives in memory, refreshed in place
 * so a long-running app does not start failing with an expired JWT.
 */
public fun cairnSupabaseClient(
    url: String,
    key: String,
    engine: HttpClientEngine? = null,
): SupabaseClient = createSupabaseClient(supabaseUrl = url, supabaseKey = key) {
    install(Auth) {
        autoLoadFromStorage = false
        autoSaveToStorage = false
        alwaysAutoRefresh = true
    }
    install(Postgrest)
    if (engine != null) httpEngine = engine
}

public class SupabaseRemoteDataSource(
    private val client: SupabaseClient,
) : RemoteDataSource {

    override suspend fun signIn(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    override suspend fun signOut() {
        client.auth.signOut()
    }

    override suspend fun currentUserId(): String? = client.auth.currentUserOrNull()?.id

    override suspend fun studies(since: String?, limit: Int): List<StudyDto> =
        client.from("studies").page(since, limit).decodeList()

    override suspend fun members(studyId: String, since: String?, limit: Int): List<StudyMemberDto> =
        client.from("study_members").page(since, limit) { eq("study_id", studyId) }.decodeList()

    override suspend fun forms(studyId: String, since: String?, limit: Int): List<FormDto> =
        client.from("forms").page(since, limit) { eq("study_id", studyId) }.decodeList()

    override suspend fun formVersions(
        formIds: List<String>,
        since: String?,
        limit: Int,
    ): List<FormVersionDto> {
        if (formIds.isEmpty()) return emptyList()
        return client.from("form_versions")
            .page(since, limit) { isIn("form_id", formIds) }
            .decodeList()
    }

    override suspend fun participants(
        studyId: String,
        since: String?,
        limit: Int,
    ): List<ParticipantDto> =
        client.from("participants").page(since, limit) { eq("study_id", studyId) }.decodeList()

    override suspend fun translations(
        formVersionIds: List<String>,
        since: String?,
        limit: Int,
    ): List<FormTranslationDto> {
        if (formVersionIds.isEmpty()) return emptyList()
        return client.from("form_translations")
            .page(since, limit) { isIn("form_version_id", formVersionIds) }
            .decodeList()
    }

    override suspend fun submissions(
        studyId: String,
        since: String?,
        limit: Int,
    ): List<SubmissionDto> =
        client.from("submissions").page(since, limit) { eq("study_id", studyId) }.decodeList()

    /**
     * `updated_at` is not sent. The server owns it — the touch trigger overwrites
     * whatever arrives, on insert as well as update — and it is what
     * last-write-wins compares. Stripping it here means a device with a skewed
     * clock cannot even attempt to win every conflict.
     */
    override suspend fun push(submissions: List<SubmissionDto>): List<SubmissionDto> {
        if (submissions.isEmpty()) return emptyList()
        return client.from("submissions").upsert(
            submissions.map { it.copy(updatedAt = null) },
        ) {
            onConflict = "collected_by,client_id"
            select()
        }.decodeList()
    }
}

/**
 * The one paging shape every read uses: whatever scoping the table needs, then
 * `updated_at > cursor`, oldest first, capped.
 *
 * Ordering and the cursor comparison must agree or a page boundary silently
 * drops rows, which is the kind of bug that shows up as missing data months
 * later rather than as an error.
 */
private suspend fun PostgrestQueryBuilder.page(
    since: String?,
    limit: Int,
    scope: PostgrestFilterBuilder.() -> Unit = {},
): PostgrestResult = select {
    filter {
        scope()
        if (since != null) gt("updated_at", since)
    }
    order("updated_at", Order.ASCENDING)
    limit(limit.toLong())
}
