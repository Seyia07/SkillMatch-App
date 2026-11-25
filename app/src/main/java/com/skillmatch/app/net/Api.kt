package com.skillmatch.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.net.URLEncoder

/** code 0 means the server could not be reached at all. */
class ApiException(message: String, val code: Int = 0) : Exception(message) {
    val isNetwork get() = code == 0
    val isUnauthorized get() = code == 401
}

private class Reply(val code: Int, val text: String)

/** A file sent to the server as multipart/form-data. */
class Upload(val field: String, val filename: String, val mime: String, val bytes: ByteArray)

class SkillMatchApi(private val session: SessionStore) {
    private val refreshLock = Mutex()

    /** Called when the refresh token no longer works, so the app can show the login screen. */
    var onSessionExpired: (() -> Unit)? = null

    // ---------- plumbing ----------
    private fun rawCall(method: String, path: String, body: JSONObject?, token: String?, upload: Upload? = null): Reply {
        val url = try {
            URL(session.baseUrl.trimEnd('/') + "/api/v1" + path)
        } catch (e: MalformedURLException) {
            throw ApiException("The server address looks wrong. Check it and try again.")
        }
        val conn = (try { url.openConnection() } catch (e: IOException) { null }) as? HttpURLConnection
            ?: throw ApiException("The server address looks wrong. It must start with http:// or https://.")
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("Accept", "application/json")
            if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
            if (upload != null) {
                val boundary = "----SkillMatch" + System.currentTimeMillis()
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                val safeName = upload.filename.replace("\"", "").replace("\r", "").replace("\n", "")
                conn.outputStream.use { out ->
                    out.write(("--$boundary\r\nContent-Disposition: form-data; name=\"${upload.field}\"; filename=\"$safeName\"\r\nContent-Type: ${upload.mime}\r\n\r\n").toByteArray(Charsets.UTF_8))
                    out.write(upload.bytes)
                    out.write("\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8))
                }
            } else if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return Reply(code, text)
        } catch (e: IOException) {
            throw ApiException("Can't reach the server. Check your connection and the server address.")
        } finally {
            conn.disconnect()
        }
    }

    private fun failure(reply: Reply): ApiException {
        val message = try {
            val detail = JSONObject(reply.text).opt("detail")
            when (detail) {
                is String -> detail
                is JSONArray -> "Please check what you entered."
                else -> null
            }
        } catch (e: JSONException) {
            null
        }
        return ApiException(message ?: "Something went wrong (code ${reply.code}). Please try again.", reply.code)
    }

    private suspend fun refreshTokens(): Boolean {
        return refreshLock.withLock {
            val refresh = session.refreshToken ?: return@withLock false
            val reply = withContext(Dispatchers.IO) {
                rawCall("POST", "/auth/refresh", JSONObject().put("refresh_token", refresh), null)
            }
            if (reply.code in 200..299) {
                val o = parse(reply.text)
                session.saveTokens(o.getString("access_token"), o.getString("refresh_token"))
                true
            } else {
                session.clear()
                onSessionExpired?.invoke()
                false
            }
        }
    }

    private fun parse(text: String): JSONObject = try {
        JSONObject(text)
    } catch (e: JSONException) {
        throw ApiException("The server sent a reply the app could not read.", -1)
    }

    private suspend fun request(method: String, path: String, body: JSONObject? = null, auth: Boolean = true, upload: Upload? = null): String {
        var reply = withContext(Dispatchers.IO) {
            rawCall(method, path, body, if (auth) session.accessToken else null, upload)
        }
        if (reply.code == 401 && auth && session.refreshToken != null) {
            if (refreshTokens()) {
                reply = withContext(Dispatchers.IO) { rawCall(method, path, body, session.accessToken, upload) }
            } else {
                throw ApiException("Your session has expired. Please log in again.", 401)
            }
        }
        if (reply.code !in 200..299) throw failure(reply)
        return reply.text
    }

    private suspend fun json(method: String, path: String, body: JSONObject? = null, auth: Boolean = true): JSONObject =
        parse(request(method, path, body, auth))

