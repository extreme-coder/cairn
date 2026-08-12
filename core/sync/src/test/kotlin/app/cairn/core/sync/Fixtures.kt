package app.cairn.core.sync

import app.cairn.core.database.entity.SubmissionEntity
import app.cairn.core.model.StudyRole
import app.cairn.core.model.SyncState
import app.cairn.core.network.FormDto
import app.cairn.core.network.FormTranslationDto
import app.cairn.core.network.FormVersionDto
import app.cairn.core.network.ParticipantDto
import app.cairn.core.network.StudyDto
import app.cairn.core.network.StudyMemberDto
import app.cairn.core.network.SubmissionDto
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

internal object Ids {
    const val STUDY = "11111111-1111-1111-1111-111111111111"
    const val OTHER_STUDY = "aaaa1111-1111-1111-1111-111111111111"
    const val FORM = "22222222-2222-2222-2222-222222222222"
    const val FORM_2 = "22222222-2222-2222-2222-222222222223"
    const val VERSION_1 = "33333333-3333-3333-3333-333333333331"
    const val VERSION_2 = "33333333-3333-3333-3333-333333333332"
    const val VERSION_3 = "33333333-3333-3333-3333-333333333333"
    const val PARTICIPANT = "44444444-4444-4444-4444-444444444444"
    const val ADAKU = "55555555-5555-5555-5555-555555555551"
    const val TOMAS = "55555555-5555-5555-5555-555555555552"
}

/**
 * A wire timestamp with Postgres's microsecond tail.
 *
 * The six fractional digits are the point, not decoration: they are what the
 * cursor compares and what Room's millisecond columns cannot hold. Fixed offset
 * and fixed width so these sort the same lexicographically as chronologically,
 * which is how [FakeRemote] pages.
 */
internal fun ts(minute: Int, micros: Int = 0): String =
    "2026-07-01T09:%02d:00.%06d+00:00".format(minute, micros)

internal fun studyDto(
    id: String = Ids.STUDY,
    name: String = "Kestrel breeding survey",
    updatedAt: String = ts(0),
) = StudyDto(
    id = id,
    name = name,
    createdBy = Ids.ADAKU,
    createdAt = ts(0),
    updatedAt = updatedAt,
)

internal fun memberDto(
    studyId: String = Ids.STUDY,
    userId: String = Ids.ADAKU,
    role: StudyRole = StudyRole.COLLECTOR,
    updatedAt: String = ts(0),
) = StudyMemberDto(
    studyId = studyId,
    userId = userId,
    role = role,
    addedAt = ts(0),
    updatedAt = updatedAt,
)

internal fun formDto(
    id: String = Ids.FORM,
    studyId: String = Ids.STUDY,
    code: String = "baseline_intake",
    updatedAt: String = ts(1),
) = FormDto(
    id = id,
    studyId = studyId,
    code = code,
    createdAt = ts(1),
    updatedAt = updatedAt,
)

internal val bodyMassSchema: JsonObject = buildJsonObject {
    put(
        "fields",
        buildJsonArray {
            add(
                buildJsonObject {
                    put("key", "body_mass")
                    put("type", "number")
                    put("label", "Body mass")
                    put("unit", "g")
                    put("min", 90.0)
                    put("max", 400.0)
                    put("required", true)
                },
            )
        },
    )
}

internal fun versionDto(
    id: String = Ids.VERSION_1,
    formId: String = Ids.FORM,
    version: Int = 1,
    publishedAt: String? = ts(2),
    schema: JsonObject = bodyMassSchema,
    updatedAt: String = ts(2),
) = FormVersionDto(
    id = id,
    formId = formId,
    version = version,
    schema = schema,
    publishedAt = publishedAt,
    createdAt = ts(2),
    updatedAt = updatedAt,
)

internal fun participantDto(
    id: String = Ids.PARTICIPANT,
    studyId: String = Ids.STUDY,
    code: String = "K-014",
    updatedAt: String = ts(3),
) = ParticipantDto(
    id = id,
    studyId = studyId,
    code = code,
    createdAt = ts(3),
    updatedAt = updatedAt,
)

internal fun translationDto(
    id: String = "translation-fr",
    formVersionId: String = Ids.VERSION_1,
    lang: String = "fr",
    reviewedAt: String? = ts(4),
    updatedAt: String = ts(4),
) = FormTranslationDto(
    id = id,
    formVersionId = formVersionId,
    lang = lang,
    labels = buildJsonObject { put("body_mass", "Masse corporelle") },
    engine = "libretranslate",
    reviewedBy = if (reviewedAt == null) null else Ids.TOMAS,
    reviewedAt = reviewedAt,
    createdAt = ts(4),
    updatedAt = updatedAt,
)

internal fun submissionDto(
    id: String? = "server-existing",
    clientId: String = "cccccccc-0000-0000-0000-000000000001",
    collectedBy: String = Ids.ADAKU,
    studyId: String = Ids.STUDY,
    formVersionId: String = Ids.VERSION_1,
    participantId: String? = Ids.PARTICIPANT,
    mass: Double = 268.0,
    lockedAt: String? = null,
    deletedAt: String? = null,
    updatedAt: String? = ts(5),
) = SubmissionDto(
    id = id,
    studyId = studyId,
    formVersionId = formVersionId,
    participantId = participantId,
    collectedBy = collectedBy,
    clientId = clientId,
    collectedAt = ts(5),
    data = buildJsonObject { put("body_mass", mass) },
    lockedAt = lockedAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

/**
 * A row as `:feature:capture` leaves it: no server id, device clock, queued.
 *
 * [updatedAt] defaults to a time far in the future on purpose. The device clock
 * is not trustworthy, and several tests turn on the server's value replacing
 * this one rather than being compared against it.
 */
internal fun queued(
    clientId: String = "cccccccc-0000-0000-0000-000000000001",
    collectedBy: String = Ids.ADAKU,
    studyId: String = Ids.STUDY,
    formVersionId: String = Ids.VERSION_1,
    participantId: String? = Ids.PARTICIPANT,
    mass: Double = 268.0,
    syncState: SyncState = SyncState.QUEUED,
    pendingSince: Instant = Instant.parse("2026-07-01T09:06:00Z"),
    updatedAt: Instant = Instant.parse("2099-01-01T00:00:00Z"),
) = SubmissionEntity(
    collectedBy = collectedBy,
    clientId = clientId,
    id = null,
    studyId = studyId,
    formVersionId = formVersionId,
    participantId = participantId,
    collectedAt = Instant.parse("2026-07-01T09:05:00Z"),
    data = buildJsonObject { put("body_mass", mass) },
    lockedAt = null,
    updatedAt = updatedAt,
    deletedAt = null,
    syncState = syncState,
    pendingSince = pendingSince,
)

/** One study, one form, one published version, one participant — the minimum a capture needs. */
internal fun FakeRemote.seedStudy() {
    studies += studyDto()
    members += memberDto()
    forms += formDto()
    versions += versionDto()
    participants += participantDto()
}
