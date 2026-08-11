package app.cairn.feature.capture

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import app.cairn.core.database.CairnDatabase
import app.cairn.core.database.entity.FormEntity
import app.cairn.core.database.entity.FormVersionEntity
import app.cairn.core.database.entity.ParticipantEntity
import app.cairn.core.database.entity.StudyEntity
import app.cairn.core.database.toFormSchema
import app.cairn.core.model.FormSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

internal object Ids {
    const val STUDY = "11111111-1111-1111-1111-111111111111"
    const val FORM = "22222222-2222-2222-2222-222222222222"
    const val VERSION_1 = "33333333-3333-3333-3333-333333333331"
    const val VERSION_2 = "33333333-3333-3333-3333-333333333332"
    const val PARTICIPANT = "44444444-4444-4444-4444-444444444444"
    const val ADAKU = "55555555-5555-5555-5555-555555555551"
    const val TOMAS = "55555555-5555-5555-5555-555555555552"

    const val FUTURE_FORM = "66666666-6666-6666-6666-666666666666"
    const val FUTURE_VERSION = "66666666-6666-6666-6666-666666666667"
    const val DRAFT_FORM = "77777777-7777-7777-7777-777777777777"
    const val DRAFT_VERSION = "77777777-7777-7777-7777-777777777778"
}

internal val T0: Instant = Instant.parse("2026-07-01T09:00:00Z")

internal fun at(minutes: Int): Instant = T0 + minutes.minutes

internal val kestrelSchemaJson: JsonObject = buildJsonObject {
    putJsonArray("fields") {
        addJsonObject {
            put("key", "body_mass")
            put("type", "number")
            put("label", "Body mass")
            put("unit", "g")
            put("min", 90.0)
            put("max", 400.0)
            put("required", true)
        }
        addJsonObject {
            put("key", "ring")
            put("type", "text")
            put("label", "Ring code")
            put("maxLength", 8)
        }
        addJsonObject {
            put("key", "sex")
            put("type", "single_select")
            put("label", "Sex")
            put("required", true)
            putJsonArray("options") {
                addJsonObject {
                    put("value", "female")
                    put("label", "Female")
                }
                addJsonObject {
                    put("value", "male")
                    put("label", "Male")
                }
                addJsonObject {
                    put("value", "unknown")
                    put("label", "Unknown")
                }
            }
        }
        addJsonObject {
            put("key", "behaviours")
            put("type", "multi_select")
            put("label", "Behaviours")
            putJsonArray("options") {
                addJsonObject {
                    put("value", "foraging")
                    put("label", "Foraging")
                }
                addJsonObject {
                    put("value", "preening")
                    put("label", "Preening")
                }
                addJsonObject {
                    put("value", "flight")
                    put("label", "In flight")
                }
            }
        }
        addJsonObject {
            put("key", "observed_on")
            put("type", "date")
            put("label", "Observed on")
        }
        addJsonObject {
            put("key", "notes")
            put("type", "long_text")
            put("label", "Notes")
        }
    }
}

/**
 * A schema carrying a field type this build has never heard of, standing in for
 * a form published by a newer server than the app installed on this device.
 */
internal val futureSchemaJson: JsonObject = buildJsonObject {
    putJsonArray("fields") {
        addJsonObject {
            put("key", "call_recording")
            put("type", "audio")
            put("label", "Call recording")
        }
    }
}

internal val kestrelSchema: FormSchema = kestrelSchemaJson.toFormSchema()

internal fun state(
    clientId: String = "cccccccc-0000-0000-0000-000000000001",
    collectedBy: String = Ids.ADAKU,
    participantId: String? = Ids.PARTICIPANT,
): CaptureState = CaptureState(
    clientId = clientId,
    collectedBy = collectedBy,
    studyId = Ids.STUDY,
    formVersionId = Ids.VERSION_1,
    schema = kestrelSchema,
    openedAt = at(30),
    participantId = participantId,
)

/** The smallest set of answers that validates, so a test can vary one thing. */
internal fun CaptureState.filledIn(): CaptureState =
    setNumber("body_mass", "268").setChoice("sex", "female")

internal fun testDatabase(): CairnDatabase =
    Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        CairnDatabase::class.java,
    )
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Unconfined)
        .build()

internal suspend fun CairnDatabase.seedKestrelStudy() {
    studies().upsert(
        listOf(
            StudyEntity(
                id = Ids.STUDY,
                name = "Kestrel breeding survey",
                createdBy = Ids.ADAKU,
                createdAt = T0,
            ),
        ),
    )
    forms().upsertForms(
        listOf(
            FormEntity(id = Ids.FORM, studyId = Ids.STUDY, code = "baseline_intake", createdAt = T0),
            FormEntity(id = Ids.FUTURE_FORM, studyId = Ids.STUDY, code = "call_survey", createdAt = T0),
            FormEntity(id = Ids.DRAFT_FORM, studyId = Ids.STUDY, code = "nest_check", createdAt = T0),
        ),
    )
    forms().upsertVersions(
        listOf(
            formVersion(id = Ids.VERSION_1, version = 1),
            formVersion(id = Ids.VERSION_2, version = 2),
            formVersion(
                id = Ids.FUTURE_VERSION,
                formId = Ids.FUTURE_FORM,
                version = 1,
                schema = futureSchemaJson,
            ),
            formVersion(
                id = Ids.DRAFT_VERSION,
                formId = Ids.DRAFT_FORM,
                version = 1,
                publishedAt = null,
            ),
        ),
    )
    participants().upsert(
        listOf(
            ParticipantEntity(
                id = Ids.PARTICIPANT,
                studyId = Ids.STUDY,
                code = "K-014",
                createdAt = T0,
            ),
        ),
    )
}

internal fun formVersion(
    id: String,
    formId: String = Ids.FORM,
    version: Int,
    publishedAt: Instant? = T0,
    schema: JsonObject = kestrelSchemaJson,
) = FormVersionEntity(
    id = id,
    formId = formId,
    version = version,
    schema = schema,
    publishedAt = publishedAt,
    createdAt = T0,
)

internal fun choices(vararg values: String) = buildJsonArray { values.forEach { add(it) } }
