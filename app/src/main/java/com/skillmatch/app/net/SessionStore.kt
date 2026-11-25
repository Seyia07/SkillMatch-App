package com.skillmatch.app.net

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.skillmatch.app.BuildConfig
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts small strings with a key that never leaves the Android Keystore. */
private class SecretBox {
    private val alias = "skillmatch_session_key"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + sealed, Base64.NO_WRAP)
    }

    /** Returns null when the text cannot be read (for example after the key was reset). */
    fun decrypt(text: String): String? = try {
        val all = Base64.decode(text, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, all.copyOfRange(0, 12)))
        String(cipher.doFinal(all, 12, all.size - 12), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }
}

/** The server address and the login tokens. Tokens are stored encrypted. */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("skillmatch_session", Context.MODE_PRIVATE)
    private val box = SecretBox()

    var baseUrl: String
        get() = clean(prefs.getString("base_url_v2", null)?.takeIf { it.isNotBlank() } ?: BuildConfig.DEFAULT_SERVER)
        set(value) { prefs.edit().putString("base_url_v2", clean(value)).apply() }

    /** Trims, drops a pasted /health or trailing slash, and uses https for online servers (http gets redirected and fails). */
    private fun clean(raw: String): String {
        var v = raw.trim().trimEnd('/').removeSuffix("/health").trimEnd('/')
        val local = Regex("^http://(10\\.0\\.2\\.2|localhost|127\\.|192\\.168\\.|10\\.)").containsMatchIn(v)
        if (v.startsWith("http://") && !local) v = "https://" + v.removePrefix("http://")
        return v
    }

    val accessToken: String? get() = prefs.getString("access", null)?.let { box.decrypt(it) }
    val refreshToken: String? get() = prefs.getString("refresh", null)?.let { box.decrypt(it) }
    val hasSession: Boolean get() = refreshToken != null

    fun saveTokens(access: String, refresh: String) {
        prefs.edit().putString("access", box.encrypt(access)).putString("refresh", box.encrypt(refresh)).apply()
    }

    fun clear() {
        prefs.edit().remove("access").remove("refresh").apply()
    }
}