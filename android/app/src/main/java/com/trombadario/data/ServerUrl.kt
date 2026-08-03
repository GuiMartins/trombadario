package com.trombadario.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object ServerUrl {

    /**
     * Turns what the user typed into something Retrofit accepts, or null if it
     * cannot. Accepts a bare "192.168.31.172:8090" because that is how the
     * address is written down on the server, and defaults to http:// since the
     * home backend has no TLS.
     */
    private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

    fun normalize(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        val withScheme = when {
            // Checked before trimming slashes: trimming first turns "http://"
            // into "http:", which then looks scheme-less and gets mangled into
            // "http://http:" - a URL OkHttp accepts and nothing ever answers.
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            // Any other scheme is a typo, not something to wrap in http://.
            SCHEME.containsMatchIn(trimmed) -> return null
            else -> "http://$trimmed"
        }

        val parsed = withScheme.trimEnd('/').toHttpUrlOrNull() ?: return null
        if (parsed.host.isBlank()) return null
        return withScheme.trimEnd('/')
    }
}
