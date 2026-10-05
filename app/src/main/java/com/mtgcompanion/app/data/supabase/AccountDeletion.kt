package com.mtgcompanion.app.data.supabase

import com.mtgcompanion.app.BuildConfig
import com.mtgcompanion.app.data.social.isMissingFunction
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

// Settings › Account & sync › Delete my account. The server side is public.delete_my_account()
// (supabase/migrations/20261006040000_delete_account.sql); the web twin is src/account/deleteAccount.ts
// on manabind.com/delete-account.

/** How a delete-account call ended. */
enum class AccountDeletion {
    /** The account and everything the server held for it are gone. */
    DELETED,
    /** The server doesn't have delete_my_account yet (its migration not run): nothing was deleted. */
    UNAVAILABLE,
    /** The server refused or failed: nothing should be assumed deleted. */
    FAILED
}

/** What the server's answer to delete_my_account means. [pgCode]: the JSON body's "code", if any. */
fun accountDeletionOutcome(status: Int, pgCode: String?): AccountDeletion = when {
    status in 200..299 -> AccountDeletion.DELETED
    isMissingFunction(status, pgCode) -> AccountDeletion.UNAVAILABLE
    else -> AccountDeletion.FAILED
}

/** The object paths to remove from the avatars bucket, from a Storage list of the user's folder. */
fun avatarPaths(userId: String, listJson: String): List<String> {
    val list = runCatching { JSONArray(listJson) }.getOrNull() ?: return emptyList()
    return (0 until list.length()).mapNotNull { i ->
        val o = list.optJSONObject(i) ?: return@mapNotNull null
        // Folders come back with a null id; there are none under a user's folder, but skip them anyway.
        if (o.isNull("id")) return@mapNotNull null
        o.optString("name").takeIf { it.isNotBlank() && !it.contains('/') }?.let { "$userId/$it" }
    }
}

/**
 * Removes the user's profile pictures through the Storage API (the server function can't always
 * do it from SQL). Best effort: a failure here never stops the account being deleted.
 */
internal fun deleteOwnAvatars(http: OkHttpClient, token: String, userId: String) {
    runCatching {
        val list = Request.Builder()
            .url(BuildConfig.SUPABASE_URL + "/storage/v1/object/list/avatars")
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer $token")
            .post(JSONObject().put("prefix", "$userId/").put("limit", 100).toString().toRequestBody(JSON_MEDIA))
            .build()
        val paths = http.newCall(list).execute().use { if (it.isSuccessful) avatarPaths(userId, it.body?.string().orEmpty()) else emptyList() }
        if (paths.isEmpty()) return@runCatching
        val delete = Request.Builder()
            .url(BuildConfig.SUPABASE_URL + "/storage/v1/object/avatars")
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer $token")
            .delete(JSONObject().put("prefixes", JSONArray(paths)).toString().toRequestBody(JSON_MEDIA))
            .build()
        http.newCall(delete).execute().close()
    }
}

/** Calls delete_my_account as the signed-in user. Throws IOException when the server can't be reached. */
internal fun callDeleteMyAccount(http: OkHttpClient, token: String): AccountDeletion {
    val request = Request.Builder()
        .url(BuildConfig.SUPABASE_URL + "/rest/v1/rpc/delete_my_account")
        .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
        .header("Authorization", "Bearer $token")
        .post("{}".toRequestBody(JSON_MEDIA))
        .build()
    return http.newCall(request).execute().use { response ->
        val text = response.body?.string().orEmpty()
        val code = runCatching { JSONObject(text).optString("code") }.getOrNull()?.ifBlank { null }
        accountDeletionOutcome(response.code, code)
    }
}
