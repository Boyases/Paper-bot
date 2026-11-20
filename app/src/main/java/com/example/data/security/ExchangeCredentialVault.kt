package com.example.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private val Context.vaultDataStore by preferencesDataStore(name = "quantpulse_exchange_vault")

class ExchangeCredentialVault(private val context: Context) {

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val dataB64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        return "$ivB64:$dataB64"
    }

    private fun decrypt(encoded: String): String? {
        val parts = encoded.split(":")
        if (parts.size != 2) return null
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    suspend fun saveCredentials(
        exchangeId: String,
        apiKey: String,
        apiSecret: String
    ): String {
        val cleanKey = apiKey.trim()
        val cleanSecret = apiSecret.trim()
        val masked = if (cleanKey.length >= 6) {
            "${cleanKey.take(3)}••••${cleanKey.takeLast(4)}"
        } else {
            "key_••••"
        }
        context.vaultDataStore.edit { prefs ->
            prefs[stringPreferencesKey("key_$exchangeId")] = encrypt(cleanKey)
            prefs[stringPreferencesKey("sec_$exchangeId")] = encrypt(cleanSecret)
        }
        return masked
    }

    suspend fun clearCredentials(exchangeId: String) {
        context.vaultDataStore.edit { prefs ->
            prefs.remove(stringPreferencesKey("key_$exchangeId"))
            prefs.remove(stringPreferencesKey("sec_$exchangeId"))
        }
    }

    fun observeHasCredentials(exchangeId: String): Flow<Boolean> =
        context.vaultDataStore.data.map { prefs ->
            prefs.contains(stringPreferencesKey("key_$exchangeId")) &&
                prefs.contains(stringPreferencesKey("sec_$exchangeId"))
        }

    suspend fun signOrderPayload(exchangeId: String, queryString: String): String? {
        val prefs = context.vaultDataStore.data.first()
        val encryptedSecret = prefs[stringPreferencesKey("sec_$exchangeId")] ?: return null
        val secret = decrypt(encryptedSecret) ?: return null
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(queryString.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "quantpulse_exchange_vault_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