    private fun storeSession(o: JSONObject) {
        session.saveTokens(o.getString("access_token"), o.getString("refresh_token"))
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    // ---------- sign up and login ----------
    /** Returns the demo code when the server is set to show it, otherwise null. */
    suspend fun signupRequest(email: String): String? {
        val o = json("POST", "/auth/signup/request", JSONObject().put("email", email), auth = false)
        return if (o.has("dev_code")) o.getString("dev_code") else null
    }

    suspend fun signupVerify(email: String, code: String): String =
        json("POST", "/auth/signup/verify", JSONObject().put("email", email).put("code", code), auth = false)
            .getString("signup_token")

    suspend fun signupComplete(token: String, password: String, year: Int, programme: String, consent: Boolean) {
        val body = JSONObject().put("signup_token", token).put("password", password)
            .put("year_of_study", year).put("programme", programme).put("consent", consent)
        storeSession(json("POST", "/auth/signup/complete", body, auth = false))
    }

    suspend fun login(email: String, password: String): LoginOutcome {
        val o = json("POST", "/auth/login", JSONObject().put("email", email).put("password", password), auth = false)
        if (o.optBoolean("mfa_required", false)) return LoginOutcome.AdminAccount
        storeSession(o)
        return LoginOutcome.LoggedIn
    }

    suspend fun logout() {
        val refresh = session.refreshToken
        if (refresh != null) {
            try {
                request("POST", "/auth/logout", JSONObject().put("refresh_token", refresh), auth = false)
            } catch (e: ApiException) {
                // Logging out on the phone must work even when the server cannot be reached.
            }
        }
        session.clear()
    }

    suspend fun forgot(email: String): String? {
        val o = json("POST", "/auth/password/forgot", JSONObject().put("email", email), auth = false)
        return if (o.has("dev_code")) o.getString("dev_code") else null
    }

    suspend fun reset(email: String, code: String, newPassword: String) {
        json("POST", "/auth/password/reset", JSONObject().put("email", email).put("code", code).put("new_password", newPassword), auth = false)
    }

    // ---------- profile ----------
    suspend fun me(): UserProfile = json("GET", "/me").toProfile()

    suspend fun saveProfile(year: Int, programme: String, units: List<String>): UserProfile =
        json("PUT", "/me/profile", JSONObject().put("year_of_study", year).put("programme", programme).put("units", JSONArray(units))).toProfile()

    suspend fun saveSkills(skills: List<String>): UserProfile =
        json("PUT", "/me/skills", JSONObject().put("skills", JSONArray(skills))).toProfile()

    suspend fun suggest(q: String): List<String> = JSONArray(request("GET", "/skills/suggest?q=${enc(q)}")).toStringList()

    /** The server also accepts DELETE /me, but a body on DELETE is unreliable on some phones, so POST is used. */
    suspend fun deleteAccount(password: String) {
        json("POST", "/me/delete", JSONObject().put("password", password))
    }

    // ---------- listings ----------
    suspend fun listings(type: String?, query: String, researchSet: String? = null, includeFit: Boolean = true): JobList {
        val sb = StringBuilder("/listings?sort=fit")
        if (type != null) sb.append("&type=").append(enc(type))
        if (query.isNotBlank()) sb.append("&q=").append(enc(query))
        if (researchSet != null) sb.append("&research_set=").append(enc(researchSet))
        if (!includeFit) sb.append("&include_fit=false")
        return json("GET", sb.toString()).toJobList()
    }

    suspend fun saved(): JobList = json("GET", "/me/saved").toJobList()

    suspend fun listing(id: Int, includeFit: Boolean = true): JobDetail =
        json("GET", "/listings/$id" + if (includeFit) "" else "?include_fit=false").toDetail()

    suspend fun setSaved(id: Int, saved: Boolean) {
        request(if (saved) "POST" else "DELETE", "/listings/$id/save")
    }

    suspend fun report(id: Int, reason: String, note: String) {
        json("POST", "/listings/$id/report", JSONObject().put("reason", reason).put("note", note))
    }

    // ---------- CV ----------
    /** The server reads the file in memory and only suggests skills. Nothing is saved until the student confirms. */
    suspend fun uploadCv(filename: String, mime: String, bytes: ByteArray): CvSuggestions =
        parse(request("POST", "/me/cv", upload = Upload("file", filename, mime, bytes))).toCv()

    // ---------- research mode ----------
    suspend fun researchStart(condition: String, listingSet: String): Int =
        json("POST", "/research/sessions", JSONObject().put("condition", condition).put("listing_set", listingSet)).getInt("session_id")

    suspend fun researchEvent(sessionId: Int, event: String, payload: JSONObject = JSONObject()) {
        json("POST", "/research/sessions/$sessionId/events", JSONObject().put("event", event).put("payload", payload))
    }

    /** Returns the seconds the session took. */
    suspend fun researchEnd(sessionId: Int, shortlist: List<Int>, confidence: Int?): Double {
        val body = JSONObject().put("shortlist", JSONArray(shortlist))
        if (confidence != null) body.put("confidence", confidence)
        return json("POST", "/research/sessions/$sessionId/end", body).optDouble("seconds", 0.0)
    }

    suspend fun researchSurvey(sus: List<Int>, tamUseful: List<Int>, tamEase: List<Int>) {
        json("POST", "/research/survey", JSONObject().put("sus", JSONArray(sus)).put("tam_useful", JSONArray(tamUseful)).put("tam_ease", JSONArray(tamEase)))
    }
}
