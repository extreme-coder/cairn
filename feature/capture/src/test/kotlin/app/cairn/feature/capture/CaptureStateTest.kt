package app.cairn.feature.capture

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureStateTest {

    @Test
    fun `a field left empty is absent from the payload rather than an empty string`() {
        val filled = state().filledIn().setText("ring", "EX-4471")
        val cleared = filled.setText("ring", "  ")

        assertTrue(filled.payload.containsKey("ring"))
        assertFalse(cleared.payload.containsKey("ring"))
    }

    @Test
    fun `the payload follows schema order however the form was filled`() {
        val filled = state()
            .setText("notes", "Recaptured at dusk.")
            .setChoice("sex", "female")
            .setText("ring", "EX-4471")
            .setNumber("body_mass", "268")

        assertEquals(listOf("body_mass", "ring", "sex", "notes"), filled.payload.keys.toList())
    }

    @Test
    fun `a key the schema does not declare never reaches the payload`() {
        val filled = state().filledIn().setText("weather", "overcast")

        assertTrue(filled.values.containsKey("weather"))
        assertFalse(filled.payload.containsKey("weather"))
    }

    @Test
    fun `text that is not a number is kept verbatim and reported as the wrong type`() {
        val typing = state().setChoice("sex", "female").setNumber("body_mass", "268 g")

        assertEquals(JsonPrimitive("268 g"), typing.payload["body_mass"])
        assertEquals("Body mass must be a number.", typing.errors.single().message())
    }

    @Test
    fun `a number still being typed is a number, and says nothing until a save is attempted`() {
        val typing = state().setChoice("sex", "female").setNumber("body_mass", "26.")

        assertEquals(JsonPrimitive(26.0), typing.payload["body_mass"])
        assertTrue(typing.visibleErrors.isEmpty())
        assertEquals(
            "Body mass must be between 90 and 400 g.",
            typing.attemptSave().errorFor("body_mass")?.message(),
        )
    }

    @Test
    fun `a number that parses is stored as a number`() {
        val filled = state().filledIn()

        assertEquals(JsonPrimitive(268.0), filled.payload["body_mass"])
        assertTrue(filled.isValid)
    }

    @Test
    fun `errors stay hidden until the collector tries to save`() {
        val empty = state()

        assertFalse(empty.isValid)
        assertTrue(empty.visibleErrors.isEmpty())
        assertNull(empty.errorFor("body_mass"))

        val attempted = empty.attemptSave()

        assertEquals(2, attempted.visibleErrors.size)
        assertEquals("Body mass is required.", attempted.errorFor("body_mass")?.message())
        assertEquals("Sex is required.", attempted.errorFor("sex")?.message())
    }

    @Test
    fun `fixing a field clears its error without a second save attempt`() {
        val rejected = state().setChoice("sex", "female").setNumber("body_mass", "512").attemptSave()

        assertEquals(
            "Body mass must be between 90 and 400 g.",
            rejected.errorFor("body_mass")?.message(),
        )

        val fixed = rejected.setNumber("body_mass", "268")

        assertNull(fixed.errorFor("body_mass"))
        assertTrue(fixed.visibleErrors.isEmpty())
    }

    @Test
    fun `clearing a required field brings its error back`() {
        val emptied = state().filledIn().attemptSave().clear("body_mass")

        assertEquals("Body mass is required.", emptied.errorFor("body_mass")?.message())
    }

    @Test
    fun `error text is derived from the field spec, not written on the screen`() {
        val tooLong = state().filledIn().setText("ring", "EX-44710099").attemptSave()

        assertEquals(
            "Ring code must be 8 characters or fewer.",
            tooLong.errorFor("ring")?.message(),
        )
    }

    @Test
    fun `toggling an option twice removes it`() {
        val on = state().filledIn().toggleChoice("behaviours", "preening")
        val off = on.toggleChoice("behaviours", "preening")

        assertEquals(choices("preening"), on.payload["behaviours"])
        assertFalse(off.payload.containsKey("behaviours"))
    }

    @Test
    fun `chosen options follow the order the schema lists them, not the order they were tapped`() {
        val tapped = state()
            .filledIn()
            .toggleChoice("behaviours", "flight")
            .toggleChoice("behaviours", "foraging")

        assertEquals(choices("foraging", "flight"), tapped.payload["behaviours"])
    }

    @Test
    fun `an unknown option is refused by validation rather than silently dropped`() {
        val stray = state().filledIn().setChoice("sex", "juvenile").attemptSave()

        assertEquals(
            "Sex has a value that is not an option in this form version.",
            stray.errorFor("sex")?.message(),
        )
    }

    @Test
    fun `a date is stored as an ISO string`() {
        val dated = state().filledIn().setDate("observed_on", LocalDate(2026, 6, 14))

        assertEquals(JsonPrimitive("2026-06-14"), dated.payload["observed_on"])
        assertTrue(dated.isValid)
    }
}
