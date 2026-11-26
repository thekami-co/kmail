package com.kmail.thekami.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.kmail.thekami.data.prefs.LinkMode
import com.kmail.thekami.data.prefs.LinkPrefs

/** Opens a link the way the user chose in Settings. Never throws. */
object LinkOpener {
    fun open(context: Context, url: String) {
        val uri = runCatching { Uri.parse(url.trim()) }.getOrNull() ?: return
        val scheme = uri.scheme?.lowercase() ?: return
        if (scheme == "javascript" || scheme == "intent") return
        val web = scheme == "http" || scheme == "https"
        if (web && LinkPrefs.get(context).mode.value == LinkMode.IN_APP && openInApp(context, uri)) return
        openExternal(context, uri)
    }

    private fun openInApp(context: Context, uri: Uri): Boolean = try {
        val tab = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_ON)
            .build()
        if (context !is Activity) tab.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        tab.launchUrl(context, uri)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    private fun openExternal(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // no app can open this link
        } catch (e: SecurityException) {
            // blocked by the system
        }
    }
}
