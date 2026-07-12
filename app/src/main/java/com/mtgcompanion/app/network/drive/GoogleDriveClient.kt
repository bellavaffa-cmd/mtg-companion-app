package com.mtgcompanion.app.network.drive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Minimal Google Drive v3 client over OkHttp using the `drive.file` scope. It only ever touches a
 * single "MTG Companion" folder and a `mtg-companion-backup.json` file inside it — the files the app
 * itself created — so it never needs broad Drive access. The caller supplies a fresh OAuth bearer
 * token (see DriveSyncManager).
 */
class GoogleDriveClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private companion object {
        const val FILES = "https://www.googleapis.com/drive/v3/files"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
        const val FOLDER_NAME = "MTG Companion"
        const val BACKUP_NAME = "mtg-companion-backup.json"
        val JSON = "application/json".toMediaType()
    }

    /** Find the app's Drive folder, creating it if missing. Returns the folder id. */
    suspend fun ensureFolder(token: String): String = withContext(Dispatchers.IO) {
        val q = "mimeType='$FOLDER_MIME' and name='$FOLDER_NAME' and trashed=false"
        firstFileId(token, q) ?: run {
            val body = JSONObject()
                .put("name", FOLDER_NAME)
                .put("mimeType", FOLDER_MIME)
                .toString()
                .toRequestBody(JSON)
            val req = Request.Builder().url("$FILES?fields=id").post(body).authorized(token).build()
            execJson(req).getString("id")
        }
    }

    /** Id of the backup file in [folderId], or null if it doesn't exist yet. */
    suspend fun findBackup(token: String, folderId: String): String? = withContext(Dispatchers.IO) {
        firstFileId(token, "name='$BACKUP_NAME' and '$folderId' in parents and trashed=false")
    }

    /** Download a file's text content. */
    suspend fun downloadText(token: String, fileId: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("$FILES/$fileId?alt=media").get().authorized(token).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Drive download failed (${resp.code})")
            resp.body?.string().orEmpty()
        }
    }

    /**
     * Write [content] to the backup file, creating it in [folderId] on first use. Returns the file id.
     */
    suspend fun uploadBackup(
        token: String,
        folderId: String,
        existingFileId: String?,
        content: String
    ): String = withContext(Dispatchers.IO) {
        val fileId = existingFileId ?: createEmptyBackup(token, folderId)
        val req = Request.Builder()
            .url("$UPLOAD/$fileId?uploadType=media")
            .patch(content.toRequestBody(JSON))
            .authorized(token)
            .build()
        execJson(req)
        fileId
    }

    private fun createEmptyBackup(token: String, folderId: String): String {
        val body = JSONObject()
            .put("name", BACKUP_NAME)
            .put("mimeType", "application/json")
            .put("parents", listOf(folderId).let { org.json.JSONArray(it) })
            .toString()
            .toRequestBody(JSON)
        val req = Request.Builder().url("$FILES?fields=id").post(body).authorized(token).build()
        return execJson(req).getString("id")
    }

    private fun firstFileId(token: String, query: String): String? {
        val url = FILES.toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("spaces", "drive")
            .addQueryParameter("fields", "files(id,name)")
            .build()
        val req = Request.Builder().url(url).get().authorized(token).build()
        val files = execJson(req).optJSONArray("files") ?: return null
        return if (files.length() > 0) files.getJSONObject(0).getString("id") else null
    }

    private fun execJson(req: Request): JSONObject = http.newCall(req).execute().use { resp ->
        val text = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) throw IOException("Drive request failed (${resp.code}): $text")
        if (text.isBlank()) JSONObject() else JSONObject(text)
    }

    private fun Request.Builder.authorized(token: String) = header("Authorization", "Bearer $token")
}
