package app.cairn

import app.cairn.core.database.CairnDatabase
import app.cairn.core.database.entity.FormEntity
import app.cairn.core.database.entity.FormVersionEntity
import app.cairn.core.database.entity.ParticipantEntity
import app.cairn.core.database.entity.StudyEntity
import app.cairn.core.database.entity.StudyMemberEntity
import app.cairn.core.model.StudyRole
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.time.Instant

/**
 * Puts one study, one published form and one participant on the device.
 *
 * **This stands in for the sync pull, which does not exist yet.** Reference data
 * is server-owned and read-only on the device, so when `:core:sync` lands this
 * file is deleted rather than adapted. Nothing in the app reads these ids: the
 * screen renders whatever schema it is handed.
 */
internal object DevSeed {

    const val STUDY_ID = "11111111-1111-1111-1111-111111111111"
    const val FORM_ID = "22222222-2222-2222-2222-222222222222"
    const val COLLECTOR_ID = "55555555-5555-5555-5555-555555555551"

    private const val VERSION_2_ID = "33333333-3333-3333-3333-333333333332"
    private const val VERSION_3_ID = "33333333-3333-3333-3333-333333333333"
    private const val PARTICIPANT_ID = "44444444-4444-4444-4444-444444444444"

    suspend fun applyIfEmpty(database: CairnDatabase) {
        if (database.studies().observeAll().first().isNotEmpty()) return

        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())

        database.studies().upsert(
            listOf(
                StudyEntity(
                    id = STUDY_ID,
                    name = "Kestrel breeding survey",
                    createdBy = COLLECTOR_ID,
                    createdAt = now,
                ),
            ),
        )
        database.members().upsert(
            listOf(
                StudyMemberEntity(
                    studyId = STUDY_ID,
                    userId = COLLECTOR_ID,
                    role = StudyRole.COLLECTOR,
                    addedAt = now,
                ),
            ),
        )
        database.forms().upsertForms(
            listOf(
                FormEntity(
                    id = FORM_ID,
                    studyId = STUDY_ID,
                    code = "baseline_intake",
                    createdAt = now,
                ),
            ),
        )
        database.forms().upsertVersions(
            listOf(
                FormVersionEntity(
                    id = VERSION_2_ID,
                    formId = FORM_ID,
                    version = 2,
                    schema = supersededSchema,
                    publishedAt = now,
                    createdAt = now,
                ),
                FormVersionEntity(
                    id = VERSION_3_ID,
                    formId = FORM_ID,
                    version = 3,
                    schema = currentSchema,
                    publishedAt = now,
                    createdAt = now,
                ),
            ),
        )
        database.participants().upsert(
            listOf(
                ParticipantEntity(
                    id = PARTICIPANT_ID,
                    studyId = STUDY_ID,
                    code = "KL-0148",
                    createdAt = now,
                ),
            ),
        )
    }

    /**
     * v2 is seeded alongside v3 so that "open the current version" is a real
     * choice the app makes rather than the only row present.
     */
    private val supersededSchema: JsonObject = buildJsonObject {
        putJsonArray("fields") {
            addJsonObject {
                put("key", "body_mass")
                put("type", "number")
                put("label", "Body mass")
                put("unit", "g")
                put("required", true)
            }
        }
    }

    private val currentSchema: JsonObject = buildJsonObject {
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
                put("help", "Study code only. Do not enter names.")
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
                        put("label", "Not recorded")
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
}
