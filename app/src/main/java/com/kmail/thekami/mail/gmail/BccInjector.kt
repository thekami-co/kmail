package com.kmail.thekami.mail.gmail

import jakarta.mail.internet.InternetAddress

/**
 * The app's MimeBuilder never writes a Bcc header, but the Gmail API takes recipients from the
 * message headers. This adds a Bcc header for every recipient not already in To, Cc or Bcc.
 * Gmail removes the Bcc header from the copies it delivers.
 */
internal object BccInjector {
    fun inject(raw: ByteArray, recipients: List<String>): ByteArray {
        val known = HashSet<String>()
        for (line in unfoldedHeaderLines(raw)) {
            val name = line.substringBefore(':', "").trim().lowercase()
            if (name == "to" || name == "cc" || name == "bcc") {
                runCatching { InternetAddress.parse(line.substringAfter(':'), false) }.getOrNull()
                    ?.forEach { it.address?.let { a -> known += a.lowercase() } }
            }
        }
        val extra = recipients
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.none { c -> c == '\r' || c == '\n' || c == ',' } }
            .filter { it.lowercase() !in known }
            .distinctBy { it.lowercase() }
        if (extra.isEmpty()) return raw
        return ("Bcc: " + extra.joinToString(", ") + "\r\n").toByteArray(Charsets.UTF_8) + raw
    }

    private fun unfoldedHeaderLines(raw: ByteArray): List<String> {
        var end = raw.size
        for (i in 0..raw.size - 4) {
            if (raw[i] == '\r'.code.toByte() && raw[i + 1] == '\n'.code.toByte() &&
                raw[i + 2] == '\r'.code.toByte() && raw[i + 3] == '\n'.code.toByte()
            ) {
                end = i
                break
            }
        }
        return String(raw, 0, end, Charsets.ISO_8859_1)
            .replace(Regex("\r?\n[ \t]+"), " ")
            .split(Regex("\r?\n"))
    }
}
