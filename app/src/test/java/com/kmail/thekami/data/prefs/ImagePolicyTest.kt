package com.kmail.thekami.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImagePolicyTest {
    private val trusted = setOf("news@shop.com")

    @Test
    fun alwaysAndNeverIgnoreTheSender() {
        assertTrue(ImagePolicy.initialAllow(ImageMode.ALWAYS, emptySet(), null))
        assertFalse(ImagePolicy.initialAllow(ImageMode.NEVER, trusted, "news@shop.com"))
    }

    @Test
    fun askLoadsOnlyForTrustedSendersIgnoringCaseAndSpaces() {
        assertTrue(ImagePolicy.initialAllow(ImageMode.ASK, trusted, " News@Shop.com "))
        assertFalse(ImagePolicy.initialAllow(ImageMode.ASK, trusted, "other@x.com"))
        assertFalse(ImagePolicy.initialAllow(ImageMode.ASK, trusted, null))
    }

    @Test
    fun companyApprovalCoversEveryAddressOfThatCompany() {
        val approved = setOf("@linkedin.com")
        assertTrue(ImagePolicy.initialAllow(ImageMode.ASK, approved, "jobs@linkedin.com"))
        assertTrue(ImagePolicy.initialAllow(ImageMode.ASK, approved, "news@e.linkedin.com"))
        assertFalse(ImagePolicy.initialAllow(ImageMode.ASK, approved, "jobs@other.com"))
    }

    @Test
    fun everyProviderIsApprovedAsAWholeDomain() {
        assertEquals("@gmail.com", SenderDomain.trustKey("A@Gmail.com"))
        val approved = setOf("@gmail.com")
        assertTrue(ImagePolicy.initialAllow(ImageMode.ASK, approved, "b@gmail.com"))
        assertFalse(ImagePolicy.initialAllow(ImageMode.ASK, approved, "b@yahoo.com"))
    }

    @Test
    fun trustKeyHandlesTwoPartTopLevelDomains() {
        assertEquals("@shop.com.bd", SenderDomain.trustKey("x@mail.shop.com.bd"))
        assertEquals("@anthropic.com", SenderDomain.trustKey("no-reply@mail.anthropic.com"))
    }
}
