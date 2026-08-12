package app.cairn.core.session

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * The store's behaviour, over a real DataStore file and a stand-in cipher.
 *
 * The cipher is a stand-in because the real one needs an Android Keystore, and
 * Robolectric has no shadow for it — that path is verified on a device instead.
 * What can be pinned here is everything around it: that nothing readable reaches
 * the disk, that a session outlives the process that wrote it, and that bytes
 * which will not decrypt are dropped rather than thrown.
 */
@RunWith(RobolectricTestRunner::class)
class EncryptedSessionStoreTest {

    @get:Rule
    val folder: TemporaryFolder = TemporaryFolder()

    /** Reversible, and obviously not encryption. It only has to not be the plaintext. */
    private class ReversingCipher(private val readable: Boolean = true) : TokenCipher {
        override fun encrypt(plaintext: String): String = plaintext.reversed()

        override fun decrypt(ciphertext: String): String? =
            if (readable) ciphertext.reversed() else null
    }

    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun release() {
        scopes.forEach { it.cancel() }
    }

    private fun store(file: File, cipher: TokenCipher = ReversingCipher()): EncryptedSessionStore {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scopes += scope
        return EncryptedSessionStore(
            PreferenceDataStoreFactory.create(scope = scope) { file },
            cipher,
        )
    }

    /**
     * DataStore refuses a second instance over a live file, so the first one is
     * released first. That is also what a restart is.
     */
    private fun restart(file: File, cipher: TokenCipher = ReversingCipher()): EncryptedSessionStore {
        release()
        scopes.clear()
        return store(file, cipher)
    }

    private fun file(name: String): File = folder.newFile("$name.preferences_pb").apply { delete() }

    private val session = """{"access_token":"jwt","refresh_token":"refresh"}"""

    @Test
    fun `a session written comes back`() = runTest {
        val store = store(file("written"))

        store.write(session)

        assertEquals(session, store.read())
    }

    @Test
    fun `a session survives the process it was written in`() = runTest {
        val file = file("restart")
        store(file).write(session)

        assertEquals(session, restart(file).read())
    }

    @Test
    fun `the token is not on disk in the clear`() = runTest {
        val file = file("plaintext")
        store(file).write(session)

        val bytes = file.readBytes().toString(Charsets.ISO_8859_1)

        assertFalse(bytes.contains("refresh"))
        assertFalse(bytes.contains("access_token"))
    }

    @Test
    fun `nothing stored reads as no session`() = runTest {
        assertNull(store(file("empty")).read())
    }

    @Test
    fun `clearing empties the file`() = runTest {
        val file = file("cleared")
        val store = store(file)
        store.write(session)

        store.clear()

        assertNull(store.read())
        assertNull(restart(file).read())
    }

    /**
     * What a keystore key lost to a factory reset, or a restore onto another
     * device, looks like from here. It has to be a sign-in prompt rather than a
     * crash on launch, and the unreadable bytes should not be left lying around
     * to fail again on the next start.
     */
    @Test
    fun `bytes that will not decrypt are dropped rather than thrown`() = runTest {
        val file = file("unreadable")
        store(file).write(session)

        assertNull(restart(file, ReversingCipher(readable = false)).read())
        assertNull(restart(file).read())
    }
}
