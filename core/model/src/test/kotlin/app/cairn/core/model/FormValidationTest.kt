package app.cairn.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val baselineIntake = FormSchema(
    fields = listOf(
        FieldSpec(
            key = "participant_code",
            type = FieldType.TEXT,
            label = "Participant code",
            help = "Study code only. Do not enter names.",
            required = true,
            maxLength = 16,
        ),
        FieldSpec(
            key = "trap_station",
            type = FieldType.SINGLE_SELECT,
            label = "Trap station",
            required = true,
            options = listOf(
                FieldOption("station_4", "Station 4"),
                FieldOption("station_5", "Station 5"),
            ),
        ),
        FieldSpec(
            key = "body_mass_g",
            type = FieldType.NUMBER,
            label = "Body mass",
            required = true,
            unit = "g",
            min = 90.0,
            max = 400.0,
        ),
        FieldSpec(
            key = "observations",
            type = FieldType.MULTI_SELECT,
            label = "Observations",
            options = listOf(
                FieldOption("ear_tag", "Ear tag present"),
                FieldOption("injury", "Visible injury"),
                FieldOption("lactating", "Lactating"),
            ),
        ),
        FieldSpec(
            key = "notes",
            type = FieldType.LONG_TEXT,
            label = "Notes",
            maxLength = 500,
        ),
        FieldSpec(
            key = "collected_on",
            type = FieldType.DATE,
            label = "Collected on",
        ),
    ),
)

private fun payload(raw: String): JsonObject = Json.decodeFromString(raw)

class FormValidationTest {

    @Test
    fun `a complete submission has no errors`() {
        val errors = baselineIntake.validate(
            payload(
                """
                {
                  "participant_code": "KL-0148",
                  "trap_station": "station_4",
                  "body_mass_g": 182,
                  "observations": ["ear_tag"],
                  "notes": "Released at the burrow.",
                  "collected_on": "2026-07-30"
                }
                """,
            ),
        )
        assertEquals(emptyList(), errors)
    }

    @Test
    fun `a required field that is absent is reported as missing`() {
        val errors = baselineIntake.validate(
            payload("""{"participant_code":"KL-0148","trap_station":"station_4"}"""),
        )
        val error = errors.single { it.key == "body_mass_g" }
        assertIs<MissingValue>(error)
        assertEquals("Body mass is required.", error.message())
    }

    @Test
    fun `a number above the maximum reports the range and the unit`() {
        val errors = baselineIntake.validate(
            payload(
                """{"participant_code":"KL-0148","trap_station":"station_4","body_mass_g":412}""",
            ),
        )
        val error = errors.single()
        assertIs<OutOfRange>(error)
        assertEquals("Body mass must be between 90 and 400 g.", error.message())
    }

    @Test
    fun `a number below the minimum is caught too`() {
        val errors = baselineIntake.validate(
            payload(
                """{"participant_code":"KL-0148","trap_station":"station_4","body_mass_g":12}""",
            ),
        )
        assertIs<OutOfRange>(errors.single())
    }

    @Test
    fun `text in a number field is a type error, not a range error`() {
        val errors = baselineIntake.validate(
            payload(
                """{"participant_code":"KL-0148","trap_station":"station_4","body_mass_g":"heavy"}""",
            ),
        )
        val error = errors.single()
        assertIs<WrongType>(error)
        assertEquals("Body mass must be a number.", error.message())
    }

    @Test
    fun `a blank required text field is missing rather than valid`() {
        val errors = baselineIntake.validate(
            payload("""{"participant_code":"   ","trap_station":"station_4","body_mass_g":182}"""),
        )
        val error = errors.single()
        assertIs<MissingValue>(error)
        assertEquals("participant_code", error.key)
    }

    @Test
    fun `a choice outside the option list is rejected`() {
        val errors = baselineIntake.validate(
            payload(
                """{"participant_code":"KL-0148","trap_station":"station_9","body_mass_g":182}""",
            ),
        )
        val error = errors.single()
        assertIs<UnknownOption>(error)
        assertEquals("station_9", error.value)
    }

    @Test
    fun `one stray value invalidates a multi select`() {
        val errors = baselineIntake.validate(
            payload(
                """
                {
                  "participant_code":"KL-0148",
                  "trap_station":"station_4",
                  "body_mass_g":182,
                  "observations":["ear_tag","moulting"]
                }
                """,
            ),
        )
        val error = errors.single()
        assertIs<UnknownOption>(error)
        assertEquals("moulting", error.value)
    }

    @Test
    fun `optional fields may be absent`() {
        val errors = baselineIntake.validate(
            payload("""{"participant_code":"KL-0148","trap_station":"station_4","body_mass_g":182}"""),
        )
        assertEquals(emptyList(), errors)
    }

    @Test
    fun `text longer than the limit reports the limit`() {
        val errors = baselineIntake.validate(
            payload(
                """
                {
                  "participant_code":"KL-0148",
                  "trap_station":"station_4",
                  "body_mass_g":182,
                  "notes":"${"x".repeat(501)}"
                }
                """,
            ),
        )
        val error = errors.single()
        assertIs<TooLong>(error)
        assertEquals("Notes must be 500 characters or fewer.", error.message())
    }

    @Test
    fun `a malformed date is a type error`() {
        val errors = baselineIntake.validate(
            payload(
                """
                {
                  "participant_code":"KL-0148",
                  "trap_station":"station_4",
                  "body_mass_g":182,
                  "collected_on":"30-07-2026"
                }
                """,
            ),
        )
        assertIs<WrongType>(errors.single())
    }

    @Test
    fun `keys the schema does not know about are ignored`() {
        val errors = baselineIntake.validate(
            payload(
                """
                {
                  "participant_code":"KL-0148",
                  "trap_station":"station_4",
                  "body_mass_g":182,
                  "burrow_depth_cm": 41
                }
                """,
            ),
        )
        assertEquals(emptyList(), errors)
    }

    @Test
    fun `every violation is reported, not just the first`() {
        val errors = baselineIntake.validate(payload("""{"body_mass_g":412}"""))
        assertEquals(3, errors.size)
        assertTrue(errors.any { it is MissingValue && it.key == "participant_code" })
        assertTrue(errors.any { it is MissingValue && it.key == "trap_station" })
        assertTrue(errors.any { it is OutOfRange && it.key == "body_mass_g" })
    }

    @Test
    fun `a schema survives a round trip through json`() {
        val encoded = Json.encodeToString(baselineIntake)
        assertEquals(baselineIntake, Json.decodeFromString<FormSchema>(encoded))
        assertTrue(encoded.contains("\"single_select\""), "field types serialise to snake case")
    }
}
