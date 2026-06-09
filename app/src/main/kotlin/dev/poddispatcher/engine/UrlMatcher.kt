package dev.poddispatcher.engine

import dev.poddispatcher.model.LinkLevel
import dev.poddispatcher.model.PodSchema
import dev.poddispatcher.model.SourcePattern

data class SourceMatch(
    val schema: PodSchema,
    val level: LinkLevel,
    /** Named capture group values from the matched pattern. */
    val captures: Map<String, String>,
)

object UrlMatcher {

    /** Returns the first schema/pattern that matches, or null. */
    fun match(url: LinkUrl, schemas: List<PodSchema>): SourceMatch? {
        for (schema in schemas) {
            val source = schema.source ?: continue
            if (url.host !in source.hosts) continue
            for (pattern in source.patterns) {
                val captures = matchPattern(url, pattern) ?: continue
                return SourceMatch(schema, pattern.level, captures)
            }
        }
        return null
    }

    private fun matchPattern(url: LinkUrl, pattern: SourcePattern): Map<String, String>? {
        val captures = mutableMapOf<String, String>()

        val pathRegex = compile(pattern.path) ?: return null
        val pathMatch = pathRegex.find(url.path) ?: return null
        captures += namedGroups(pattern.path, pathMatch)

        for ((param, valueRegexText) in pattern.query) {
            val value = url.query[param] ?: return null
            val valueRegex = compile(valueRegexText) ?: return null
            val valueMatch = valueRegex.find(value) ?: return null
            captures += namedGroups(valueRegexText, valueMatch)
        }
        return captures
    }

    private fun compile(pattern: String): Regex? =
        runCatching { Regex(pattern) }.getOrNull()

    private val GROUP_NAME = Regex("""\(\?<([A-Za-z][A-Za-z0-9]*)>""")

    /** Extracts values of all named groups declared in [pattern] from [match]. */
    private fun namedGroups(pattern: String, match: MatchResult): Map<String, String> =
        GROUP_NAME.findAll(pattern)
            .mapNotNull { found ->
                val name = found.groupValues[1]
                match.groups[name]?.value?.let { name to it }
            }
            .toMap()
}
