package com.kmail.thekami.mail.gmail

import com.kmail.thekami.domain.model.FolderRole
import com.kmail.thekami.domain.model.RemoteFolder
import com.kmail.thekami.domain.model.RemoteMessageHeader
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeUtility
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.parser.Parser

internal data class GmailLabel(val id: String, val name: String, val type: String)

internal class GmailMeta(
    val gmailId: String,
    val internalDateMillis: Long,
    val labelIds: Set<String>,
    val snippet: String,
    val sizeBytes: Long,
    val subject: String,
    val fromName: String?,
    val fromAddress: String,
    val messageIdHeader: String?,
    val hasAttachments: Boolean,
)

internal fun GmailMeta.toHeader(uid: Long) = RemoteMessageHeader(
    uid = uid,
    messageIdHeader = messageIdHeader,
    subject = subject,
    fromName = fromName,
    fromAddress = fromAddress,
    snippet = snippet,
    receivedAtMillis = internalDateMillis,
    isRead = "UNREAD" !in labelIds,
    isStarred = "STARRED" in labelIds,
    hasAttachments = hasAttachments,
    sizeBytes = sizeBytes,
)

internal object GmailLabels {
    /** Virtual folder: every message that is not in Spam or Trash. Archiving moves a message here. */
    const val ALL_MAIL = "ALL_MAIL"

    private val SYSTEM = mapOf(
        "INBOX" to (FolderRole.INBOX to "Inbox"),
        "SENT" to (FolderRole.SENT to "Sent"),
        "DRAFT" to (FolderRole.DRAFTS to "Drafts"),
        "SPAM" to (FolderRole.SPAM to "Spam"),
        "TRASH" to (FolderRole.TRASH to "Trash"),
    )

    fun toFolders(labels: List<GmailLabel>): List<RemoteFolder> {
        val out = ArrayList<RemoteFolder>()
        for (l in labels) {
            val system = SYSTEM[l.id]
            when {
                system != null -> out += RemoteFolder(l.id, system.second, system.first)
                l.type == "user" -> out += RemoteFolder(l.id, l.name, FolderRole.CUSTOM)
            }
        }
        out += RemoteFolder(ALL_MAIL, "All Mail", FolderRole.ARCHIVE)
        return out
    }
}

internal object GmailJson {
    const val META_FIELDS =
        "id,labelIds,snippet,sizeEstimate,internalDate," +
            "payload(headers(name,value),parts(filename,parts(filename,parts(filename))))"

    fun parseLabels(root: JSONObject): List<GmailLabel> {
        val array = root.optJSONArray("labels") ?: return emptyList()
        val out = ArrayList<GmailLabel>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (id.isEmpty()) continue
            out += GmailLabel(id, o.optString("name", id), o.optString("type", "user"))
        }
        return out
    }

    fun parseMeta(o: JSONObject): GmailMeta? {
        val id = o.optString("id")
        if (id.isEmpty()) return null
        val internal = o.optString("internalDate").toLongOrNull() ?: return null
        val payload = o.optJSONObject("payload")
        val headers = HashMap<String, String>()
        payload?.optJSONArray("headers")?.let { arr ->
            for (i in 0 until arr.length()) {
                val h = arr.optJSONObject(i) ?: continue
                headers.putIfAbsent(h.optString("name").lowercase(), h.optString("value"))
            }
        }
        val from = headers["from"].orEmpty()
        val address = runCatching { InternetAddress.parseHeader(from, false).firstOrNull() }.getOrNull()
        val subjectRaw = headers["subject"].orEmpty()
        val subject = runCatching { MimeUtility.decodeText(subjectRaw) }.getOrDefault(subjectRaw)
            .replace(Regex("\\s+"), " ").trim()
        return GmailMeta(
            gmailId = id,
            internalDateMillis = internal,
            labelIds = strings(o.optJSONArray("labelIds")),
            snippet = Parser.unescapeEntities(o.optString("snippet"), false),
            sizeBytes = o.optLong("sizeEstimate", 0L),
            subject = subject,
            fromName = address?.personal?.takeIf { it.isNotBlank() },
            fromAddress = address?.address?.lowercase() ?: from.trim().ifEmpty { "unknown" },
            messageIdHeader = headers["message-id"]?.trim()?.ifEmpty { null },
            hasAttachments = hasFile(payload?.optJSONArray("parts")),
        )
    }

    private fun strings(array: JSONArray?): Set<String> {
        if (array == null) return emptySet()
        return (0 until array.length()).mapTo(HashSet()) { array.optString(it) }
    }

    private fun hasFile(parts: JSONArray?): Boolean {
        if (parts == null) return false
        for (i in 0 until parts.length()) {
            val p = parts.optJSONObject(i) ?: continue
            if (p.optString("filename").isNotEmpty() || hasFile(p.optJSONArray("parts"))) return true
        }
        return false
    }
}
