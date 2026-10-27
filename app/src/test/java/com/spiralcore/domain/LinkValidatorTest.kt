package com.spiralcore.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkValidatorTest {
    @Test
    fun addsHttpsToBareDomain() {
        assertEquals(
            LinkValidation.Valid("https://example.com/", "example.com"),
            LinkValidator.validate("example.com"),
        )
    }

    @Test
    fun preservesPathQueryAndFragment() {
        assertEquals(
            LinkValidation.Valid("https://example.com/a?q=1#part", "example.com"),
            LinkValidator.validate("https://example.com/a?q=1#part"),
        )
    }

    @Test
    fun acceptsBareDomainWithPort() {
        assertEquals(
            LinkValidation.Valid("https://example.com:8443/page", "example.com"),
            LinkValidator.validate("example.com:8443/page"),
        )
    }

    @Test
    fun convertsInternationalizedDomainToAscii() {
        val result = LinkValidator.validate("مثال.إختبار")
        assertTrue(result is LinkValidation.Valid)
        assertTrue((result as LinkValidation.Valid).host.startsWith("xn--"))
    }

    @Test
    fun rejectsUnsupportedSchemes() {
        listOf("javascript:alert(1)", "file:///etc/passwd", "intent://example.com").forEach {
            assertEquals(
                LinkValidation.Invalid(LinkValidation.Reason.UNSUPPORTED_SCHEME),
                LinkValidator.validate(it),
            )
        }
    }

    @Test
    fun rejectsCredentialsEmbeddedInUrl() {
        assertEquals(
            LinkValidation.Invalid(LinkValidation.Reason.CREDENTIALS_NOT_ALLOWED),
            LinkValidator.validate("https://user:secret@example.com/private"),
        )
    }

    @Test
    fun rejectsMalformedAndEmptyInput() {
        assertEquals(LinkValidation.Invalid(LinkValidation.Reason.EMPTY), LinkValidator.validate("  "))
        assertTrue(LinkValidator.validate("https://") is LinkValidation.Invalid)
        assertTrue(LinkValidator.validate("https://example.com:0/") is LinkValidation.Invalid)
        assertTrue(LinkValidator.validate("https://example.com:65536/") is LinkValidation.Invalid)
        assertTrue(LinkValidator.validate("https://example.com:/") is LinkValidation.Invalid)
        assertTrue(LinkValidator.validate("https://example.com:abc/") is LinkValidation.Invalid)
        assertTrue(LinkValidator.validate("https://example.com\\n.evil") is LinkValidation.Invalid)
    }

    @Test
    fun rejectsControlCharactersAndOversizedUrls() {
        assertTrue(LinkValidator.validate("https://example.com/\u0000x") is LinkValidation.Invalid)
        assertTrue(LinkValidator.validate("https://example.com/" + "a".repeat(9_000)) is LinkValidation.Invalid)
    }

    @Test
    fun rejectsMalformedIpv6LiteralAndUnbracketedIpv6() {
        assertTrue(LinkValidator.validate("https://[not-ipv6]/") is LinkValidation.Invalid)
        assertTrue(LinkValidator.validate("https://2001:db8::1/") is LinkValidation.Invalid)
    }

    @Test
    fun normalizesSchemeAndHostCase() {
        assertEquals(
            LinkValidation.Valid("https://example.com/Path", "example.com"),
            LinkValidator.validate("HTTPS://ExAmPlE.CoM/Path"),
        )
    }

    @Test
    fun rejectsLookalikeAuthorityWithCredentials() {
        assertEquals(
            LinkValidation.Invalid(LinkValidation.Reason.CREDENTIALS_NOT_ALLOWED),
            LinkValidator.validate("https://example.com@evil.example/"),
        )
    }
}
