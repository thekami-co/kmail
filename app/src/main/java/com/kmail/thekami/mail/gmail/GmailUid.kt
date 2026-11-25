package com.kmail.thekami.mail.gmail

import com.kmail.thekami.data.db.GmailIdDao
import com.kmail.thekami.data.db.GmailIdEntity
import com.kmail.thekami.domain.model.MAX_IMAP_UINT32
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Gmail ids are 64-bit; the app's UIDs are 32-bit. A UID is the receive time in seconds since
 * 2000-01-01 (bumped by one on collision), so UIDs grow with time like IMAP UIDs do.
 */
internal object GmailUid {
    /** Gmail has no UID reset, so the validity never changes. */
    const val UID_VALIDITY = 1L
    const val MAX_UID = MAX_IMAP_UINT32
    private const val EPOCH_OFFSET_SECONDS = 946_684_800L

    fun candidate(internalDateMillis: Long): Long =
        (internalDateMillis / 1000 - EPOCH_OFFSET_SECONDS).coerceIn(1L, MAX_UID - 1_000_000L)

    fun secondsFor(uid: Long): Long = uid + EPOCH_OFFSET_SECONDS
}

@Singleton
class GmailIdMap @Inject constructor(private val dao: GmailIdDao) {
    private val mutex = Mutex()

    suspend fun lookup(accountId: String, gmailIds: Collection<String>): Map<String, Long> {
        val out = HashMap<String, Long>()
        for (chunk in gmailIds.distinct().chunked(CHUNK)) {
            dao.byGmailIds(accountId, chunk).forEach { out[it.gmailId] = it.uid }
        }
        return out
    }

    suspend fun gmailIds(accountId: String, uids: Collection<Long>): Map<Long, String> {
        val out = HashMap<Long, String>()
        for (chunk in uids.distinct().chunked(CHUNK)) {
            dao.byUids(accountId, chunk).forEach { out[it.uid] = it.gmailId }
        }
        return out
    }

    /** Returns the existing UID of [gmailId] or creates one. Call in ascending date order. */
    suspend fun assign(accountId: String, gmailId: String, internalDateMillis: Long): Long = mutex.withLock {
        dao.byGmailIds(accountId, listOf(gmailId)).firstOrNull()?.let { return@withLock it.uid }
        var uid = GmailUid.candidate(internalDateMillis)
        while (uid <= GmailUid.MAX_UID) {
            if (dao.insert(GmailIdEntity(accountId, uid, gmailId)) != -1L) return@withLock uid
            uid++
        }
        error("uid space exhausted")
    }

    private companion object {
        const val CHUNK = 400
    }
}
