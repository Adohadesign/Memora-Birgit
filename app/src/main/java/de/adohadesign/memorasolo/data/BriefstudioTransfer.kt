package de.adohadesign.memorasolo.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class BriefstudioDocument(val id: String, val title: String, val body: String, val date: String, val time: String)

object BriefstudioTransfer {
    private const val origin = "https://xvpetyidbjjfsmqnksoa.supabase.co"
    // Public Supabase publishable key; the private service-role key is never used here.
    private const val apiKey = "sb_publishable_gpMWv1dQ5NN3JgFBoF6UZQ_ELqKxA9K"
    private const val preferenceName = "briefstudio_auth_private"
    private const val keyAlias = "memora_briefstudio_refresh_v1"

    private fun request(path: String, method: String = "GET", token: String? = null, payload: String? = null): String {
        val connection = (URL(origin + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("apikey", apiKey)
            setRequestProperty("Accept", "application/json")
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
            if (payload != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        return try {
            if (payload != null) connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText().take(250) } ?: ""
                throw IllegalStateException(if (status == 400 || status == 401) "Anmeldung fehlgeschlagen. E-Mail und Passwort prüfen." else "Briefstudio-Server: HTTP $status $detail")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun key(): java.security.Key {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        store.getKey(keyAlias, null)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }

    private fun saveRefresh(context: Context, refreshToken: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.iv + cipher.doFinal(refreshToken.toByteArray(Charsets.UTF_8))
        context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE).edit()
            .putString("session", Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    private fun readRefresh(context: Context): String? {
        val raw = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE).getString("session", null) ?: return null
        return try {
            val bytes = Base64.decode(raw, Base64.NO_WRAP)
            require(bytes.size > 28)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        } catch (_: Exception) { null }
    }

    fun isConnected(context: Context): Boolean = readRefresh(context) != null

    fun disconnect(context: Context) {
        context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE).edit().remove("session").apply()
    }

    private fun retrieve(accessToken: String): List<BriefstudioDocument> {
        val documents = mutableListOf<BriefstudioDocument>()
        var offset = 0
        while (true) {
            val response = JSONArray(request("/rest/v1/bs_documents?select=id,title,body,assets,diary_date,diary_time&transfer_ready=eq.true&order=created_at.asc&limit=200&offset=$offset", token = accessToken))
            for (i in 0 until response.length()) {
                val item = response.getJSONObject(i)
                // Letter documents can contain text solely in freely placed textboxes.
                // Keep the original body unchanged and append those separate text elements.
                val body = item.optString("body", "")
                val assets = item.optJSONArray("assets")
                val textboxes = mutableListOf<String>()
                if (assets != null) {
                    for (assetIndex in 0 until assets.length()) {
                        val asset = assets.optJSONObject(assetIndex) ?: continue
                        if (asset.optString("kind") == "textbox") {
                            val textbox = asset.optString("text", "").trim()
                            if (textbox.isNotBlank()) textboxes.add(textbox)
                        }
                    }
                }
                val combinedBody = (listOf(body.trim()) + textboxes)
                    .filter { it.isNotBlank() }.joinToString("\n\n")
                val date = item.optString("diary_date", "")
                val time = item.optString("diary_time", "")
                if (combinedBody.isNotBlank() && date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) && time.isNotBlank()) {
                    documents.add(BriefstudioDocument(item.getString("id"), item.optString("title", ""), combinedBody, date, time.take(5)))
                }
            }
            if (response.length() < 200) break
            offset += 200
            require(offset < 10000) { "Zu viele vorgemerkte Dokumente" }
        }
        return documents
    }

    suspend fun connectAndFetch(context: Context, email: String, password: String): List<BriefstudioDocument> = withContext(Dispatchers.IO) {
        require(email.isNotBlank() && password.isNotBlank()) { "E-Mail und Passwort eingeben" }
        val body = JSONObject().put("email", email.trim()).put("password", password).toString()
        val session = JSONObject(request("/auth/v1/token?grant_type=password", "POST", payload = body))
        val documents = retrieve(session.getString("access_token"))
        saveRefresh(context.applicationContext, session.getString("refresh_token"))
        documents
    }

    suspend fun fetchSaved(context: Context): List<BriefstudioDocument> = withContext(Dispatchers.IO) {
        val refresh = readRefresh(context.applicationContext) ?: return@withContext emptyList()
        val body = JSONObject().put("refresh_token", refresh).toString()
        val session = JSONObject(request("/auth/v1/token?grant_type=refresh_token", "POST", payload = body))
        // Refresh tokens rotate after use.
        saveRefresh(context.applicationContext, session.getString("refresh_token"))
        retrieve(session.getString("access_token"))
    }
}
