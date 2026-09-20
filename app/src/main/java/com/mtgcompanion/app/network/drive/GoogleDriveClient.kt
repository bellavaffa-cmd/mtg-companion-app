package com.mtgcompanion.app.network.drive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Minimal read-only Google Drive v3 client over OkHttp using the `drive.file` scope, for importing
 * the backup left by the retired Drive sync: a `mtg-companion-backup.json` file inside the
 * "MTG Companion" folder the app created. The caller supplies a fresh OAuth bearer token
 * (see DriveImporter).
 */
class GoogleDriveClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private companion object {
        const val FILES = "https://www.googleapis.com/drive/v3/files"
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
        // Deliberately still the old app name: this is the folder people's existing backups
        // are already sitting in. Rename it and the app makes a new empty one and can no longer
        // see anything backed up before the rename.
        const val FOLDER_NAME = "MTG Companion"
        const val BACKUP_NAME = "mtg-companion-backup.json"
    }

    /** Id of the app's Drive folder, or null if it was never created. */
    suspend fun findFolder(token: String): String? = withContext(Dispatchers.IO) {
        firstFileId(token, "mimeType='$FOLDER_MIME' and name='$FOLDER_NAME' and trashed=false")
    }

    /** Id of the backup file in [folderId], or null if it doesn't exist. */
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
