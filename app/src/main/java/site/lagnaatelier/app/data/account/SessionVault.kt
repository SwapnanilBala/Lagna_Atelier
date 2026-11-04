package site.lagnaatelier.app.data.account

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/*
 * The signed-in session, kept on the phone (SIGN_IN.md 2.3).
 *
 * The session token is encrypted with an AES-GCM key that lives in the
 * Android Keystore and never leaves it; only the ciphertext and its IV are
 * written, to a private SharedPreferences file. The file is excluded from
 * backup and device transfer (res/xml), and a Keystore key is not backed up
 * either, so a copy restored elsewhere could not be read. Anything that will
 * not decrypt is treated as signed out and deleted.
 *
 * Next to the token: the name and address the Account tab shows, and whether
 * the reader agreed to keep this phone's charts on the account. The Google
 * id_token is never kept; it has done its job once exchanged.
 */

data class StoredSession(
    val token: String,
    val expiresAtMillis: Long,
    val email: String,
    val displayName: String?,
    /** The reader agreed to keep this phone's charts on this account. */
    val syncCharts: Boolean = false,
)

/** Encrypts and decrypts the vault's one value; a fake in the tests. */
interface SessionCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(sealed: ByteArray): ByteArray
}

/** Where the sealed value is kept; memory in the tests. */
interface VaultStorage {
    fun read(): String?
    fun write(value: String)
    fun clear()
}

class SessionVault(private val storage: VaultStorage, private val cipher: SessionCipher) {
    constructor(context: Context) : this(PreferencesVaultStorage(context), KeystoreSessionCipher())

    /** The stored session, or null when there is none, it has expired, or it will not decrypt. */
    fun read(now: Long = System.currentTimeMillis()): StoredSession? {
        val sealed = storage.read() ?: return null
        val session = try {
            SessionCodec.decode(String(cipher.decrypt(Base64.getDecoder().decode(sealed)), Charsets.UTF_8))
        } catch (e: Exception) {
            null
        }
        if (session == null || session.expiresAtMillis <= now) {
            clear()
            return null
        }
        return session
    }

    fun write(session: StoredSession) {
        val sealed = cipher.encrypt(SessionCodec.encode(session).toByteArray(Charsets.UTF_8))
        storage.write(Base64.getEncoder().encodeToString(sealed))
    }

    fun clear() {
        try {
            storage.clear()
        } catch (e: Exception) {
            // Nothing left to protect if the file cannot even be cleared.
        }
    }
}

internal object SessionCodec {
    fun encode(session: StoredSession): String = JSONObject().apply {
        put("token", session.token)
        put("expiresAt", session.expiresAtMillis)
        put("email", session.email)
        session.displayName?.let { put("displayName", it) }
        put("syncCharts", session.syncCharts)
    }.toString()

    fun decode(raw: String): StoredSession? {
        val json = JSONObject(raw)
        val token = (json.opt("token") as? String)?.takeIf { it.isNotBlank() } ?: return null
        val expires = (json.opt("expiresAt") as? Number)?.toLong() ?: return null
        val email = (json.opt("email") as? String)?.takeIf { it.isNotBlank() } ?: return null
        return StoredSession(
            token = token,
            expiresAtMillis = expires,
            email = email,
            displayName = json.opt("displayName") as? String,
            syncCharts = json.opt("syncCharts") == true,
        )
    }
}

private class PreferencesVaultStorage(context: Context) : VaultStorage {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun read(): String? = prefs.getString(KEY, null)

    /* commit, not apply: a sign-in the phone forgets on a crash would leave a
       live session on the server with nobody holding it. */
    override fun write(value: String) {
        prefs.edit().putString(KEY, value).commit()
    }

    override fun clear() {
        prefs.edit().clear().commit()
    }

    companion object {
        const val FILE = "lagna_session"
        const val KEY = "sealed"
    }
}

/**
 * AES-256-GCM with a key generated inside the Android Keystore. No user
 * authentication is asked for: the token must be readable to send, and the
 * phone's own lock is what guards the app.
 */
private class KeystoreSessionCipher : SessionCipher {
    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    /** The IV's length byte, the IV the Keystore chose, then the ciphertext. */
    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        return byteArrayOf(iv.size.toByte()) + iv + cipher.doFinal(plain)
    }

    override fun decrypt(sealed: ByteArray): ByteArray {
        val ivLength = sealed.first().toInt()
        require(ivLength in 12..16 && sealed.size > 1 + ivLength) { "Not a sealed session" }
        val iv = sealed.copyOfRange(1, 1 + ivLength)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return cipher.doFinal(sealed.copyOfRange(1 + ivLength, sealed.size))
    }

    companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "lagna_session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
