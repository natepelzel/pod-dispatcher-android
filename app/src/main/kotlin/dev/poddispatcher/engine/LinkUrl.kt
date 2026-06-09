package dev.poddispatcher.engine

import java.net.URI
import java.net.URLDecoder

/**
 * Minimal parsed URL. Deliberately avoids android.net.Uri so the whole engine
 * runs in plain JVM unit tests.
 */
data class LinkUrl(
    val host: String,
    val path: String,
    val query: Map<String, String>,
) {
    companion object {
        fun parse(url: String): LinkUrl? {
            val uri = runCatching { URI(url) }.getOrNull() ?: return null
            val host = uri.host?.lowercase() ?: return null
            val path = uri.path?.ifEmpty { "/" } ?: "/"
            val query = (uri.rawQuery ?: "")
                .split('&')
                .filter { it.isNotEmpty() }
                .associate { param ->
                    val i = param.indexOf('=')
                    if (i < 0) decode(param) to ""
                    else decode(param.substring(0, i)) to decode(param.substring(i + 1))
                }
            return LinkUrl(host, path, query)
        }

        /**
         * Extracts the first http(s) URL from free-form text, e.g. the
         * share-sheet payload ("Check out this podcast https://…"). Trailing
         * punctuation that prose tends to glue onto links is stripped.
         */
        fun findInText(text: String?): String? {
            val match = URL_IN_TEXT.find(text ?: return null) ?: return null
            return match.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '>', '"', '\'')
        }

        private val URL_IN_TEXT = Regex("""https?://\S+""")

        private fun decode(s: String): String =
            runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
    }
}
