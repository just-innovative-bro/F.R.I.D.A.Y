package com.friday.assistant.conversation

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class ConversationConnectionSettings(context: Context) {
    private val preferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )
    private val secureTokenStore = SecureTokenStore(context)

    fun endpointUrl(): String = preferences.getString(KEY_ENDPOINT, "").orEmpty()

    fun backendConfiguration(): BackendConfiguration = BackendConfiguration(
        endpointUrl = endpointUrl(),
        accessToken = secureTokenStore.read()
    )

    fun save(endpointUrl: String, accessToken: String) {
        if (accessToken.isNotBlank()) {
            secureTokenStore.write(accessToken.trim())
        } else if (secureTokenStore.read() == null) {
            throw SecureTokenException("Enter the F.R.I.D.A.Y. server access token.")
        }
        if (!preferences.edit().putString(KEY_ENDPOINT, endpointUrl.trim()).commit()) {
            throw SecureTokenException("The backend URL could not be saved.")
        }
    }

    fun clearToken() {
        secureTokenStore.clear()
    }

    private class SecureTokenStore(context: Context) {
        private val preferences = context.getSharedPreferences(
            TOKEN_PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )

        fun read(): String? {
            val stored = preferences.getString(KEY_ENCRYPTED_TOKEN, null) ?: return null
            try {
                val encrypted = Base64.decode(stored, Base64.NO_WRAP)
                if (encrypted.size <= IV_LENGTH_BYTES) {
                    throw SecureTokenException("The saved backend token is invalid.")
                }
                val iv = encrypted.copyOfRange(0, IV_LENGTH_BYTES)
                val ciphertext = encrypted.copyOfRange(IV_LENGTH_BYTES, encrypted.size)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
                return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
            } catch (exception: SecureTokenException) {
                throw exception
            } catch (exception: Exception) {
                throw SecureTokenException("The saved backend token cannot be unlocked.", exception)
            }
        }

        fun write(token: String) {
            try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey())
                val encrypted = cipher.iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8))
                val encoded = Base64.encodeToString(encrypted, Base64.NO_WRAP)
                if (!preferences.edit().putString(KEY_ENCRYPTED_TOKEN, encoded).commit()) {
                    throw SecureTokenException("The server access token could not be saved.")
                }
            } catch (exception: SecureTokenException) {
                throw exception
            } catch (exception: Exception) {
                throw SecureTokenException("The server access token could not be encrypted.", exception)
            }
        }

        fun clear() {
            if (!preferences.edit().remove(KEY_ENCRYPTED_TOKEN).commit()) {
                throw SecureTokenException("The server access token could not be cleared.")
            }
        }

        private fun secretKey(): SecretKey {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
            (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            return generator.generateKey()
        }

        private companion object {
            const val ANDROID_KEY_STORE = "AndroidKeyStore"
            const val KEY_ALIAS = "friday-backend-token"
            const val TOKEN_PREFERENCES_NAME = "friday_secure_connection"
            const val KEY_ENCRYPTED_TOKEN = "encrypted_server_token"
            const val TRANSFORMATION = "AES/GCM/NoPadding"
            const val IV_LENGTH_BYTES = 12
            const val TAG_LENGTH_BITS = 128
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "friday_connection"
        const val KEY_ENDPOINT = "backend_endpoint"
    }
}

class SecureTokenException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
