package com.mtgcompanion.app.data.supabase

import android.net.Uri
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mtgcompanion.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

private val Context.supabaseAuthStore by preferencesDataStore(name = "supabase_auth")

/** The signed-in account, or null. */
data class SupabaseAccount(val userId: String, val email: String)

/**
 * A problem the user can act on ("Wrong email or password"), kept separate from bugs. [httpCode],
 * [errorCode] and [serverMessage] describe the server's answer, when there was one.
 */
class SupabaseAuthException(
    message: String,
    val httpCode: Int = 0,
    val errorCode: String? = null,
    val serverMessage: String = ""
) : Exception(message) {
    /**
     * Whether the server says this session is gone for good (revoked, expired, already-used refresh
     * token), as opposed to being briefly unable to answer — only then should the device sign out.
     */
    val sessionGone: Boolean
        get() = httpCode in 400..403 && (
            errorCode in SESSION_GONE_CODES ||
                serverMessage.contains("refresh token", ignoreCase = true) ||
                serverMessage.contains("invalid_grant", ignoreCase = true)
            )

    private companion object {
        val SESSION_GONE_CODES = setOf(
            "refresh_token_not_found", "refresh_token_already_used", "session_not_found", "session_expired",
            "user_not_found", "user_banned", "invalid_grant"
        )
    }
}

/** The account server answered with an error that isn't about this session (overloaded, rate limited…). */
class SyncServerUnavailableException(message: String) : IOException(message)

internal val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

/**
 * Where confirmation emails send the user back to. Must be listed under Authentication → URL
 * Configuration → Redirect URLs in the Supabase dashboard, or Supabase falls back to the Site URL.
 */
const val AUTH_REDIRECT_URL = "mtgcompanion://auth-callback"

/**
 * Email + password accounts against Supabase Auth (GoTrue), over plain HTTPS — no SDK, so the app
 * keeps its existing OkHttp/Moshi stack. The session (access + refresh token) lives in app-private
 * DataStore and is refreshed shortly before it expires.
 */
class SupabaseAuth(private val context: Context) {

    val configured: Boolean = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    internal val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val accessKey = stringPreferencesKey("access_token")
    private val refreshKey = stringPreferencesKey("refresh_token")
    private val expiresKey = longPreferencesKey("expires_at_ms")
    private val userIdKey = stringPreferencesKey("user_id")
    private val emailKey = stringPreferencesKey("email")
    private val refreshMutex = Mutex()

    private val _account = MutableStateFlow<SupabaseAccount?>(null)
    val account: StateFlow<SupabaseAccount?> = _account.asStateFlow()

    /** True after a password-reset link signed the user in: the app should ask for a new password. */
    private val _passwordRecovery = MutableStateFlow(false)
    val passwordRecovery: StateFlow<Boolean> = _passwordRecovery.asStateFlow()

    fun dismissPasswordRecovery() {
        _passwordRecovery.value = false
    }

    suspend fun restore() {
        val prefs = context.supabaseAuthStore.data.first()
        val id = prefs[userIdKey]
        val email = prefs[emailKey]
        _account.value = if (id != null && email != null && prefs[refreshKey] != null) SupabaseAccount(id, email) else null
    }

    /**
     * Creates an account. Supabase emails a confirmation link by default, so this usually returns
     * false ("check your email"); it returns true if the project doesn't require confirmation and
     * the new account is already signed in.
     */
    suspend fun signUp(email: String, password: String): Boolean {
        val body = JSONObject().put("email", email.trim()).put("password", password)
        val json = post("/auth/v1/signup?redirect_to=" + Uri.encode(AUTH_REDIRECT_URL), body)
        return if (json.has("access_token")) {
            saveSession(json)
            true
        } else false
    }

    /**
     * Emails a password-reset link. Its link reopens the app signed in, and the app then asks for a
     * new password. Supabase answers the same whether or not the email has an account, so this
     * doesn't reveal who's registered.
     */
    suspend fun sendPasswordReset(email: String) {
        post("/auth/v1/recover?redirect_to=" + Uri.encode(AUTH_REDIRECT_URL), JSONObject().put("email", email.trim()))
    }

