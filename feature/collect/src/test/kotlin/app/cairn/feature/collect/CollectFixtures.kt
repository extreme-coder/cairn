package app.cairn.feature.collect

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import app.cairn.core.database.CairnDatabase
import app.cairn.core.database.entity.FormEntity
import app.cairn.core.database.entity.FormVersionEntity
import app.cairn.core.database.entity.ParticipantEntity
import app.cairn.core.database.entity.StudyEntity
import app.cairn.core.database.entity.StudyMemberEntity
import app.cairn.core.database.entity.SubmissionEntity
import app.cairn.core.model.StudyRole
import app.cairn.core.model.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.ZoneId
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

internal object Ids {
    const val KLUANE = "11111111-1111-1111-1111-111111111111"
    const val PEEL = "11111111-1111-1111-1111-111111111112"
    const val BASELINE = "22222222-2222-2222-2222-222222222221"
    const val TRAP = "22222222-2222-2222-2222-222222222222"
    const val FUTURE = "22222222-2222-2222-2222-222222222223"
    const val BASELINE_V3 = "33333333-3333-3333-3333-333333333331"
    const val TRAP_V5 = "33333333-3333-3333-3333-333333333332"
    const val FUTURE_V1 = "33333333-3333-3333-3333-333333333333"
    const val PEEL_FORM = "22222222-2222-2222-2222-222222222224"
    const val PEEL_V1 = "33333333-3333-3333-3333-333333333334"
    const val KL_0148 = "44444444-4444-4444-4444-444444444448"
    const val ADAKU = "55555555-5555-5555-5555-555555555551"
    const val TOMAS = "55555555-5555-5555-5555-555555555552"
}

/** 09:00 UTC, which is 09:00 in [UTC] — the zone every test formats in. */
internal val T0: Instant = Instant.parse("2026-08-12T09:00:00Z")

internal val UTC: ZoneId = ZoneId.of("UTC")

internal fun at(minutes: Int): Instant = T0 + minutes.minutes

internal val twoFieldSchema: JsonObject = buildJsonObject {
    putJsonArray("fields") {
        addJsonObject {
            put("key", "body_mass")
            put("type", "number")
            put("label", "Body mass")
        }
        addJsonObject {
            put("key", "notes")
            put("type", "long_text")
            put("label", "Notes")
        }
    }
}

/** A field type published by a newer server than this build understands. */
internal val futureSchema: JsonObject = buildJsonObject {
    putJsonArray("fields") {
        addJsonObject {
            put("key", "call_recording")
            put("type", "audio")
            put("label", "Call recording")
        }
    }
}

/**
 * Keeps a subscriber alive for the test's lifetime and hands the flow back.
 *
 * These are `WhileSubscribed` flows: cold until something collects them, exactly
 * as they are cold until a screen is on top of the stack. Without a standing
 * subscriber a test reads the initial value and passes for the wrong reason,
 * which is the failure mode the wiki keeps warning about.
 */
internal fun <T> TestScope.watching(state: StateFlow<T>): StateFlow<T> {
    backgroundScope.launch { state.collect {} }
    return state
}

/** Waits for the state a test is about, rather than assuming it has arrived. */
internal suspend fun <T> StateFlow<T>.awaiting(predicate: (T) -> Boolean): T = first(predicate)

internal fun testDatabase(): CairnDatabase =
    Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        CairnDatabase::class.java,
    )
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Unconfined)
        .build()

/**
 * One study a collector works in, with three forms: an ordinary one, one whose
 * versions have not arrived, and one this build cannot render.
 */
internal suspend fun CairnDatabase.seedKluane() {
    studies().upsert(
        listOf(
            StudyEntity(
                id = Ids.KLUANE,
                name = "Kluane ground squirrel survey",
                createdBy = Ids.TOMAS,
                createdAt = T0,
            ),
        ),
    )
    members().upsert(
        listOf(
            StudyMemberEntity(
                studyId = Ids.KLUANE,
                userId = Ids.ADAKU,
                role = StudyRole.COLLECTOR,
                addedAt = T0,
            ),
        ),
    )
    forms().upsertForms(
        listOf(
            FormEntity(Ids.BASELINE, Ids.KLUANE, "baseline_intake", T0),
            FormEntity(Ids.TRAP, Ids.KLUANE, "trap_check", T0),
            FormEntity(Ids.FUTURE, Ids.KLUANE, "call_survey", T0),
        ),
    )
    forms().upsertVersions(
        listOf(
            FormVersionEntity(Ids.BASELINE_V3, Ids.BASELINE, 3, twoFieldSchema, T0, T0),
            FormVersionEntity(Ids.FUTURE_V1, Ids.FUTURE, 1, futureSchema, T0, T0),
        ),
    )
    participants().upsert(listOf(ParticipantEntity(Ids.KL_0148, Ids.KLUANE, "KL-0148", T0)))
}

/** A second study, so every study-scoped query has to actually filter. */
internal suspend fun CairnDatabase.seedPeel() {
    studies().upsert(
        listOf(
            StudyEntity(
                id = Ids.PEEL,
                name = "Peel watershed water quality",
                createdBy = Ids.TOMAS,
                createdAt = T0,
            ),
        ),
    )
    members().upsert(
        listOf(
            StudyMemberEntity(Ids.PEEL, Ids.ADAKU, StudyRole.VIEWER, T0),
        ),
    )
    forms().upsertForms(listOf(FormEntity(Ids.PEEL_FORM, Ids.PEEL, "water_sample", T0)))
    forms().upsertVersions(
        listOf(FormVersionEntity(Ids.PEEL_V1, Ids.PEEL_FORM, 1, twoFieldSchema, T0, T0)),
    )
}

internal fun submission(
    clientId: String,
    collectedBy: String = Ids.ADAKU,
    studyId: String = Ids.KLUANE,
    formVersionId: String = Ids.BASELINE_V3,
    participantId: String? = Ids.KL_0148,
    collectedAt: Instant = at(14),
    syncState: SyncState = SyncState.QUEUED,
    deletedAt: Instant? = null,
) = SubmissionEntity(
    collectedBy = collectedBy,
    clientId = clientId,
    studyId = studyId,
    formVersionId = formVersionId,
    participantId = participantId,
    collectedAt = collectedAt,
    data = buildJsonObject { put("body_mass", 268.0) },
    updatedAt = collectedAt,
    deletedAt = deletedAt,
    syncState = syncState,
    pendingSince = collectedAt,
)
