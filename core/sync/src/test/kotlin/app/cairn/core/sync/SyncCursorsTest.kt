package app.cairn.core.sync

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class SyncCursorsTest {

    @get:Rule
    val folder: TemporaryFolder = TemporaryFolder()

    /** Not created up front: DataStore writes the file itself and reads an existing empty one as corrupt. */
    private fun store(name: String = "cursors") =
        DataStoreSyncCursors(
            PreferenceDataStoreFactory.create { File(folder.root, "$name.preferences_pb") },
        )

    @Test
    fun `a cursor survives being written and read back`() = runTest {
        val cursors = store()
        cursors.write("submissions:s1", scope = "s1", cursor = ts(3, 654321))

        assertEquals(ts(3, 654321), cursors.read("submissions:s1", scope = "s1"))
    }

    @Test
    fun `an unwritten cursor reads as null rather than an empty string`() = runTest {
        assertNull(store().read("submissions:s1", scope = "s1"))
    }

    /**
     * The scope-widening trap, at the storage layer. `form_versions` is fetched
     * by an explicit id list, and a cursor taken under one list says nothing
     * about a longer one — the new form's versions are older than that cursor and
     * would never be requested again.
     */
    @Test
    fun `a cursor taken under a different scope reads as absent`() = runTest {
        val cursors = store()
        cursors.write("form_versions:s1", scope = scopeOf(listOf("form-a")), cursor = ts(5))

        assertNull(cursors.read("form_versions:s1", scope = scopeOf(listOf("form-a", "form-b"))))
    }

    @Test
    fun `the same scope in a different order is the same scope`() {
        assertEquals(
            scopeOf(listOf("form-a", "form-b")),
            scopeOf(listOf("form-b", "form-a")),
        )
    }

    @Test
    fun `a widened scope is a different fingerprint`() {
        assertNotEquals(
            scopeOf(listOf("form-a")),
            scopeOf(listOf("form-a", "form-b")),
        )
    }

    @Test
    fun `an empty scope is stable`() {
        assertEquals(scopeOf(emptyList()), scopeOf(emptySet()))
    }

    /** Sign-out. A cursor outliving its user would hide that user's rows from the next one. */
    @Test
    fun `clearing drops every cursor`() = runTest {
        val cursors = store()
        cursors.write("studies", scope = "all", cursor = ts(1))
        cursors.write("submissions:s1", scope = "s1", cursor = ts(2))

        cursors.clear()

        assertNull(cursors.read("studies", scope = "all"))
        assertNull(cursors.read("submissions:s1", scope = "s1"))
    }

    @Test
    fun `cursors for different tables do not collide`() = runTest {
        val cursors = store()
        cursors.write("forms:s1", scope = "s1", cursor = ts(1))
        cursors.write("participants:s1", scope = "s1", cursor = ts(2))

        assertEquals(ts(1), cursors.read("forms:s1", scope = "s1"))
        assertEquals(ts(2), cursors.read("participants:s1", scope = "s1"))
    }

    @Test
    fun `a rewritten cursor replaces rather than accumulates`() = runTest {
        val cursors = store()
        cursors.write("studies", scope = "all", cursor = ts(1))
        cursors.write("studies", scope = "all", cursor = ts(2))

        assertEquals(ts(2), cursors.read("studies", scope = "all"))
    }

    @Test
    fun `cursor keys are namespaced by study`() {
        assertNotEquals(SyncCursors.forms("s1"), SyncCursors.forms("s2"))
        assertNotEquals(SyncCursors.forms("s1"), SyncCursors.participants("s1"))
    }
}
