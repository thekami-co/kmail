package com.kmail.thekami.mail.gmail

import com.kmail.thekami.domain.mail.MailError
import com.kmail.thekami.domain.mail.MailProvider
import com.kmail.thekami.domain.mail.MailResult
import com.kmail.thekami.domain.mail.ProviderCapabilities
import com.kmail.thekami.domain.model.FolderRole
import com.kmail.thekami.domain.model.FolderSnapshot
import com.kmail.thekami.domain.model.RemoteFlags
import com.kmail.thekami.domain.model.RemoteFolder
import com.kmail.thekami.domain.model.RemoteMessageHeader
import java.net.URLEncoder
import java.util.Base64
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject

internal inline fun <T, R> MailResult<T>.flatMap(block: (T) -> MailResult<R>): MailResult<R> = when (this) {
    is MailResult.Failure -> this
    is MailResult.Success -> block(value)
}

/**
 * Gmail REST API provider. Labels are folders, UIDs come from [GmailIdMap]. Needs only the
 * `gmail.modify` scope (read, modify, trash and send). Never logs tokens or message content.
 */
class GmailMailProvider(
    private val accountId: String,
    private val email: String,
    private val api: GmailApiClient,
    private val ids: GmailIdMap,
) : MailProvider {

    override val capabilities = ProviderCapabilities(supportsPush = false, supportsCategories = false)

    private class Listed(val ids: List<String>, val truncated: Boolean)

    override suspend fun testConnection(): MailResult<Unit> =
        api.json(email, "GET", "profile", listOf("fields" to "emailAddress"))
            .flatMap<JSONObject, Unit> { MailResult.Success(Unit) }

    override suspend fun listFolders(): MailResult<List<RemoteFolder>> =
        api.json(email, "GET", "labels", listOf("fields" to "labels(id,name,type)"))
            .flatMap<JSONObject, List<RemoteFolder>> {
                MailResult.Success(GmailLabels.toFolders(GmailJson.parseLabels(it)))
            }

    override suspend fun fetchHeaders(folder: RemoteFolder, afterUid: Long?, limit: Int): MailResult<FolderSnapshot> {
        val q = afterUid?.let { "after:${afterSeconds(it)}" }
        val listed = when (
            val r = listIds(
                baseLabels(folder), q,
                maxTotal = if (afterUid == null) limit else MAX_NEW,
                maxPages = if (afterUid == null) 1 else MAX_NEW_PAGES,
            )
        ) {
            is MailResult.Failure -> return r
            is MailResult.Success -> r.value
        }
        val known = ids.lookup(accountId, listed.ids)
        val wanted = listed.ids.filter { afterUid == null || (known[it] ?: Long.MAX_VALUE) > afterUid }
        val metas = when (val r = loadMetas(wanted)) {
            is MailResult.Failure -> return r
            is MailResult.Success -> r.value
        }
        var headers = toHeaders(metas)
        if (afterUid != null) headers = headers.filter { it.uid > afterUid }.sortedBy { it.uid }.take(limit)
        return MailResult.Success(FolderSnapshot(GmailUid.UID_VALIDITY, null, headers))
    }

    override suspend fun fetchOlder(
        folder: RemoteFolder,
        uidValidity: Long,
        beforeUid: Long,
        limit: Int,
    ): MailResult<List<RemoteMessageHeader>> {
        val q = "before:${GmailUid.secondsFor(beforeUid) + 1}"
        val listed = when (val r = listIds(baseLabels(folder), q, maxTotal = limit + OLDER_EXTRA, maxPages = 1)) {
            is MailResult.Failure -> return r
            is MailResult.Success -> r.value
        }
        val known = ids.lookup(accountId, listed.ids)
        val wanted = listed.ids.filter { (known[it] ?: Long.MIN_VALUE) < beforeUid }.take(limit)
        val metas = when (val r = loadMetas(wanted)) {
            is MailResult.Failure -> return r
            is MailResult.Success -> r.value
        }
        return MailResult.Success(toHeaders(metas).filter { it.uid < beforeUid })
    }

    override suspend fun fetchFlags(
        folder: RemoteFolder,
        uidValidity: Long,
        fromUid: Long,
    ): MailResult<List<RemoteFlags>> {
        val q = "after:${afterSeconds(fromUid)}"
        val base = baseLabels(folder)
        val all = when (val r = listIds(base, q, MAX_FLAGS, MAX_FLAG_PAGES)) {
            is MailResult.Failure -> return r
            is MailResult.Success -> r.value
        }
        val unread = when (val r = listIds(base + "UNREAD", q, MAX_FLAGS, MAX_FLAG_PAGES)) {
            is MailResult.Failure -> return r
            is MailResult.Success -> r.value
        }
        val starred = when (val r = listIds(base + "STARRED", q, MAX_FLAGS, MAX_FLAG_PAGES)) {
            is MailResult.Failure -> return r
            is MailResult.Success -> r.value
        }
        // A partial list would make cached mail look deleted, so never answer with one.
        if (all.truncated || unread.truncated || starred.truncated) {
            return MailResult.Failure(MailError.Protocol("too_many_messages"))
        }
        val known = ids.lookup(accountId, all.ids)
        val unreadSet = unread.ids.toHashSet()
        val starredSet = starred.ids.toHashSet()
        return MailResult.Success(
            all.ids.mapNotNull { id ->
                val uid = known[id] ?: return@mapNotNull null
                RemoteFlags(uid, isRead = id !in unreadSet, isStarred = id in starredSet)
            },
        )
    }

    override suspend fun fetchRawMessage(folder: RemoteFolder, uidValidity: Long, uid: Long): MailResult<ByteArray> {
        val id = ids.gmailIds(accountId, listOf(uid))[uid]
            ?: return MailResult.Failure(MailError.Protocol("unknown_uid"))
        return fetchRaw(id)
    }

    override suspend fun fetchRawBatch(
        folder: RemoteFolder,
        uidValidity: Long,
        uids: List<Long>,
        maxBytesEach: Long,
    ): MailResult<Map<Long, ByteArray>> {
        val map = ids.gmailIds(accountId, uids)
        val out = LinkedHashMap<Long, ByteArray>()
        for (chunk in uids.chunked(CONCURRENCY)) {
            val results = coroutineScope {
                chunk.map { uid -> async { uid to map[uid]?.let { fetchRaw(it) } } }.awaitAll()
            }
            for ((uid, r) in results) {
                val raw = (r as? MailResult.Success)?.value ?: continue
                if (raw.size <= maxBytesEach) out[uid] = raw
            }
        }
        return MailResult.Success(out)
    }

    override suspend fun updateFlags(
        folder: RemoteFolder,
        uidValidity: Long,
        uids: List<Long>,
        isRead: Boolean?,
        isStarred: Boolean?,
    ): MailResult<Unit> {
        val add = mutableListOf<String>()
        val remove = mutableListOf<String>()
        if (isRead != null) {
            if (isRead) remove += "UNREAD" else add += "UNREAD"
        }
        if (isStarred != null) {
            if (isStarred) add += "STARRED" else remove += "STARRED"
        }
        if (add.isEmpty() && remove.isEmpty()) return MailResult.Success(Unit)
        val gmailIds = resolve(uids) ?: return MailResult.Failure(MailError.Protocol("unknown_uid"))
        return modify(gmailIds, add, remove)
    }

    override suspend fun moveMessages(
        folder: RemoteFolder,
        uidValidity: Long,
        uids: List<Long>,
        destination: RemoteFolder,
    ): MailResult<Unit> {
        val gmailIds = resolve(uids) ?: return MailResult.Failure(MailError.Protocol("unknown_uid"))
        if (destination.role == FolderRole.TRASH) return postEach(gmailIds, "trash")
        if (folder.role == FolderRole.TRASH) {
            val r = postEach(gmailIds, "untrash")
            if (r is MailResult.Failure) return r
        }
        val add = mutableListOf<String>()
        val remove = mutableListOf<String>()
        when {
            destination.remoteName == GmailLabels.ALL_MAIL -> remove += "INBOX" // archive
            destination.role == FolderRole.SPAM -> { add += "SPAM"; remove += "INBOX" }
            else -> add += destination.remoteName
        }
        val keepsSource = folder.role == FolderRole.SENT || folder.role == FolderRole.DRAFTS ||
            folder.role == FolderRole.TRASH || folder.remoteName == GmailLabels.ALL_MAIL
        if (!keepsSource && destination.remoteName != GmailLabels.ALL_MAIL && folder.remoteName != destination.remoteName) {
            remove += folder.remoteName
        }
        if (add.isEmpty() && remove.isEmpty()) return MailResult.Success(Unit)
        return modify(gmailIds, add.distinct(), remove.distinct())
    }

    override suspend fun send(rawMime: ByteArray, envelopeFrom: String, recipients: List<String>): MailResult<Unit> =
        api.rawCall(email, "POST", GmailApiClient.SEND_UPLOAD_URL, BccInjector.inject(rawMime, recipients), "message/rfc822")
            .flatMap<ByteArray, Unit> { MailResult.Success(Unit) }

    /** Gmail files sent mail under Sent by itself; saving another copy would duplicate it. */
    override suspend fun appendMessage(folder: RemoteFolder, rawMime: ByteArray, isRead: Boolean): MailResult<Unit> =
        MailResult.Success(Unit)

    // ---- helpers ----

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun baseLabels(folder: RemoteFolder): List<String> =
        if (folder.remoteName == GmailLabels.ALL_MAIL) emptyList() else listOf(folder.remoteName)

    /** One hour of slack: a bumped UID can be a little later than the real receive time. */
    private fun afterSeconds(uid: Long): Long = (GmailUid.secondsFor(uid) - AFTER_SLACK_SECONDS).coerceAtLeast(0)

    private suspend fun resolve(uids: List<Long>): List<String>? {
        val map = ids.gmailIds(accountId, uids)
        return uids.map { map[it] ?: return null }
    }

    private suspend fun listIds(
        labels: List<String>,
        q: String?,
        maxTotal: Int,
        maxPages: Int,
    ): MailResult<Listed> {
        val out = ArrayList<String>()
        var pageToken: String? = null
        var pages = 0
        do {
            val query = mutableListOf(
                "maxResults" to minOf(500, maxTotal - out.size).toString(),
                "fields" to "messages/id,nextPageToken",
            )
            labels.forEach { query += "labelIds" to it }
            if (q != null) query += "q" to q
            pageToken?.let { query += "pageToken" to it }
            val root = when (val r = api.json(email, "GET", "messages", query)) {
                is MailResult.Failure -> return r
                is MailResult.Success -> r.value
            }
            root.optJSONArray("messages")?.let { arr ->
                for (i in 0 until arr.length()) arr.optJSONObject(i)?.optString("id")?.takeIf { it.isNotEmpty() }?.let(out::add)
            }
            pageToken = root.optString("nextPageToken").ifEmpty { null }
            pages++
        } while (pageToken != null && pages < maxPages && out.size < maxTotal)
        return MailResult.Success(Listed(out, pageToken != null))
    }

    private suspend fun fetchMeta(id: String): MailResult<GmailMeta?> =
        when (val r = api.json(email, "GET", "messages/${enc(id)}", listOf("format" to "full", "fields" to GmailJson.META_FIELDS))) {
            is MailResult.Failure ->
                if (r.error is MailError.Protocol && r.error.detail == "not_found") MailResult.Success<GmailMeta?>(null) else r
            is MailResult.Success -> MailResult.Success(GmailJson.parseMeta(r.value))
        }

    private suspend fun loadMetas(wanted: List<String>): MailResult<List<GmailMeta>> = coroutineScope {
        val out = ArrayList<GmailMeta>(wanted.size)
        for (chunk in wanted.chunked(CONCURRENCY)) {
            val results = chunk.map { id -> async { fetchMeta(id) } }.awaitAll()
            for (r in results) {
                when (r) {
                    is MailResult.Failure -> return@coroutineScope r
                    is MailResult.Success -> r.value?.let { out.add(it) }
                }
            }
        }
        MailResult.Success(out)
    }

    private suspend fun toHeaders(metas: List<GmailMeta>): List<RemoteMessageHeader> =
        metas.sortedBy { it.internalDateMillis }
            .map { it.toHeader(ids.assign(accountId, it.gmailId, it.internalDateMillis)) }

    private suspend fun fetchRaw(id: String): MailResult<ByteArray> =
        api.json(email, "GET", "messages/${enc(id)}", listOf("format" to "raw", "fields" to "raw"))
            .flatMap<JSONObject, ByteArray> { o ->
                val raw = o.optString("raw")
                if (raw.isEmpty()) {
                    MailResult.Failure(MailError.Protocol("empty_raw"))
                } else {
                    try {
                        MailResult.Success(Base64.getUrlDecoder().decode(raw))
                    } catch (e: IllegalArgumentException) {
                        MailResult.Failure(MailError.Protocol("bad_base64"))
                    }
                }
            }

    private suspend fun modify(gmailIds: List<String>, add: List<String>, remove: List<String>): MailResult<Unit> {
        for (chunk in gmailIds.chunked(1000)) {
            val body = JSONObject()
                .put("ids", JSONArray(chunk))
                .put("addLabelIds", JSONArray(add))
                .put("removeLabelIds", JSONArray(remove))
            val r = api.json(email, "POST", "messages/batchModify", body = body)
            if (r is MailResult.Failure) return r
        }
        return MailResult.Success(Unit)
    }

    private suspend fun postEach(gmailIds: List<String>, action: String): MailResult<Unit> {
        for (id in gmailIds) {
            val r = api.json(email, "POST", "messages/${enc(id)}/$action", listOf("fields" to "id"))
            if (r is MailResult.Failure) return r
        }
        return MailResult.Success(Unit)
    }

    private companion object {
        const val CONCURRENCY = 6
        const val AFTER_SLACK_SECONDS = 3600L
        const val MAX_NEW = 2000
        const val MAX_NEW_PAGES = 4
        const val OLDER_EXTRA = 25
        const val MAX_FLAGS = 5000
        const val MAX_FLAG_PAGES = 10
    }
}
