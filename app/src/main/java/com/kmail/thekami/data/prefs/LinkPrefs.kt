package com.kmail.thekami.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LinkMode { IN_APP, EXTERNAL }

/** Where web links open: inside the app (Custom Tabs) or in the phone's browser. */
class LinkPrefs private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("kmail_links", Context.MODE_PRIVATE)
    private val _mode = MutableStateFlow(readMode())
    val mode: StateFlow<LinkMode> = _mode.asStateFlow()

    fun setMode(mode: LinkMode) {
        prefs.edit().putString("mode", mode.name).apply()
        _mode.value = mode
    }

    private fun readMode(): LinkMode =
        runCatching { LinkMode.valueOf(prefs.getString("mode", null) ?: "IN_APP") }.getOrDefault(LinkMode.IN_APP)

    companion object {
        @Volatile private var instance: LinkPrefs? = null

        fun get(context: Context): LinkPrefs =
            instance ?: synchronized(this) {
                instance ?: LinkPrefs(context.applicationContext).also { instance = it }
            }
    }
}
