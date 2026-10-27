package com.spiralcore.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkValidatorTest {
    @Test
    fun addsHttpsToBareDomain() {
        assertEquals(LinkValidation.Valid("https://example.com/", "example.com"), LinkValidator.validate("example.com"))
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
    fun rejectsCredentialsAndAuthorityConfusion() {
        listOf(
            "https://user:secret@example.com/private",
            "https://example.com@evil.example/",
            "https://user%40example.com@example.net/",
        ).forEach {
            assertEquals(
                LinkValidation.Invalid(LinkValidation.Reason.CREDENTIALS_NOT_ALLOWED),
                LinkValidator.validate(it),
            )
        }
    }

    @Test
    fun rejectsMalformedAndOutOfRangePorts() {
        listOf(
            "https://example.com:0/",
            "https://example.com:65536/",
            "https://example.com:/",
            "https://example.com:abc/",
            "https://example.com:123456/",
            "https://[2001:db8::1]:65536/",
            "https://[2001:db8::1]:abc/",
        ).forEach { assertTrue("Expected rejection: $it", LinkValidator.validate(it) is LinkValidation.Invalid) }
        assertTrue(LinkValidator.validate("https://example.com:1/") is LinkValidation.Valid)
        assertTrue(LinkValidator.validate("https://example.com:65535/") is LinkValidation.Valid)
    }

    @Test
    fun rejectsControlCharactersAndOversizedUrls() {
        listOf(
            "https://example.com/\u0000x",
            "https://example.com/line\nbreak",
            "https://example.com/line\rbreak",
            "https://example.com/\u007f",
        ).forEach { assertTrue(LinkValidator.validate(it) is LinkValidation.Invalid) }

        assertTrue(LinkValidator.validate("https://example.com/" + "a".repeat(9_000)) is LinkValidation.Invalid)
        assertTrue(LinkValidator.validate("https://example.com/" + "a".repeat(8_000)) is LinkValidation.Valid)
    }

    @Test
    fun acceptsValidBracketedIpv6AndRejectsMalformedVariants() {
        listOf(
            "https://[2001:db8::1]/path",
            "https://[::1]/",
            "https://[2001:db8:0:1:2:3:4:5]/",
            "https://[::ffff:192.0.2.1]/",
        ).forEach { assertTrue("Expected acceptance: $it", LinkValidator.validate(it) is LinkValidation.Valid) }
        listOf(
            "https://[not-ipv6]/",
            "https://[2001:db8::1",
            "https://2001:db8::1/",
            "https://[::::]/",
            "https://[2001:db8::1]suffix/",
            "https://[1:2:3:4:5:6:7:8:9]/",
            "https://[1:2:3:4:5:6:7]/",
            "https://[::ffff:999.0.2.1]/",
            "https://[fe80::1%25eth0]/",
        ).forEach { assertTrue("Expected rejection: $it", LinkValidator.validate(it) is LinkValidation.Invalid) }
    }

    @Test
    fun normalizesSchemeAndHostCase() {
        assertEquals(
            LinkValidation.Valid("https://example.com/Path", "example.com"),
            LinkValidator.validate("HTTPS://ExAmPlE.CoM/Path"),
        )
    }

    @Test
    fun rejectsEmptyInputAndMalformedAuthority() {
        assertEquals(LinkValidation.Invalid(LinkValidation.Reason.EMPTY), LinkValidator.validate("  "))
        listOf("https://", "https://.", "https://.example.com", "https://example.com..", "https://exa mple.com")
            .forEach { assertTrue("Expected rejection: $it", LinkValidator.validate(it) is LinkValidation.Invalid) }
    }
}
