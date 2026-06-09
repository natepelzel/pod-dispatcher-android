package dev.poddispatcher.engine

import java.net.URLEncoder
import java.util.Base64

/**
 * Renders `{variable}` / `{variable|filter}` placeholders.
 * Filters: urlencode, strip-scheme, base64url.
 */
object Template {
    private val PLACEHOLDER = Regex("""\{([A-Za-z][A-Za-z0-9]*)(?:\|([a-z0-9-]+))?\}""")

    /** Returns null if any referenced variable is missing — callers treat that
     *  template as unavailable and move on to the next candidate. */
    fun render(template: String, vars: Map<String, String>): String? {
        var failed = false
        val out = PLACEHOLDER.replace(template) { m ->
            val value = vars[m.groupValues[1]]
            if (value == null) {
                failed = true
                ""
            } else {
                applyFilter(value, m.groupValues[2])
            }
        }
        return if (failed) null else out
    }

    private fun applyFilter(value: String, filter: String): String = when (filter) {
        "" -> value
        "urlencode" -> URLEncoder.encode(value, "UTF-8")
        "strip-scheme" -> value.replace(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://"), "")
        "base64url" -> Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())
        else -> value // unknown filters are validated away in CI; pass through defensively
    }
}
