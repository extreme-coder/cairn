package app.cairn.core.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Asserts the request that would go out, not a live server.
 *
 * What matters here is the shape: the conflict target, the `Prefer` header, the
 * cursor comparison and the ordering. Those are the contract with PostgREST, and
 * getting one wrong produces duplicate rows or a sync that silently skips data
 * rather than an error anyone would notice.
 */
class SupabaseRemoteDataSourceTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun source(body: String = "[]"): SupabaseRemoteDataSource {
        val engine = MockEngine { request ->
            requests += request
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return SupabaseRemoteDataSource(
            cairnSupabaseClient("https://example.supabase.co", "sb_publishable_test", engine),
        )
    }

    private val last: HttpRequestData get() = requests.last()

    private fun lastBody(): String = (last.body as TextContent).text

    private fun submission(clientId: String = "cccccccc-0000-0000-0000-000000000001") = SubmissionDto(
        studyId = "11111111-1111-1111-1111-111111111111",
        formVersionId = "33333333-3333-3333-3333-333333333332",
        collectedBy = "55555555-5555-5555-5555-555555555551",
        clientId = clientId,
        collectedAt = "2026-07-01T09:30:00+00:00",
        data = buildJsonObject { put("body_mass", 268.0) },
        updatedAt = "2026-07-01T09:30:00.123456+00:00",
    )

    @Test
    fun `a push upserts on the collector and client id pair`() = runTest {
        source().push(listOf(submission()))

        val prefer = last.headers.getAll(HttpHeaders.Prefer).orEmpty().joinToString(",")
        assertContains(prefer, "resolution=merge-duplicates")
        assertContains(last.url.toString(), "on_conflict=collected_by%2Cclient_id")
    }

    /**
     * The device clock must never write the column last-write-wins compares, or a
     * phone with a skewed clock wins every conflict for as long as it is wrong.
     */
    @Test
    fun `a push does not send updated_at even when the local row has one`() = runTest {
        source().push(listOf(submission()))

        assertFalse(lastBody().contains("updated_at"), "body was: ${lastBody()}")
        assertContains(lastBody(), "client_id")
        assertContains(lastBody(), "collected_by")
    }

    @Test
    fun `pushing nothing makes no request at all`() = runTest {
        source().push(emptyList())

        assertTrue(requests.isEmpty())
    }

    @Test
    fun `the submission pull filters by study, advances past the cursor and reads oldest first`() =
        runTest {
            source().submissions(
                studyId = "11111111-1111-1111-1111-111111111111",
                since = "2026-07-01T09:30:00.123456+00:00",
                limit = 500,
            )

            val url = last.url.toString()
            assertContains(url, "study_id=eq.11111111-1111-1111-1111-111111111111")
            assertContains(url, "updated_at=gt.2026-07-01T09%3A30%3A00.123456%2B00%3A00")
            assertContains(url, "order=updated_at.asc")
            assertContains(url, "limit=500")
        }

    @Test
    fun `a first pull sends no cursor`() = runTest {
        source().submissions(studyId = "11111111-1111-1111-1111-111111111111")

        assertFalse(last.url.toString().contains("updated_at=gt"))
    }

    @Test
    fun `reference reads are scoped to one study`() = runTest {
        val remote = source()
        remote.forms("11111111-1111-1111-1111-111111111111")
        assertContains(last.url.toString(), "study_id=eq.11111111-1111-1111-1111-111111111111")

        remote.participants("11111111-1111-1111-1111-111111111111")
        assertContains(last.url.toString(), "study_id=eq.11111111-1111-1111-1111-111111111111")
    }

    /**
     * Reference tables gained `updated_at` and touch triggers on 2026-08-11.
     * Before that these reads had to be full pulls; if the column ever goes away
     * again these assertions are what will say so.
     */
    @Test
    fun `every reference read is incremental on the same cursor`() = runTest {
        val remote = source()
        val cursor = "2026-07-01T09:30:00.123456+00:00"
        val study = "11111111-1111-1111-1111-111111111111"

        val reads: List<Pair<String, suspend () -> Unit>> = listOf(
            "studies" to { remote.studies(since = cursor); Unit },
            "study_members" to { remote.members(study, since = cursor); Unit },
            "forms" to { remote.forms(study, since = cursor); Unit },
            "form_versions" to { remote.formVersions(listOf("f1"), since = cursor); Unit },
            "participants" to { remote.participants(study, since = cursor); Unit },
            "form_translations" to { remote.translations(listOf("v1"), since = cursor); Unit },
        )

        reads.forEach { (table, read) ->
            read()
            val url = last.url.toString()
            assertContains(url, "/rest/v1/$table", message = "wrong table for $table")
            assertContains(url, "updated_at=gt.", message = "no cursor on $table")
            assertContains(url, "order=updated_at.asc", message = "wrong order on $table")
            assertContains(url, "limit=500", message = "no page cap on $table")
        }
    }

    @Test
    fun `a reference read without a cursor pulls from the beginning`() = runTest {
        source().forms("11111111-1111-1111-1111-111111111111")

        assertFalse(last.url.toString().contains("updated_at=gt"))
        assertContains(last.url.toString(), "order=updated_at.asc")
    }

    @Test
    fun `asking for versions of no forms makes no request`() = runTest {
        val remote = source()
        remote.formVersions(emptyList())
        remote.translations(emptyList())

        assertTrue(requests.isEmpty())
    }

    @Test
    fun `the server's id and updated_at come back from a push`() = runTest {
        val remote = source(
            """
            [{"id":"99999999-9999-9999-9999-999999999999",
              "study_id":"11111111-1111-1111-1111-111111111111",
              "form_version_id":"33333333-3333-3333-3333-333333333332",
              "participant_id":null,
              "collected_by":"55555555-5555-5555-5555-555555555551",
              "client_id":"cccccccc-0000-0000-0000-000000000001",
              "collected_at":"2026-07-01T09:30:00+00:00",
              "data":{"body_mass":268.0},
              "locked_at":null,
              "updated_at":"2026-07-01T09:31:02.456789+00:00",
              "deleted_at":null}]
            """.trimIndent(),
        )

        val stored = remote.push(listOf(submission())).single()

        assertEquals("99999999-9999-9999-9999-999999999999", stored.id)
        assertEquals("2026-07-01T09:31:02.456789+00:00", stored.updatedAt)
    }
}
