package com.mtgcompanion.app.tester

import android.app.Application
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Interceptor
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter

/** One thing the app did. [kind]: screen, tap, net, sync, remote, scan or app. */
data class LogLine(val at: Long, val kind: String, val text: String, val times: Int = 1)

/**
 * The last things the app did, newest last — sent with every report and crash, so a problem arrives
 * with what led up to it. Held in memory only: it's a trail, not a record.
 */
object TesterLog {
    /** Enough to cover the minute or two before a problem without becoming a diary. */
    private const val KEEP = 250

    private val lines = ArrayDeque<LogLine>()
    private val _flow = MutableStateFlow<List<LogLine>>(emptyList())
    val flow: StateFlow<List<LogLine>> = _flow.asStateFlow()

    fun add(kind: String, text: String) {
        if (!Tester.on) return
        synchronized(lines) {
            val last = lines.lastOrNull()
            // The same thing again (a heartbeat, a burst of taps) counts up rather than filling the trail.
            if (last != null && last.kind == kind && last.text == text) {
                lines[lines.lastIndex] = last.copy(at = System.currentTimeMillis(), times = last.times + 1)
            } else {
                lines.addLast(LogLine(System.currentTimeMillis(), kind, text.take(300)))
                while (lines.size > KEEP) lines.removeFirst()
            }
            _flow.value = lines.toList()
        }
    }

    /** The newest [count] lines, oldest first, as a report carries them. */
    fun tail(count: Int = 60): JSONArray = JSONArray().also { arr ->
        synchronized(lines) { lines.toList() }.takeLast(count).forEach {
            arr.put(JSONObject().put("at", it.at).put("kind", it.kind).put("text", it.text).put("times", it.times))
        }
    }
}

/**
 * Notes every request the app makes: where to, what came back, how long it took. The address is
 * kept without its query — that's where search text and ids ride, and no report needs them.
 */
class TesterNetInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val where = "${request.method} ${request.url.host}${request.url.encodedPath}"
        val started = System.currentTimeMillis()
        try {
            val response = chain.proceed(request)
            val took = System.currentTimeMillis() - started
            // Card pictures and symbols are most of the traffic and none of the interest, unless they fail.
            val picture = request.url.host.endsWith("scryfall.io")
            if (!response.isSuccessful || !picture) TesterLog.add("net", "$where → ${response.code} (${took} ms)")
            return response
        } catch (e: IOException) {
            TesterLog.add("net", "$where → failed: ${e.javaClass.simpleName} ${e.message.orEmpty()}")
            throw e
        }
    }
}

/**
 * A crash is written down before the app dies and sent the next time it opens (see
 * [TesterReports.flush]). Written straight to a file: by now nothing else can be counted on.
 */
object TesterCrashes {
    fun install(app: Application) {
        val before = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                val report = JSONObject()
                    .put("kind", "crash")
                    .put("note", "${error.javaClass.name}: ${error.message.orEmpty()}".take(500))
                    .put("screen", Tester.screen)
                    .put("context", Tester.context(app).put("thread", thread.name).put("trace", trace.take(12_000)))
                    .put("log", TesterLog.tail())
                    .put("createdAt", System.currentTimeMillis())
                val dir = File(app.filesDir, TesterReports.DIR).apply { mkdirs() }
                File(dir, "crash-${System.currentTimeMillis()}.json").writeText(report.toString())
            }
            // Then crash as it would have: the system's dialog, and the process gone.
            before?.uncaughtException(thread, error)
        }
    }
}
