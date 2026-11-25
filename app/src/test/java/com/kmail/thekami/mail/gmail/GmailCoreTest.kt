package com.kmail.thekami.mail.gmail

import com.kmail.thekami.domain.mail.MailError
import com.kmail.thekami.domain.mail.MailResult
import com.kmail.thekami.domain.model.FolderRole
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GmailCoreTest {
    @Test fun uidGrowsWithTimeAndRoundTrips() {
        val a = GmailUid.candidate(1_700_000_000_000L)
        val b = GmailUid.candidate(1_700_000_100_000L)
        assertEquals(753_315_200L, a)
        assertTrue(b > a)
        assertEquals(1_700_000_000L, GmailUid.secondsFor(a))
        assertTrue(a in 1..GmailUid.MAX_UID)
    }

    @Test fun idMapIsStableAndBumpsOnCollision() = runTest {
        val map = GmailIdMap(FakeGmailIdDao())
        val first = map.assign("a", "g1", 1_700_000_000_000L)
        val second = map.assign("a", "g2", 1_700_000_000_000L)
        assertEquals(first, map.assign("a", "g1", 1_700_000_000_000L))
        assertEquals(first + 1, second)
        assertEquals("g2", map.gmailIds("a", listOf(second))[second])
        assertEquals(first, map.lookup("a", listOf("g1"))["g1"])
    }

    @Test fun labelsBecomeFolders() {
        val root = JSONObject(
            """{"labels":[{"id":"INBOX","name":"INBOX","type":"system"},{"id":"CATEGORY_SOCIAL","name":"x","type":"system"},
            {"id":"Label_1","name":"Work","type":"user"},{"id":"TRASH","name":"TRASH","type":"system"}]}""",
        )
        val folders = GmailLabels.toFolders(GmailJson.parseLabels(root))
        assertEquals(listOf("INBOX", "Label_1", "TRASH", "ALL_MAIL"), folders.map { it.remoteName })
        assertEquals(FolderRole.ARCHIVE, folders.last().role)
        assertEquals(FolderRole.CUSTOM, folders[1].role)
    }

    @Test fun metadataIsParsed() {
        val meta = GmailJson.parseMeta(
            JSONObject(
                """{"id":"18c0","labelIds":["INBOX","UNREAD"],"snippet":"Hello &#39;x&#39;","sizeEstimate":2048,
                "internalDate":"1700000000000","payload":{"headers":[{"name":"From","value":"Ana Bell <ANA@example.com>"},
                {"name":"Subject","value":"Hi   there"},{"name":"Message-ID","value":"<abc@example.com>"}],
                "parts":[{"filename":""},{"filename":"a.pdf"}]}}""",
            ),
        )!!
        assertEquals("Ana Bell", meta.fromName)
        assertEquals("ana@example.com", meta.fromAddress)
        assertEquals("Hi there", meta.subject)
        assertEquals("Hello 'x'", meta.snippet)
        assertTrue(meta.hasAttachments)
        assertFalse(meta.toHeader(5).isRead)
        assertNull(GmailJson.parseMeta(JSONObject("""{"id":"x"}""")))
    }

    @Test fun httpStatusesAreClassified() {
        assertTrue(GmailErrors.classify(200, ByteArray(0), null) is Outcome.Ok)
        assertTrue(GmailErrors.classify(401, ByteArray(0), null) is Outcome.Reauthorize)
        assertTrue(GmailErrors.classify(429, ByteArray(0), 3) is Outcome.Retry)
        assertTrue(GmailErrors.classify(503, ByteArray(0), null) is Outcome.Retry)
        val rate = """{"error":{"errors":[{"reason":"rateLimitExceeded"}]}}""".toByteArray()
        assertTrue(GmailErrors.classify(403, rate, null) is Outcome.Retry)
        val off = """{"error":{"errors":[{"reason":"accessNotConfigured"}]}}""".toByteArray()
        val fail = GmailErrors.classify(403, off, null) as Outcome.Fail
        assertEquals("forbidden_accessNotConfigured", fail.error.detail)
        assertEquals(MailError.Protocol("not_found"), (GmailErrors.classify(404, ByteArray(0), null) as Outcome.Fail).error)
    }

    @Test fun bccIsAddedOnlyForMissingRecipients() {
        val raw = "To: a@x.com,\r\n b@x.com\r\nCc: c@x.com\r\nSubject: s\r\n\r\nbody".toByteArray()
        val out = BccInjector.inject(raw, listOf("a@x.com", "b@x.com", "c@x.com", "secret@x.com")).toString(Charsets.UTF_8)
        assertTrue(out.startsWith("Bcc: secret@x.com\r\nTo: a@x.com"))
        assertTrue(BccInjector.inject(raw, listOf("a@x.com", "c@x.com")).contentEquals(raw))
    }

    @Test fun unauthorizedRefreshesTokenOnceThenFails() = runTest {
        val tokens = FakeTokens()
        val http = FakeHttp(mutableListOf(status(401), ok("""{"emailAddress":"a@x.com"}""")))
        val r = GmailApiClient(tokens, http).json("a@x.com", "GET", "profile")
        assertTrue(r is MailResult.Success)
        assertEquals(1, tokens.drops)
        assertEquals(listOf("token1", "token2"), http.calls.map { it.token })

        val failing = GmailApiClient(FakeTokens(), FakeHttp(mutableListOf(status(401), status(401))))
        assertTrue(failing.json("a@x.com", "GET", "profile").errorOrNull is MailError.Authentication)
    }

    @Test fun rateLimitIsRetriedThenGivesUp() = runTest {
        val http = FakeHttp(mutableListOf(status(429), status(429), ok("""{"a":1}""")))
        assertTrue(GmailApiClient(FakeTokens(), http).json("a@x.com", "GET", "profile") is MailResult.Success)
        assertEquals(3, http.calls.size)
        val dead = FakeHttp(mutableListOf(status(500), status(500), status(500), status(500)))
        assertTrue(GmailApiClient(FakeTokens(), dead).json("a@x.com", "GET", "profile").errorOrNull is MailError.Network)
    }
}
