package com.kmail.thekami.mail.gmail

import com.kmail.thekami.data.db.GmailIdDao
import com.kmail.thekami.data.db.GmailIdEntity
import com.kmail.thekami.domain.mail.MailError
import com.kmail.thekami.domain.mail.MailResult
import java.io.IOException

class FakeGmailIdDao : GmailIdDao {
    val rows = mutableListOf<GmailIdEntity>()
    override suspend fun byGmailIds(accountId: String, gmailIds: List<String>) =
        rows.filter { it.accountId == accountId && it.gmailId in gmailIds }
    override suspend fun byUids(accountId: String, uids: List<Long>) =
        rows.filter { it.accountId == accountId && it.uid in uids }
    override suspend fun insert(row: GmailIdEntity): Long {
        if (rows.any { it.accountId == row.accountId && (it.uid == row.uid || it.gmailId == row.gmailId) }) return -1
        rows += row
        return rows.size.toLong()
    }
}

class Call(val method: String, val url: String, val token: String, val body: String?)

class FakeHttp(private val replies: MutableList<Any>) : GmailHttp {
    val calls = mutableListOf<Call>()
    override suspend fun execute(method: String, url: String, accessToken: String, body: ByteArray?, contentType: String?): HttpReply {
        calls += Call(method, url, accessToken, body?.toString(Charsets.UTF_8))
        val next = replies.removeAt(0)
        if (next is IOException) throw next
        return next as HttpReply
    }
}

class FakeTokens : AccessTokenSource {
    var issued = 0
    var drops = 0
    override suspend fun accessToken(email: String): MailResult<String> = MailResult.Success("token${++issued}")
    override fun drop(email: String) { drops++ }
}

fun ok(json: String = "{}") = HttpReply(200, json.toByteArray(), null)
fun status(code: Int, json: String = "{}") = HttpReply(code, json.toByteArray(), null)

val MailResult<*>.errorOrNull: MailError? get() = (this as? MailResult.Failure)?.error