    /** Sets a new password for the signed-in account. */
    suspend fun updatePassword(newPassword: String) {
        val token = accessToken() ?: throw SupabaseAuthException("You're signed out — request a new reset link.")
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(BuildConfig.SUPABASE_URL + "/auth/v1/user")
                .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer $token")
                .put(JSONObject().put("password", newPassword).toString().toRequestBody(JSON_MEDIA))
                .build()
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
                    throw SupabaseAuthException(friendlyError(response.code, json))
                }
            }
        }
        _passwordRecovery.value = false
    }

    /** Sends the sign-up confirmation email again (the new one links back into the app). */
    suspend fun resendConfirmation(email: String) {
        post("/auth/v1/resend?redirect_to=" + Uri.encode(AUTH_REDIRECT_URL), JSONObject().put("type", "signup").put("email", email.trim()))
    }

    /**
     * Finishes sign-in from an email link that reopened the app: Supabase appends the new session to
     * the redirect as a URL fragment (#access_token=…&refresh_token=…), or an error if the link was
     * already used or expired.
     */
    suspend fun completeFromLink(link: Uri): SupabaseAccount {
        val params = (link.fragment ?: link.query).orEmpty().split("&").mapNotNull { part ->
            val i = part.indexOf('=')
            if (i <= 0) null else Uri.decode(part.substring(0, i)) to Uri.decode(part.substring(i + 1))
        }.toMap()
        params["error_description"]?.let { description ->
            throw SupabaseAuthException(
                if (params["error_code"] == "otp_expired") "That link has expired or was already used. Sign in, or send a new confirmation email."
                else description.replace('+', ' ')
            )
        }
        val access = params["access_token"] ?: throw SupabaseAuthException("That link didn't include a sign-in. Try signing in with your password.")
        val refresh = params["refresh_token"] ?: throw SupabaseAuthException("That link didn't include a sign-in. Try signing in with your password.")
        val user = withContext(Dispatchers.IO) {
            http.newCall(
                Request.Builder()
                    .url(BuildConfig.SUPABASE_URL + "/auth/v1/user")
                    .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    .header("Authorization", "Bearer $access")
                    .get()
                    .build()
            ).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw SupabaseAuthException("Couldn't finish signing in (HTTP ${response.code}). Try signing in with your password.")
                JSONObject(text)
            }
        }
        saveSession(
            JSONObject()
                .put("access_token", access)
                .put("refresh_token", refresh)
                .put("expires_in", params["expires_in"]?.toLongOrNull() ?: 3600L)
                .put("user", user)
        )
        if (params["type"] == "recovery") _passwordRecovery.value = true
        return _account.value!!
    }

    suspend fun signIn(email: String, password: String) {
        val body = JSONObject().put("email", email.trim()).put("password", password)
        saveSession(post("/auth/v1/token?grant_type=password", body))
    }

    /**
     * Signs this device out on the server (best effort) and forgets its session. scope=local: the
     * default (global) would also sign out every other device on the account.
     */
    suspend fun signOut() {
        val token = context.supabaseAuthStore.data.first()[accessKey]
        if (token != null) {
            runCatching {
                withContext(Dispatchers.IO) {
                    http.newCall(
                        Request.Builder()
                            .url(BuildConfig.SUPABASE_URL + "/auth/v1/logout?scope=local")
                            .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                            .header("Authorization", "Bearer $token")
                            .post("".toRequestBody(JSON_MEDIA))
                            .build()
                    ).execute().close()
                }
            }
        }
        context.supabaseAuthStore.edit { it.clear() }
        _account.value = null
    }

    /** A valid access token, refreshing it first if it's about to expire; null when signed out. */
    suspend fun accessToken(): String? = refreshMutex.withLock {
        val prefs = context.supabaseAuthStore.data.first()
        val refresh = prefs[refreshKey] ?: return null
        val access = prefs[accessKey]
        val expiresAt = prefs[expiresKey] ?: 0L
        if (access != null && System.currentTimeMillis() < expiresAt - 60_000) return access
        return try {
            val json = post("/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", refresh))
            saveSession(json)
            json.getString("access_token")
        } catch (e: SupabaseAuthException) {
            if (!e.sessionGone) {
                // A server hiccup (5xx, rate limit) mustn't cost the user their session; try again later.
                throw SyncServerUnavailableException("The account server isn't responding — will try again shortly.")
            }
            // The refresh token was revoked or expired — the user has to sign in again.
            context.supabaseAuthStore.edit { it.clear() }
            _account.value = null
            null
        }
    }

    /** The server rejected the current access token (clock skew, revoked early): refresh it on next use. */
    suspend fun invalidateAccessToken() {
        context.supabaseAuthStore.edit { it[expiresKey] = 0L }
    }

    private suspend fun saveSession(json: JSONObject) {
        val user = json.getJSONObject("user")
        val account = SupabaseAccount(user.getString("id"), user.optString("email"))
        val expiresIn = json.optLong("expires_in", 3600)
        context.supabaseAuthStore.edit {
            it[accessKey] = json.getString("access_token")
            it[refreshKey] = json.getString("refresh_token")
            it[expiresKey] = System.currentTimeMillis() + expiresIn * 1000
            it[userIdKey] = account.userId
            it[emailKey] = account.email
        }
        _account.value = account
    }

    private suspend fun post(path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(BuildConfig.SUPABASE_URL + path)
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (!response.isSuccessful) {
                val raw = listOf("msg", "error_description", "message", "error").joinToString(" ") { json.optString(it) }
                throw SupabaseAuthException(
                    friendlyError(response.code, json),
                    httpCode = response.code,
                    errorCode = json.optString("error_code").ifBlank { json.optString("error").ifBlank { null } },
                    serverMessage = raw
                )
            }
            json
        }
    }

    private fun friendlyError(code: Int, json: JSONObject): String {
        val raw = listOf("msg", "error_description", "message", "error").firstNotNullOfOrNull { key ->
            json.optString(key).takeIf { it.isNotBlank() }
        }.orEmpty()
        return when {
            raw.contains("Invalid login credentials", ignoreCase = true) -> "Wrong email or password."
            raw.contains("Email not confirmed", ignoreCase = true) -> "Confirm your email first — open the link Supabase sent you, then sign in."
            raw.contains("already registered", ignoreCase = true) -> "That email already has an account. Sign in instead."
            raw.contains("Password should be", ignoreCase = true) -> raw
            raw.contains("should be different from the old password", ignoreCase = true) -> "That's your current password — choose a different one."
            raw.contains("reauthentication", ignoreCase = true) -> "For security, sign out and use Forgot password to set a new one."
            raw.contains("rate limit", ignoreCase = true) || code == 429 -> "Too many attempts. Wait a minute and try again."
            raw.isNotBlank() -> raw
            else -> "Sign-in failed (HTTP $code)."
        }
    }
}

/** Thrown for network trouble so callers can show "offline" rather than a scary error. */
internal fun isNetworkProblem(e: Throwable) = e is IOException
