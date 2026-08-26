package com.kino.puber.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import com.kino.puber.core.logger.log
import java.security.KeyStore
import java.security.UnrecoverableEntryException
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Tokens sealed with an AndroidKeyStore key.
 *
 * The key does not outlive everything the app does: an uninstall, a restored backup, a changed
 * screen lock or a system update can all take it, leaving behind ciphertext that will never
 * decrypt again. These values are read while the app is starting, so an exception there took the
 * whole app down — over and over, with no way out but clearing its data. A value that cannot be
 * read is therefore reported as absent and dropped, which lands the user on the pairing screen
 * exactly as a first install would.
 */
internal class CryptoPreferenceRepository(
    private val context: Context,
) : ICryptoPreferenceRepository {

    private val sharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun saveAccessToken(token: String) = saveString(ACCESS_TOKEN_KEY_NAME, token)

    override fun getAccessToken(): String? = getString(ACCESS_TOKEN_KEY_NAME)

    override fun clearAccessToken() = saveString(ACCESS_TOKEN_KEY_NAME, null)

    override fun saveRefreshToken(token: String) = saveString(REFRESH_TOKEN_KEY_NAME, token)

    override fun getRefreshToken() = getString(REFRESH_TOKEN_KEY_NAME)

    override fun clearRefreshToken() = saveString(REFRESH_TOKEN_KEY_NAME, null)

    override fun saveUsername(userName: String) = saveString(USERNAME_KEY_NAME, userName)

    override fun getUsername(): String? = getString(USERNAME_KEY_NAME)

    override fun clearUsername() = saveString(USERNAME_KEY_NAME, null)

    @SuppressLint("HardwareIds")
    override fun getAndroidId(): String? =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

    override fun saveApiDomain(domain: String?) = saveString(API_DOMAIN_KEY_NAME, domain)

    override fun getApiDomain(): String? = getString(API_DOMAIN_KEY_NAME)

    private fun saveString(name: String, value: String?) {
        sharedPreferences.edit {
            putString(name, encrypt(value.orEmpty()))
        }
    }

    /**
     * Sealing is left to throw. It happens while the user is pairing, where a failure has to be
     * seen — swallowing it would leave them pairing again on every launch with nothing to explain
     * why. Only the read path below has to survive.
     */
    private fun getString(name: String): String? {
        val value = sharedPreferences.getString(name, null) ?: return null
        return runCatching { decrypt(value) }.getOrElse { error ->
            if (error.isSealedShut()) {
                log(error, "Cannot unseal $name — the key that wrote it is gone. Dropping it.")
                sharedPreferences.edit { remove(name) }
            } else {
                log(error, "Cannot unseal $name just now. Keeping it and reporting no value.")
            }
            null
        }
    }

    /**
     * True when these bytes can never become a token again: the GCM tag does not verify against
     * the current key, or the string is not the shape [encrypt] writes. Everything else — a
     * keystore that is busy, missing or refusing to answer — may well work on the next launch,
     * so the value is kept rather than thrown away on one bad morning.
     */
    private fun Throwable.isSealedShut(): Boolean = when (this) {
        is BadPaddingException,          // AEADBadTagException: sealed with a key that is gone
        is IllegalArgumentException,     // not Base64, or too short to hold an IV
        is IndexOutOfBoundsException -> true
        else -> false
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + cipherText, Base64.DEFAULT)
    }

    private fun decrypt(encryptedText: String): String {
        if (encryptedText.isEmpty()) return ""
        val decoded = Base64.decode(encryptedText, Base64.DEFAULT)
        require(decoded.size > GCM_IV_BYTES) { "Sealed value is too short to hold an IV" }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(GCM_TAG_BITS, decoded.sliceArray(0 until GCM_IV_BYTES)),
        )
        return String(
            cipher.doFinal(decoded.sliceArray(GCM_IV_BYTES until decoded.size)),
            Charsets.UTF_8,
        )
    }

    /**
     * The alias can be missing, hold something that is not a secret key, or hold a key the
     * keystore will not hand back. Those three mean there is no usable key and a new one is made
     * — which replaces the alias, so anything sealed with the old key is lost, and [getString]
     * clears it away as it meets it. Any *other* failure is the keystore having a bad moment,
     * and it is rethrown rather than answered by rotating a key that is probably fine.
     *
     * Serialized because two callers arriving at an empty alias would otherwise each generate,
     * and the second would replace the key the first had just sealed a token with.
     */
    private fun secretKey(): SecretKey = synchronized(KEY_LOCK) {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existing = try {
            keyStore.getEntry(SECURITY_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        } catch (unrecoverable: UnrecoverableEntryException) {
            log(unrecoverable, "The stored key cannot be recovered; replacing it.")
            null
        }
        existing?.secretKey ?: generateKey()
    }

    private fun generateKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                SECURITY_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build()
        )
        return keyGenerator.generateKey()
    }

    companion object {
        private const val PREFS_NAME = "KINOPUBER_SECURE_PREFS"
        private const val ACCESS_TOKEN_KEY_NAME = "KINOPUBER_ACCESS_TOKEN"
        private const val REFRESH_TOKEN_KEY_NAME = "KINOPUBER_REFRESH_TOKEN"
        private const val USERNAME_KEY_NAME = "KINOPUBER_USERNAME_KEY_NAME"
        private const val API_DOMAIN_KEY_NAME = "KINOPUBER_API_DOMAIN"
        private const val SECURITY_KEY_ALIAS = "SECURITY_KEY_ALIAS"

        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256

        /** GCM is always given a 12-byte IV here, and it is stored ahead of the ciphertext. */
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128

        /** Process-wide: the alias is one slot in the keystore, whoever reaches for it. */
        private val KEY_LOCK = Any()
    }
}
