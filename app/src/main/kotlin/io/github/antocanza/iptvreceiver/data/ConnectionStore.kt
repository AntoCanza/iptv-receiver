package io.github.antocanza.iptvreceiver.data

import android.content.Context
import android.annotation.SuppressLint
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.github.antocanza.iptvreceiver.model.ProviderConnection
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Only encrypted ciphertext and a random IV are written to app-private storage. */
class ConnectionStore(context: Context) : SavedConnectionStore {
    private val preferences = context.getSharedPreferences("connection", Context.MODE_PRIVATE)

    // KTX edit discards commit's success flag; persistence failures must remain detectable.
    @SuppressLint("UseKtx")
    override fun save(connection: ProviderConnection) {
        val plaintext = JSONObject().apply {
            put("name", connection.name)
            put("server", connection.serverUrl)
            put("username", connection.username)
            put("password", connection.password)
            put("allowHttpStreams", connection.allowHttpStreams)
        }.toString().toByteArray(Charsets.UTF_8)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val ciphertext = cipher.doFinal(plaintext)
            check(preferences.edit()
                .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .putString("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                .commit())
        } finally {
            plaintext.fill(0)
        }
    }

    override fun load(): ProviderConnection? {
        val ciphertext = preferences.getString("ciphertext", null) ?: return null
        return try {
            val iv = preferences.getString("iv", null) ?: return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            val plaintext = cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP))
            try {
                val value = JSONObject(String(plaintext, Charsets.UTF_8))
                ProviderConnection(value.getString("name"), value.getString("server"),
                    value.getString("username"), value.getString("password"), value.optBoolean("allowHttpStreams", false))
            } finally { plaintext.fill(0) }
        } catch (_: Exception) {
            clear()
            null
        }
    }

    @SuppressLint("UseKtx")
    override fun clear() {
        check(preferences.edit().clear().commit())
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build())
        }.generateKey()
    }

    private companion object { const val KEY_ALIAS = "iptv-receiver-connection-v1" }
}
