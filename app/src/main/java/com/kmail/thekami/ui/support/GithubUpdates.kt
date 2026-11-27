package com.kmail.thekami.ui.support

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.PackageInfoCompat
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class GithubUpdate(val versionName: String, val url: String)

/** Checks the newest GitHub release (tag kmail-vX.Y.Z) at most every 12 hours. */
object GithubUpdates {
    private const val API = "https://api.github.com/repos/thekami-co/kmail/releases/latest"
    private const val EVERY_MS = 12L * 60 * 60 * 1000
    private const val RETRY_MS = 60L * 60 * 1000
    private val TAG = Regex("""(\d{1,2})\.(\d{1,2})\.(\d{1,2})$""")

    private class Found(val code: Long, val name: String, val url: String)

    private val _update = MutableStateFlow<GithubUpdate?>(null)
    val update: StateFlow<GithubUpdate?> = _update.asStateFlow()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("github_update", Context.MODE_PRIVATE)

    fun currentName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: ""

    private fun currentCode(context: Context): Long = runCatching {
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
    }.getOrDefault(0L)

    /** Shows what was found earlier, so the red dot works offline too. */
    fun restore(context: Context) {
        val p = prefs(context)
        val code = p.getLong("code", 0L)
        val name = p.getString("name", null)
        val url = p.getString("url", null)
        _update.value =
            if (name != null && url != null && code > currentCode(context)) GithubUpdate(name, url) else null
    }

    suspend fun checkIfDue(context: Context) = withContext(Dispatchers.IO) {
        restore(context)
        val p = prefs(context)
        val now = System.currentTimeMillis()
        if (now - p.getLong("last", 0L) < EVERY_MS) return@withContext
        val found = runCatching { fetchLatest() }.getOrNull()
        if (found == null) {
            p.edit().putLong("last", now - EVERY_MS + RETRY_MS).apply()
            return@withContext
        }
        p.edit()
            .putLong("last", now)
            .putLong("code", found.code)
            .putString("name", found.name)
            .putString("url", found.url)
            .apply()
        restore(context)
    }

    private fun fetchLatest(): Found? {
        val conn = URL(API).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "Kmail-Android")
            if (conn.responseCode != 200) return null
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val parts = TAG.find(json.optString("tag_name"))?.groupValues?.drop(1)?.map { it.toInt() }
                ?: return null
            val url = json.optString("html_url").ifBlank { "$GITHUB_URL/releases/latest" }
            val (major, minor, patch) = parts
            return Found(major * 10_000L + minor * 100 + patch, "$major.$minor.$patch", url)
        } finally {
            conn.disconnect()
        }
    }
}

fun openReleasePage(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
