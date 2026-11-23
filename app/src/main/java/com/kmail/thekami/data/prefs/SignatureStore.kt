package com.kmail.thekami.data.prefs

import android.content.Context
import com.kmail.thekami.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [defaultText] is the mobile signature (the footer), [enabled] switches it on or off.
 * [perAccount] holds the plain text of each address's email signature, [htmlPerAccount] its formatted version.
 */
data class SignatureState(
    val enabled: Boolean,
    val defaultText: String,
    val perAccount: Map<String, String>,
    val htmlPerAccount: Map<String, String> = emptyMap(),
    val names: Map<String, String> = emptyMap(),
)

object SignatureHtml {
    fun escape(s: String): String = buildString {
        for (c in s) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(c)
            }
        }
    }

    /** Plain text as HTML: one block per line. */
    fun fromPlain(text: String): String {
        if (text.isBlank()) return ""
        return text.trim().split("\n").joinToString("") { line ->
            if (line.isBlank()) "<div><br></div>" else "<div>${escape(line)}</div>"
        }
    }
}

/**
 * Signatures. Each address has an email signature (plain text and HTML), and there is one mobile
 * signature that is added below it. Old plain-text signatures keep working: the HTML is made from them.
 */
@Singleton
class SignatureStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("kmail_signature", Context.MODE_PRIVATE)
    private val fallback = context.getString(R.string.compose_signature)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<SignatureState> = _state.asStateFlow()

    private fun footerText(s: SignatureState): String = if (s.enabled) s.defaultText.trim() else ""

    /** Plain text for the composer: the address's own signature, then the mobile signature. */
    fun textFor(accountId: String?): String {
        val s = _state.value
        val own = accountId?.let { s.perAccount[it] }.orEmpty().trim()
        return listOf(own, footerText(s)).filter { it.isNotEmpty() }.joinToString("\n\n")
    }

    /** The same signature as HTML, for sending formatted mail. */
    fun htmlFor(accountId: String?): String {
        val s = _state.value
        val own = accountId?.let { emailHtmlFor(it) }.orEmpty()
        val footer = SignatureHtml.fromPlain(footerText(s))
        val gap = if (own.isNotEmpty() && footer.isNotEmpty()) "<div><br></div>" else ""
        return own + gap + footer
    }

    fun emailHtmlFor(accountId: String): String {
        val s = _state.value
        return s.htmlPerAccount[accountId] ?: SignatureHtml.fromPlain(s.perAccount[accountId].orEmpty())
    }

    fun displayNameFor(accountId: String): String? = _state.value.names[accountId]?.takeIf { it.isNotBlank() }

    fun setEnabled(on: Boolean) {
        prefs.edit().putBoolean("enabled", on).apply()
        _state.value = _state.value.copy(enabled = on)
    }

    /** The mobile signature text. */
    fun setDefault(text: String) {
        prefs.edit().putString("default", text).apply()
        _state.value = _state.value.copy(defaultText = text)
    }

    /** An empty [plain] removes the signature of this address. */
    fun setEmailSignature(accountId: String, plain: String, html: String) {
        val trimmed = plain.trim()
        val edit = prefs.edit()
        val texts = _state.value.perAccount.toMutableMap()
        val htmls = _state.value.htmlPerAccount.toMutableMap()
        if (trimmed.isEmpty()) {
            edit.remove("acc_$accountId").remove("html_$accountId")
            texts.remove(accountId)
            htmls.remove(accountId)
        } else {
            edit.putString("acc_$accountId", trimmed).putString("html_$accountId", html)
            texts[accountId] = trimmed
            htmls[accountId] = html
        }
        edit.apply()
        _state.value = _state.value.copy(perAccount = texts, htmlPerAccount = htmls)
    }

    fun setDisplayName(accountId: String, name: String) {
        val trimmed = name.trim()
        val edit = prefs.edit()
        val map = _state.value.names.toMutableMap()
        if (trimmed.isEmpty()) {
            edit.remove("name_$accountId")
            map.remove(accountId)
        } else {
            edit.putString("name_$accountId", trimmed)
            map[accountId] = trimmed
        }
        edit.apply()
        _state.value = _state.value.copy(names = map)
    }

    private fun collect(prefix: String): Map<String, String> =
        prefs.all.filterKeys { it.startsWith(prefix) }
            .mapNotNull { (key, value) -> (value as? String)?.let { key.removePrefix(prefix) to it } }
            .toMap()

    private fun read(): SignatureState = SignatureState(
        enabled = prefs.getBoolean("enabled", true),
        defaultText = prefs.getString("default", null) ?: fallback,
        perAccount = collect("acc_"),
        htmlPerAccount = collect("html_"),
        names = collect("name_"),
    )
}
