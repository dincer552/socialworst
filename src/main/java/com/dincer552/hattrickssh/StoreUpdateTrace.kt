package com.dincer552.hattrickssh

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Structured log of the update pipeline (download → validate → install).
 * Survives process death via SharedPreferences; exportable as JSON for debugging.
 */
object StoreUpdateTrace {
    private const val TAG = "StoreUpdateTrace"
    private const val PREFS = "store_update_trace"
    private const val KEY_SESSIONS = "sessions"
    private const val MAX_SESSIONS = 8
    private const val MAX_EVENTS = 80

    @Volatile private var activeSessionId: String? = null
    @Volatile private var sessionStartedAt: Long = 0L
    private val lock = Any()

    fun startSession(
        context: Context,
        appId: String,
        packageName: String,
        versionName: String? = null,
        versionCode: Long? = null,
        reason: String = "user_tap"
    ): String {
        val id = UUID.randomUUID().toString().take(8)
        val now = System.currentTimeMillis()
        synchronized(lock) {
            activeSessionId = id
            sessionStartedAt = now
            val session = JSONObject()
                .put("id", id)
                .put("startedAt", iso(now))
                .put("startedAtMs", now)
                .put("appId", appId)
                .put("packageName", packageName)
                .put("versionName", versionName ?: JSONObject.NULL)
                .put("versionCode", versionCode ?: JSONObject.NULL)
                .put("reason", reason)
                .put("storeVersion", BuildConfig.VERSION_NAME)
                .put("storeVersionCode", BuildConfig.VERSION_CODE)
                .put("sdk", Build.VERSION.SDK_INT)
                .put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
                .put("events", JSONArray())
            persistSession(context, session, prepend = true)
        }
        event(context, "session_start", "ok", "Pipeline started ($reason)")
        return id
    }

    fun event(
        context: Context,
        stage: String,
        status: String,
        message: String = "",
        code: Int? = null,
        extra: Map<String, Any?> = emptyMap()
    ) {
        val now = System.currentTimeMillis()
        val sid = activeSessionId
        Log.i(TAG, "[$sid] $stage/$status code=$code $message")
        synchronized(lock) {
            if (sid == null) return
            val sessions = loadSessions(context)
            val session = (0 until sessions.length())
                .map { sessions.getJSONObject(it) }
                .firstOrNull { it.optString("id") == sid }
                ?: return
            val events = session.optJSONArray("events") ?: JSONArray()
            val elapsed = if (sessionStartedAt > 0) now - sessionStartedAt else 0L
            val entry = JSONObject()
                .put("t", iso(now))
                .put("ms", now)
                .put("elapsedMs", elapsed)
                .put("stage", stage)
                .put("status", status)
                .put("message", message)
            if (code != null) entry.put("code", code)
            extra.forEach { (k, v) ->
                when (v) {
                    null -> entry.put(k, JSONObject.NULL)
                    is Number, is Boolean, is String -> entry.put(k, v)
                    else -> entry.put(k, v.toString())
                }
            }
            events.put(entry)
            while (events.length() > MAX_EVENTS) {
                // drop oldest
                val trimmed = JSONArray()
                for (i in 1 until events.length()) trimmed.put(events.get(i))
                session.put("events", trimmed)
                break
            }
            if (events.length() <= MAX_EVENTS) session.put("events", events)
            session.put("lastStage", stage)
            session.put("lastStatus", status)
            session.put("updatedAt", iso(now))
            rewriteSession(context, session)
        }
    }

    fun endSession(context: Context, status: String, message: String = "") {
        event(context, "session_end", status, message)
        synchronized(lock) {
            activeSessionId = null
            sessionStartedAt = 0L
        }
    }

    fun exportJson(context: Context): String {
        val root = JSONObject()
            .put("exportedAt", iso(System.currentTimeMillis()))
            .put("storeVersion", BuildConfig.VERSION_NAME)
            .put("storeVersionCode", BuildConfig.VERSION_CODE)
            .put("sdk", Build.VERSION.SDK_INT)
            .put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            .put("activeSessionId", activeSessionId ?: JSONObject.NULL)
            .put("sessions", loadSessions(context))
        return root.toString(2)
    }

    fun clear(context: Context) {
        synchronized(lock) {
            activeSessionId = null
            sessionStartedAt = 0L
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_SESSIONS, "[]").apply()
        }
    }

    fun shareIntent(context: Context): Intent {
        val json = exportJson(context)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, "My App Store update log")
            putExtra(Intent.EXTRA_TEXT, json)
        }
    }

    private fun loadSessions(context: Context): JSONArray {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SESSIONS, "[]") ?: "[]"
        return try {
            JSONArray(raw)
        } catch (_: Exception) {
            JSONArray()
        }
    }

    private fun persistSession(context: Context, session: JSONObject, prepend: Boolean) {
        val sessions = loadSessions(context)
        val next = JSONArray()
        if (prepend) next.put(session)
        for (i in 0 until sessions.length()) {
            if (next.length() >= MAX_SESSIONS) break
            next.put(sessions.getJSONObject(i))
        }
        if (!prepend && next.length() < MAX_SESSIONS) next.put(session)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_SESSIONS, next.toString()).apply()
    }

    private fun rewriteSession(context: Context, session: JSONObject) {
        val sessions = loadSessions(context)
        val next = JSONArray()
        val id = session.optString("id")
        var replaced = false
        for (i in 0 until sessions.length()) {
            val s = sessions.getJSONObject(i)
            if (s.optString("id") == id) {
                next.put(session)
                replaced = true
            } else {
                next.put(s)
            }
        }
        if (!replaced) next.put(session)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_SESSIONS, next.toString()).apply()
    }

    private fun iso(ms: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(ms))
    }
}
