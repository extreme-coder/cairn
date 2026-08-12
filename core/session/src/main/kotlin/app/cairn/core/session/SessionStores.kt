package app.cairn.core.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import app.cairn.core.network.SessionStore
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Turns a session into bytes that are useless to anyone reading the disk.
 *
 * Separated from the store so the store's behaviour can be tested on the JVM.
 * The real implementation needs hardware that only a device has.
 */
public interface TokenCipher {

    public fun encrypt(plaintext: String): String

    /** Null when the bytes cannot be read back — a lost key, a truncated write. */
    public fun decrypt(ciphertext: String): String?
}

/**
 * AES-256-GCM with the key held in the Android Keystore.
 *
 * `EncryptedSharedPreferences` would have been the obvious answer and is
 * deprecated, so this does what it did: a symmetric key that never leaves the
 * keystore — on most devices never leaves the secure element — and a ciphertext
 * that is just bytes in an ordinary preferences file. Nothing here is novel
 * cryptography; the point is that the key is not in the APK and not on the disk.
 *
 * Two deliberate choices:
 *
 * - **No user authentication requirement.** Requiring the lock screen would be
 *   stronger and would also stop a background sync at 03:00 from reading its own
 *   token. Field devices are shared and often unattended; a sync that only runs
 *   while someone is looking at the phone is not a sync.
 * - **A random IV per encryption**, prepended to the ciphertext. GCM with a
 *   reused IV leaks the key, and this key encrypts the same session repeatedly
 *   as it refreshes, which is exactly the pattern that punishes a fixed IV.
 */
public class KeystoreTokenCipher(
    private val alias: String = ALIAS,
) : TokenCipher {

    override fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val body = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(cipher.iv + body)
    }

    override fun decrypt(ciphertext: String): String? = runCatching {
        val bytes = Base64.getDecoder().decode(ciphertext)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES),
        )
        String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
    }.getOrNull()

    /**
     * Synchronised because two threads generating at once would leave the second
     * key in the keystore and the first one's ciphertext permanently unreadable.
     * Sync runs on a worker while the UI signs in on another; that is a real
     * pair of threads, not a theoretical one.
     */
    @Synchronized
    private fun key(): SecretKey {
        val keystore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keystore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val ALIAS = "cairn.session"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
        const val IV_BYTES = 12
    }
}

/**
 * The session, encrypted, in a preferences file of its own.
 *
 * Its own file rather than a corner of the sync cursors': the two have different
 * lifetimes and different consequences if they leak, and a sign-out clears both
 * for its own reasons.
 */
public class EncryptedSessionStore(
    private val store: DataStore<Preferences>,
    private val cipher: TokenCipher,
) : SessionStore {

    /**
     * Bytes that will not decrypt are dropped rather than returned or thrown.
     * That is what a keystore key lost to a factory reset or a cloud restore
     * looks like, and the recovery is to sign in again, not to crash on launch.
     */
    override suspend fun read(): String? {
        val stored = store.data.first()[SESSION] ?: return null
        return cipher.decrypt(stored) ?: run {
            clear()
            null
        }
    }

    override suspend fun write(value: String) {
        val sealed = cipher.encrypt(value)
        store.edit { it[SESSION] = sealed }
    }

    override suspend fun clear() {
        store.edit { it.clear() }
    }

    private companion object {
        val SESSION = stringPreferencesKey("session")
    }
}

/** The app's session store. Keeps DataStore and the keystore inside this module. */
public fun cairnSessionStore(context: Context): SessionStore = EncryptedSessionStore(
    PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("session") },
    KeystoreTokenCipher(),
)
