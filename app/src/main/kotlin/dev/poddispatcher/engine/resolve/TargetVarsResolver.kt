package dev.poddispatcher.engine.resolve

import dev.poddispatcher.engine.Template
import dev.poddispatcher.model.TargetResolve
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * Target-side resolution: mints extra template variables for a target's deep
 * links by scraping a page reachable from the canonical identity. This is how
 * proprietary-catalog apps (Spotify, …) become targets — their internal show
 * id isn't derivable from the RSS feed, but directory pages map it.
 *
 * Failures are soft: variables that can't be minted are simply absent, and
 * link templates referencing them are skipped by the normal template rules.
 */
class TargetVarsResolver(client: OkHttpClient) {

    private val scraper = Scraper(client)

    suspend fun resolve(
        config: TargetResolve,
        canonicalVars: Map<String, String>,
    ): Map<String, String> = withContext(Dispatchers.IO) {
        if (config.type != "scrape") return@withContext emptyMap()
        val pageUrl = Template.render(config.url, canonicalVars)
            ?: return@withContext emptyMap() // identity lacks a required variable
        val doc = scraper.fetchDocument(pageUrl) ?: return@withContext emptyMap()
        buildMap {
            for ((name, steps) in config.vars) {
                scraper.extractFirst(doc, steps)?.let { put(name, it) }
            }
        }
    }
}
