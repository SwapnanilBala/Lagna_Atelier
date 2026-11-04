package site.lagnaatelier.app.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import site.lagnaatelier.app.data.account.SessionCipher
import site.lagnaatelier.app.data.account.SessionVault
import site.lagnaatelier.app.data.account.StoredSession
import site.lagnaatelier.app.data.account.VaultStorage

/** The vault's file held in memory. */
class MemoryVault(var value: String? = null) : VaultStorage {
    override fun read() = value
    override fun write(value: String) {
        this.value = value
    }
    override fun clear() {
        value = null
    }
}

/**
 * A stand-in for the Keystore cipher: enough to show the vault never writes
 * the token as it is, and to fail like a lost key when told to.
 */
class XorCipher(var broken: Boolean = false) : SessionCipher {
    override fun encrypt(plain: ByteArray) = byteArrayOf(MARK) + plain.map { (it.toInt() xor 0x5A).toByte() }
    override fun decrypt(sealed: ByteArray): ByteArray {
        check(!broken && sealed.firstOrNull() == MARK) { "Key not found" }
        return sealed.drop(1).map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
    }

    private companion object {
        const val MARK: Byte = 0x42
    }
}

class SessionVaultTest {
    private val session = StoredSession(
        token = "c2Vzc2lvbi10b2tlbi1mb3ItdGhlLWFwcC0xMjM0NTY3",
        expiresAtMillis = 2_000_000_000_000,
        email = "someone@example.test",
        displayName = "Someone",
        syncCharts = true,
    )

    @Test
    fun roundTripsWithoutWritingTheTokenInTheClear() {
        val storage = MemoryVault()
        val vault = SessionVault(storage, XorCipher())

        vault.write(session)

        assertFalse(storage.value!!.contains(session.token))
        assertFalse(storage.value!!.contains(session.email))
        assertEquals(session, vault.read(now = 1_000))
    }

    @Test
    fun anExpiredSessionIsSignedOutAndDeleted() {
        val storage = MemoryVault()
        val vault = SessionVault(storage, XorCipher())
        vault.write(session)

        assertNull(vault.read(now = session.expiresAtMillis))
        assertNull(storage.value)
    }

    @Test
    fun aSessionThatWillNotDecryptIsSignedOutAndDeleted() {
        /* What a restored backup or a wiped Keystore key looks like. */
        val storage = MemoryVault()
        val cipher = XorCipher()
        SessionVault(storage, cipher).write(session)
        cipher.broken = true

        assertNull(SessionVault(storage, cipher).read(now = 1_000))
        assertNull(storage.value)
    }

    @Test
    fun garbageInTheFileIsSignedOut() {
        val storage = MemoryVault("not base64 at all %%%")
        assertNull(SessionVault(storage, XorCipher()).read(now = 1_000))
        assertNull(storage.value)
    }

    @Test
    fun clearEmptiesTheFile() {
        val storage = MemoryVault()
        val vault = SessionVault(storage, XorCipher())
        vault.write(session)

        vault.clear()

        assertNull(storage.value)
        assertNull(vault.read(now = 1_000))
    }
}
