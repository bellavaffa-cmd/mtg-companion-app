package com.mtgcompanion.app.tester

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.PixelCopy
import com.mtgcompanion.app.BuildConfig
import com.mtgcompanion.app.data.supabase.JSON_MEDIA
import com.mtgcompanion.app.data.supabase.SupabaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Reports from the tester app to the developer: problems, ideas, thumbs up and down, a build's
 * checklist, a scan that came out wrong, and crashes.
 *
 * Each is a file on the phone first and a row in the project's own Supabase (tester_reports) once it
 * has gone — so one written offline, signed out, or by an app that then crashed still arrives. The
 * table takes rows only from a signed-in account and gives none back to the app.
 */
class TesterReports(private val context: Context, private val auth: SupabaseAuth) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sending = Mutex()
    private val dir = File(context.filesDir, DIR)

    /** Reports still on the phone, waiting to go. */
    private val _waiting = MutableStateFlow(count())
    val waiting: StateFlow<Int> = _waiting.asStateFlow()

    /** What the last attempt to send said, for the tester tools. */
    private val _lastResult = MutableStateFlow<String?>(null)
    val lastResult: StateFlow<String?> = _lastResult.asStateFlow()

    private fun count(): Int = dir.listFiles { f -> f.name.endsWith(".json") }?.size ?: 0

    /**
     * Files a report and tries to send it. [kind]: bug, idea, feedback, checklist or scan. [extra]
     * joins the usual context (build, phone, screen); [picture] is a JPEG, already made small.
     */
    fun submit(kind: String, note: String, extra: JSONObject? = null, picture: ByteArray? = null) {
        val ctx = Tester.context(context)
        extra?.keys()?.forEach { ctx.put(it, extra.get(it)) }
        val report = JSONObject()
            .put("kind", kind)
            .put("note", note.take(4000))
            .put("screen", Tester.screen)
            .put("context", ctx)
            .put("log", TesterLog.tail())
            .put("createdAt", System.currentTimeMillis())
        if (picture != null) report.put("screenshot", Base64.encodeToString(picture, Base64.NO_WRAP))
        runCatching {
            dir.mkdirs()
            File(dir, "$kind-${System.currentTimeMillis()}.json").writeText(report.toString())
        }
        TesterLog.add("app", "Report filed: $kind")
        _waiting.value = count()
        flush()
    }

    /** Sends whatever is waiting. Leaves it for next time when signed out or offline. */
    fun flush() {
        scope.launch {
            sending.withLock {
                val files = dir.listFiles { f -> f.name.endsWith(".json") }?.sortedBy { it.name }.orEmpty()
                if (files.isEmpty()) return@withLock
                val token = runCatching { auth.accessToken() }.getOrNull()
                if (token == null) {
                    _lastResult.value = "Sign in to send ${files.size} waiting ${if (files.size == 1) "report" else "reports"}."
                    return@withLock
                }
                var sent = 0
                for (file in files) {
                    val ok = runCatching { send(JSONObject(file.readText()), token) }.getOrElse { e ->
                        _lastResult.value = "Couldn't send: ${e.message ?: e.javaClass.simpleName}"
                        false
                    }
                    if (!ok) break
                    file.delete()
                    sent++
                }
                _waiting.value = count()
                if (sent > 0) _lastResult.value = "Sent $sent ${if (sent == 1) "report" else "reports"}."
            }
        }
    }

    private fun send(report: JSONObject, token: String): Boolean {
        val ctx = report.optJSONObject("context") ?: JSONObject()
        val row = JSONObject()
            .put("kind", report.optString("kind", "bug"))
            .put("note", report.optString("note"))
            .put("screen", report.optString("screen"))
            .put("build", ctx.optInt("build", Tester.BUILD))
            .put("app_version", ctx.optString("version"))
            .put("device", ctx.optString("device"))
            .put("context", ctx)
            .put("log", report.optJSONArray("log") ?: org.json.JSONArray())
            .put("screenshot", if (report.has("screenshot")) report.getString("screenshot") else JSONObject.NULL)
            .put("reported_at_ms", report.optLong("createdAt"))
        val request = Request.Builder()
            .url(BuildConfig.SUPABASE_URL + "/rest/v1/tester_reports")
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer $token")
            .header("Prefer", "return=minimal")
            .post(row.toString().toRequestBody(JSON_MEDIA))
            .build()
        auth.http.newCall(request).execute().use { response ->
            if (response.isSuccessful) return true
            throw java.io.IOException("the server said ${response.code} ${response.body?.string().orEmpty().take(120)}")
        }
    }

    companion object {
        const val DIR = "tester_reports"

        /** Wide enough to read a screen by, small enough to send over a phone's connection. */
        private const val PICTURE_WIDTH = 720

        /** [bitmap] as a JPEG no wider than [PICTURE_WIDTH]. */
        fun jpeg(bitmap: Bitmap, quality: Int = 60): ByteArray {
            val scaled = if (bitmap.width > PICTURE_WIDTH) {
                Bitmap.createScaledBitmap(bitmap, PICTURE_WIDTH, bitmap.height * PICTURE_WIDTH / bitmap.width, true)
            } else bitmap
            return ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
        }

        /** A picture file as a small JPEG, or null when it isn't there or can't be read. */
        fun jpegOf(file: File): ByteArray? =
            if (!file.exists()) null else runCatching { BitmapFactory.decodeFile(file.path)?.let { jpeg(it) } }.getOrNull()

        /**
         * A picture of what's on the screen right now, handed to [done] on the main thread (null when
         * the window can't be copied). Taken before the report dialog opens, so it shows the problem
         * and not the dialog.
         */
        fun screenshot(activity: Activity, done: (Bitmap?) -> Unit) {
            val view = activity.window.decorView
            if (view.width <= 0 || view.height <= 0) return done(null)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            runCatching {
                PixelCopy.request(activity.window, bitmap, { result ->
                    done(if (result == PixelCopy.SUCCESS) bitmap else null)
                }, Handler(Looper.getMainLooper()))
            }.onFailure { done(null) }
        }
    }
}
