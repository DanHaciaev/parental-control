package com.teo.core.util

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PINs are short (low entropy) so a slow KDF (PBKDF2, high iteration count) is used
 * instead of a bare hash to make offline brute-forcing of a leaked hash impractical.
 */
object PinHasher {
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    data class Hashed(val hash: String, val salt: String)

    fun hash(pin: String, salt: ByteArray = randomSalt()): Hashed {
        val digest = deriveKey(pin, salt)
        return Hashed(
            hash = Base64.encodeToString(digest, Base64.NO_WRAP),
            salt = Base64.encodeToString(salt, Base64.NO_WRAP)
        )
    }

    fun verify(pin: String, expectedHash: String, saltBase64: String): Boolean {
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val digest = deriveKey(pin, salt)
        val actualHash = Base64.encodeToString(digest, Base64.NO_WRAP)
        return constantTimeEquals(actualHash, expectedHash)
    }

    private fun deriveKey(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        return factory.generateSecret(spec).encoded
    }

    private fun randomSalt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].code xor b[i].code)
        return result == 0
    }
}
