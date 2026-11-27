package com.kmail.thekami.data.prefs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ImageMode { ALWAYS, ASK, NEVER }

/** Decides what "the same sender" means when someone approves pictures. */
object SenderDomain {
    private val FREE_MAIL = setOf(
        "gmail.com", "googlemail.com", "yahoo.com", "yahoo.co.uk", "yahoo.co.in", "outlook.com",
        "hotmail.com", "hotmail.co.uk", "live.com", "msn.com", "icloud.com", "me.com", "proton.me",
        "protonmail.com", "aol.com", "yandex.com", "yandex.ru", "gmx.com", "mail.com", "zoho.com",
        "aster.cx", "tutanota.com", "tuta.com",
    )
    private val SECOND_LEVEL = setOf("co", "com", "org", "net", "gov", "edu", "ac")

    /** linkedin.com for jobs@e.linkedin.com, shop.com.bd for a@mail.shop.com.bd. Null if there is no domain. */
    fun registrable(address: String): String? {
        val host = address.substringAfterLast('@', "").trim().lowercase()
        val parts = host.split('.').filter { it.isNotEmpty() }
        if (parts.size < 2) return null
        val keep = if (parts.size >= 3 && parts.last().length == 2 && parts[parts.size - 2] in SECOND_LEVEL) 3 else 2
        return parts.takeLast(keep).joinToString(".")
    }

    /** "@linkedin.com" for a company, the full address for free mail (one person must not approve everyone). */
    fun trustKey(sender: String): String {
        val address = sender.trim().lowercase()
        val domain = registrable(address) ?: return address
        return "@$domain"
    }
}

object ImagePolicy {
    /** Whether pictures from the internet load as soon as a message opens. */
    fun initialAllow(mode: ImageMode, trusted: Set<String>, sender: String?): Boolean = when (mode) {
        ImageMode.ALWAYS -> true
        ImageMode.NEVER -> false
        ImageMode.ASK -> sender != null && sender.trim().lowercase().let {
            it in trusted || SenderDomain.trustKey(it) in trusted
        }
    }
}

/** Remote picture preference. Loading them tells the sender that and when you opened the message. */
@Singleton
class ImagePrefs @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("kmail_images", Context.MODE_PRIVATE)
    private val _mode = MutableStateFlow(readMode())
    val mode: StateFlow<ImageMode> = _mode.asStateFlow()
    private val _trusted = MutableStateFlow(readTrusted().sorted())

    /** Approved senders, sorted. Company entries look like "@linkedin.com". */
    val trustedSenders: StateFlow<List<String>> = _trusted.asStateFlow()

    fun setMode(mode: ImageMode) {
        prefs.edit().putString("mode", mode.name).apply()
        _mode.value = mode
    }

    fun trust(sender: String) = saveTrusted(readTrusted() + SenderDomain.trustKey(sender))

    fun untrust(entry: String) = saveTrusted(readTrusted() - entry)

    fun allowFor(sender: String?): Boolean = ImagePolicy.initialAllow(_mode.value, readTrusted(), sender)

    private fun saveTrusted(set: Set<String>) {
        prefs.edit().putStringSet("trusted", set).apply()
        _trusted.value = set.sorted()
    }

    private fun readTrusted(): Set<String> = prefs.getStringSet("trusted", emptySet()).orEmpty().toSet()

    private fun readMode(): ImageMode =
        runCatching { ImageMode.valueOf(prefs.getString("mode", null) ?: "ASK") }.getOrDefault(ImageMode.ASK)
}
