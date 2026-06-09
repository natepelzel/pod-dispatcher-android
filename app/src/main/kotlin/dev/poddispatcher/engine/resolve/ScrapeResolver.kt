package dev.poddispatcher.engine.resolve

import dev.poddispatcher.engine.ResolvedLink
import dev.poddispatcher.engine.SourceMatch
import dev.poddispatcher.engine.Template
import dev.poddispatcher.model.LinkLevel
import dev.poddispatcher.model.ResolveConfig
import dev.poddispatcher.model.ScrapeStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 * Fetches a page (the incoming URL, or a directory page built from the
 * schema's `url` template) and extracts the RSS feed URL — plus optionally
 * the iTunes id — using CSS-selector / JSON-LD steps. Resolves at show
 * level; episode-aware scraping can be added to the schema format later.
 */
class ScrapeResolver(private val client: OkHttpClient) : Resolver {
    override val type = "scrape"

    override suspend fun resolve(
        config: ResolveConfig,
        match: SourceMatch,
        originalUrl: String,
    ): ResolvedLink? = withContext(Dispatchers.IO) {
        val pageUrl = when (val template = config.url) {
            null -> originalUrl
            else -> Template.render(template, match.captures) ?: return@withContext null
        }
        val html = fetch(pageUrl) ?: return@withContext null
        val doc = Jsoup.parse(html, pageUrl)

        val feedUrl = extractFirst(doc, config.feedUrl) ?: return@withContext null
        ResolvedLink(
            level = LinkLevel.SHOW,
            feedUrl = feedUrl,
            itunesId = extractFirst(doc, config.itunesId),
        )
    }

    private fun extractFirst(doc: Document, steps: List<ScrapeStep>): String? =
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
