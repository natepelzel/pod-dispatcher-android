package dev.poddispatcher.engine.resolve

import dev.poddispatcher.model.ScrapeStep
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Shared page-fetch + extraction machinery behind every scrape in the engine:
 * source-side [ScrapeResolver] and target-side variable resolution both
 * configure it through the same [ScrapeStep] schema structure.
 */
class Scraper(private val client: OkHttpClient) {

    fun fetchDocument(url: String): Document? =
        fetch(url)?.let { Jsoup.parse(it, url) }

    /** Runs [steps] in order; the first step yielding a non-blank value wins. */
    fun extractFirst(doc: Document, steps: List<ScrapeStep>): String? =
        steps.firstNotNullOfOrNull { step ->
            extract(doc, step)
                ?.takeIf { it.isNotBlank() }
                ?.let { applyPattern(step, it) }
        }

    private fun applyPattern(step: ScrapeStep, value: String): String? {
        val pattern = step.pattern ?: return value
        return runCatching {
            Regex(pattern).find(value)?.groupValues?.getOrNull(1)
        }.getOrNull()
    }

    private fun fetch(url: String): String? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    }.getOrNull()

    private fun extract(doc: Document, step: ScrapeStep): String? = when {
        step.css != null -> {
            doc.select(step.css).firstOrNull()?.let { element ->
                val attr = step.attr ?: "href"
                when (attr) {
                    "text" -> element.text()
                    else -> element.absUrl(attr).ifEmpty { element.attr(attr) }
                }
            }
        }
        step.jsonld != null -> extractJsonLd(doc, step.jsonld)
        else -> null
    }

    private fun extractJsonLd(doc: Document, path: String): String? {
        for (script in doc.select("script[type=application/ld+json]")) {
            val root = runCatching { Json.parseToJsonElement(script.data()) }.getOrNull() ?: continue
            val nodes = if (root is JsonArray) root.toList() else listOf(root)
            for (node in nodes) {
                navigate(node, path)?.let { return it }
            }
        }
        return null
    }

    private fun navigate(node: JsonElement, dottedPath: String): String? {
        var current: JsonElement = node
        for (key in dottedPath.split('.')) {
            current = when (current) {
                is JsonObject -> current[key] ?: return null
                is JsonArray -> key.toIntOrNull()?.let { current.getOrNull(it) } ?: return null
                else -> return null
            }
        }
        return (current as? JsonPrimitive)?.contentOrNull
    }
}
