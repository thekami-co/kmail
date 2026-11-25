package com.kmail.thekami.mail.gmail

import com.kmail.thekami.data.db.GmailIdEntity
import com.kmail.thekami.domain.mail.MailResult
import com.kmail.thekami.domain.model.FolderRole
import com.kmail.thekami.domain.model.RemoteFolder
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GmailProviderTest {
    private val inbox = RemoteFolder("INBOX", "Inbox", FolderRole.INBOX)
    private val archive = RemoteFolder("ALL_MAIL", "All Mail", FolderRole.ARCHIVE)
    private val trash = RemoteFolder("TRASH", "Trash", FolderRole.TRASH)

    private fun provider(replies: List<Any>): Pair<GmailMailProvider, FakeHttp> {
        val dao = FakeGmailIdDao().apply { rows += GmailIdEntity("acc", 10L, "gid10") }
        val http = FakeHttp(replies.toMutableList())
        return GmailMailProvider("acc", "a@x.com", GmailApiClient(FakeTokens(), http), GmailIdMap(dao)) to http
    }

    @Test fun archiveRemovesInboxLabel() = runTest {
        val (p, http) = provider(listOf(status(204, "")))
        assertTrue(p.moveMessages(inbox, 1, listOf(10L), archive) is MailResult.Success)
        val call = http.calls.single()
        assertTrue(call.url.endsWith("messages/batchModify"))
        val body = JSONObject(call.body!!)
        assertEquals("INBOX", body.getJSONArray("removeLabelIds").getString(0))
        assertEquals(0, body.getJSONArray("addLabelIds").length())
    }

    @Test fun trashUsesTrashEndpoint() = runTest {
        val (p, http) = provider(listOf(ok("""{"id":"gid10"}""")))
        p.moveMessages(inbox, 1, listOf(10L), trash)
        assertTrue(http.calls.single().url.contains("messages/gid10/trash"))
    }

    @Test fun readAndStarMapToLabels() = runTest {
        val (p, http) = provider(listOf(status(204, "")))
        p.updateFlags(inbox, 1, listOf(10L), isRead = true, isStarred = true)
        val body = JSONObject(http.calls.single().body!!)
        assertEquals("UNREAD", body.getJSONArray("removeLabelIds").getString(0))
        assertEquals("STARRED", body.getJSONArray("addLabelIds").getString(0))
    }

    @Test fun unknownUidFailsWithoutNetwork() = runTest {
        val (p, http) = provider(emptyList())
        assertTrue(p.updateFlags(inbox, 1, listOf(99L), isRead = true, isStarred = null) is MailResult.Failure)
        assertTrue(http.calls.isEmpty())
    }

    @Test fun sendUploadsRawMimeWithBcc() = runTest {
        val (p, http) = provider(listOf(ok()))
        val raw = "To: a@x.com\r\nSubject: s\r\n\r\nhi".toByteArray()
        assertTrue(p.send(raw, "me@x.com", listOf("a@x.com", "hidden@x.com")) is MailResult.Success)
        assertTrue(http.calls.single().url.contains("/upload/gmail/v1/users/me/messages/send"))
        assertTrue(http.calls.single().body!!.startsWith("Bcc: hidden@x.com\r\n"))
    }

    @Test fun headersGetStableUid() = runTest {
        val list = ok("""{"messages":[{"id":"gnew"}]}""")
        val meta = ok(
            """{"id":"gnew","labelIds":["INBOX"],"snippet":"s","sizeEstimate":10,"internalDate":"1700000000000",
            "payload":{"headers":[{"name":"From","value":"b@x.com"},{"name":"Subject","value":"Yo"}]}}""",
        )
        val (p, _) = provider(listOf(list, meta))
        val snapshot = (p.fetchHeaders(inbox, null, 50) as MailResult.Success).value
        assertEquals(753_315_200L, snapshot.headers.single().uid)
        assertEquals(1L, snapshot.uidValidity)
        assertTrue(snapshot.headers.single().isRead)
    }
}
