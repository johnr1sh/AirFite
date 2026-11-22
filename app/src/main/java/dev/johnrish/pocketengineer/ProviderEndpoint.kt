package dev.johnrish.pocketengineer

import java.net.URI

/** Shared validation for provider base URLs before any credential-bearing request. */
object ProviderEndpoint {
    const val ERROR_MESSAGE = "Use a valid HTTPS API base URL without credentials, query, or fragment."

    fun normalize(raw: String): String? {
        val input = raw.trim()
        if (input.isEmpty() || input.any(Char::isWhitespace)) return null
        val uri = runCatching { URI(input) }.getOrNull() ?: return null
        if (!uri.isAbsolute || !uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.host.isNullOrBlank() || uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
        if (uri.port !in -1..65535) return null
        return input.trimEnd('/')
    }
}
