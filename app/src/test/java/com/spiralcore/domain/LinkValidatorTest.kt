package com.spiralcore.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkValidatorTest {
    @Test
    fun addsHttpsToBareDomain() {
        val result = LinkValidator.validate("example.com")
        assertEquals(LinkValidation.Valid("https://example.com/", "example.com"), result)
    }

    @Test
    fun preservesPathQueryAndFragment() {
        val result = LinkValidator.validate("https://example.com/a?q=1#part")
        assertEquals(
            LinkValidation.Valid("https://example.com/a?q=1#part", "example.com"),
            result,
        )
    }

    @Test
    fun acceptsBareDomainWithPort() {
        val result = LinkValidator.validate("example.com:8443/page")
        assertEquals(
            LinkValidation.Valid("https://example.com:8443/page", "example.com"),
            result,
        )
    }

    @Test
    fun convertsInternationalizedDomainToAscii() {
        val result = LinkValidator.validate("مثال.إختبار")
        assertTrue(result is LinkValidation.Valid)
        assertTrue((result as LinkValidation.Valid).host.startsWith("xn--"))
    }

    @Test
    fun rejectsJavascriptAndOtherUnsupportedSchemes() {
        val result = LinkValidator.validate("javascript:alert(1)")
        assertEquals(
            LinkValidation.Invalid(LinkValidation.Reason.UNSUPPORTED_SCHEME),
            result,
        )
    }

    @Test
    fun rejectsCredentialsEmbeddedInUrl() {
        val result = LinkValidator.validate("https://user:secret@example.com/private")
        assertEquals(
            LinkValidation.Invalid(LinkValidation.Reason.CREDENTIALS_NOT_ALLOWED),
            result,
        )
    }

    @Test
    fun rejectsMalformedAndEmptyInput() {
        assertEquals(
            LinkValidation.Invalid(LinkValidation.Reason.EMPTY),
            LinkValidator.validate("  "),
        )
        assertTrue(LinkValidator.validate("https://") is LinkValidation.Invalid)
    }
}
