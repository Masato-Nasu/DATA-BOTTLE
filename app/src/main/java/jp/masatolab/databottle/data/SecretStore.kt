package jp.masatolab.databottle.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small Android Keystore-backed store for BYOK secrets.
 * The OpenAI key is encrypted before it is written to app-private preferences.
 */
class SecretStore(context: Context) {
    private val prefs = context.getSharedPreferences("data_bottle_secrets", Context.MODE_PRIVATE)

    fun hasOpenAiAdminKey(): Boolean = readOpenAiAdminKey()?.isNotBlank() == true

    fun saveOpenAiAdminKey(value: String) {
        val trimmed = value.trim()
        if (trimmed.isBlank()) {
            clearOpenAiAdminKey()
            return
        }

        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val encrypted = cipher.doFinal(trimmed.toByteArray(StandardCharsets.UTF_8))
            prefs.edit()
                .putString(KEY_OPENAI_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(KEY_OPENAI_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .apply()
        }.onFailure {
            clearOpenAiAdminKey()
        }
    }

    fun readOpenAiAdminKey(): String? = runCatching {
        val encryptedText = prefs.getString(KEY_OPENAI_CIPHERTEXT, null) ?: return@runCatching null
        val ivText = prefs.getString(KEY_OPENAI_IV, null) ?: return@runCatching null
        val encrypted = Base64.decode(encryptedText, Base64.NO_WRAP)
        val iv = Base64.decode(ivText, Base64.NO_WRAP)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
    }.getOrNull()

    fun clearOpenAiAdminKey() {
        prefs.edit()
            .remove(KEY_OPENAI_CIPHERTEXT)
            .remove(KEY_OPENAI_IV)
            .apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    companion object {
        private const val KEY_ALIAS = "data_bottle_openai_byok_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_OPENAI_CIPHERTEXT = "openai_admin_key_ciphertext"
        private const val KEY_OPENAI_IV = "openai_admin_key_iv"
    }
}
