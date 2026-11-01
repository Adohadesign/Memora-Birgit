package de.adohadesign.memorasolo.security

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PIN and pattern are not stored in cleartext. This is an access barrier, not full storage encryption. */
class PatternSecurity(context: Context) {
    private val pref = context.getSharedPreferences("memora_lock", Context.MODE_PRIVATE)
    private val random = SecureRandom()
    val configured: Boolean get() = pref.contains("pattern") && pref.contains("pin")
    private fun hash(secret: String, salt: ByteArray): String {
        val spec = PBEKeySpec(secret.toCharArray(), salt, 180_000, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
    private fun newVerifier(secret: String): String {
        val salt = ByteArray(16).also { random.nextBytes(it) }
        return Base64.encodeToString(salt, Base64.NO_WRAP) + ":" + hash(secret, salt)
    }
    private fun verify(secret: String, stored: String): Boolean {
        val parts = stored.split(":")
        if (parts.size != 2) return false
        return try {
            val salt = Base64.decode(parts[0], Base64.NO_WRAP)
            val expected = Base64.decode(parts[1], Base64.NO_WRAP)
            val actual = Base64.decode(hash(secret, salt), Base64.NO_WRAP)
            MessageDigest.isEqual(expected, actual)
        } catch (_: Exception) { false }
    }
    fun setup(pattern: String, pin: String) {
        require(pattern.length >= 4 && pattern.toSet().size == pattern.length)
        require(pin.length == 4 && pin.all { it.isDigit() })
        pref.edit().putString("pattern", newVerifier(pattern))
            .putString("pin", newVerifier(pin))
            .putInt("failures", 0).putLong("lockedUntil", 0).apply()
    }
    fun verifyPattern(pattern: String): Boolean = check(pattern, "pattern")
    fun verifyPin(pin: String): Boolean = check(pin, "pin")
    fun canTry(): Boolean = System.currentTimeMillis() >= pref.getLong("lockedUntil", 0)
    fun delaySeconds(): Long = ((pref.getLong("lockedUntil", 0) - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
    private fun check(secret: String, key: String): Boolean {
        if (!canTry()) return false
        val ok = verify(secret, pref.getString(key, "") ?: "")
        if (ok) {
            pref.edit().putInt("failures", 0).putLong("lockedUntil", 0).apply()
        } else {
            val count = pref.getInt("failures", 0) + 1
            val delay = when {
                count < 5 -> 0L
                count < 8 -> 30_000L
                count < 11 -> 5 * 60_000L
                else -> 60 * 60_000L
            }
            pref.edit().putInt("failures", count)
                .putLong("lockedUntil", System.currentTimeMillis() + delay).apply()
        }
        return ok
    }
    fun changePattern(value: String) {
        require(value.length >= 4 && value.toSet().size == value.length)
        pref.edit().putString("pattern", newVerifier(value)).apply()
    }
    fun changePin(value: String) {
        require(value.length == 4 && value.all(Char::isDigit))
        pref.edit().putString("pin", newVerifier(value)).apply()
    }
}
