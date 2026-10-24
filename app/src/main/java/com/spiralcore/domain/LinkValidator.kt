package com.spiralcore.domain

import java.net.IDN
import java.net.URI
import java.util.Locale

sealed interface LinkValidation {
    data class Valid(val url: String, val host: String) : LinkValidation
    data class Invalid(val reason: Reason) : LinkValidation

    enum class Reason {
        EMPTY,
        INVALID_FORMAT,
        UNSUPPORTED_SCHEME,
        CREDENTIALS_NOT_ALLOWED,
        INVALID_HOST,
    }
}

object LinkValidator {
    private val explicitScheme = Regex("^([A-Za-z][A-Za-z0-9+.-]*):")

    fun validate(raw: String): LinkValidation {
        val input = raw.trim()
        if (input.isEmpty()) return LinkValidation.Invalid(LinkValidation.Reason.EMPTY)

        val schemeMatch = explicitScheme.find(input)
        val explicitHttp = input.startsWith("http://", ignoreCase = true) ||
            input.startsWith("https://", ignoreCase = true)
        val hostWithPort = schemeMatch != null &&
            schemeMatch.groupValues[1].contains('.') &&
            input.substringAfter(':').firstOrNull()?.isDigit() == true
        val candidate = when {
            explicitHttp || hostWithPort -> if (explicitHttp) input else "https://$input"
            schemeMatch != null -> return LinkValidation.Invalid(LinkValidation.Reason.UNSUPPORTED_SCHEME)
            else -> "https://$input"
        }

        val uri = try {
            URI(candidate)
        } catch (_: Exception) {
            return LinkValidation.Invalid(LinkValidation.Reason.INVALID_FORMAT)
        }
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
            ?: return LinkValidation.Invalid(LinkValidation.Reason.INVALID_FORMAT)
        if (scheme != "http" && scheme != "https") {
            return LinkValidation.Invalid(LinkValidation.Reason.UNSUPPORTED_SCHEME)
        }
        if (uri.rawUserInfo != null || uri.rawAuthority?.contains('@') == true) {
            return LinkValidation.Invalid(LinkValidation.Reason.CREDENTIALS_NOT_ALLOWED)
        }

        val authority = uri.rawAuthority
            ?: return LinkValidation.Invalid(LinkValidation.Reason.INVALID_HOST)
        val hostAndPort = splitAuthority(authority)
            ?: return LinkValidation.Invalid(LinkValidation.Reason.INVALID_HOST)
        val rawHost = hostAndPort.first
        val bracketedIpv6 = rawHost.startsWith('[') && rawHost.endsWith(']')
        val asciiHost = if (bracketedIpv6) {
            rawHost
        } else {
            try {
                IDN.toASCII(rawHost, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
            } catch (_: IllegalArgumentException) {
                return LinkValidation.Invalid(LinkValidation.Reason.INVALID_HOST)
            }
        }
        if (asciiHost.isBlank() || asciiHost.startsWith('.') || asciiHost.endsWith('.') ||
            asciiHost.any { it.isWhitespace() }
        ) {
            return LinkValidation.Invalid(LinkValidation.Reason.INVALID_HOST)
        }

        val path = uri.rawPath?.ifEmpty { "/" } ?: "/"
        val normalized = buildString {
            append(scheme)
            append("://")
            append(asciiHost)
            append(hostAndPort.second)
            append(path)
            uri.rawQuery?.let { append('?').append(it) }
            uri.rawFragment?.let { append('#').append(it) }
        }
        return LinkValidation.Valid(normalized, asciiHost)
    }

    private fun splitAuthority(authority: String): Pair<String, String>? {
        if (authority.isBlank() || authority.any { it.isWhitespace() }) return null
        if (authority.startsWith('[')) {
            val closing = authority.indexOf(']')
            if (closing <= 1) return null
            val host = authority.substring(0, closing + 1)
            val suffix = authority.substring(closing + 1)
            if (suffix.isNotEmpty() && !suffix.matches(Regex(":\\d{1,5}"))) return null
            val port = suffix.removePrefix(":").toIntOrNull()
            if (port != null && port !in 1..65535) return null
            return host to suffix
        }

        val colon = authority.lastIndexOf(':')
        if (colon >= 0) {
            val possiblePort = authority.substring(colon + 1)
            if (possiblePort.isEmpty() || !possiblePort.all(Char::isDigit)) return null
            val port = possiblePort.toIntOrNull() ?: return null
            if (port !in 1..65535) return null
            return authority.substring(0, colon) to ":$port"
        }
        return authority to ""
    }
}
