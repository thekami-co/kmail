package com.kmail.thekami.ui.compose

import com.kmail.thekami.data.prefs.SignatureHtml

object ComposeBody {
    /** Empty lines for the cursor, then the signature, then the quoted text. (Kept for old callers.) */
    fun initial(signature: String, quote: String): String = buildString {
        append("\n\n")
        if (signature.isNotBlank()) append("-- \n").append(signature.trim())
        if (quote.isNotBlank()) {
            if (signature.isNotBlank()) append("\n\n")
            append(quote)
        }
    }

    /** What the editor starts with: empty, or room for a reply above the quoted text. The signature is not in here. */
    fun start(quote: String): String = if (quote.isBlank()) "" else "\n\n" + quote

    /** The plain text that is sent: the body, then the signature after the "-- " line. */
    fun plain(body: String, signature: String): String =
        if (signature.isBlank()) body else body.trimEnd() + "\n\n-- \n" + signature.trim()

    /** The HTML twin of the message: the typed text first, then the formatted signature. */
    fun html(body: String, signatureHtml: String): String {
        val text = SignatureHtml.escape(body.trimEnd())
            .replace("\r\n", "\n")
            .replace("\n", "<br>")
        val head = if (text.isBlank()) "" else "<div>$text</div><br>"
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\"></head><body>$head$signatureHtml</body></html>"
    }
}
