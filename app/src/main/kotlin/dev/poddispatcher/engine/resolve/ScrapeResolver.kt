package dev.poddispatcher.engine.resolve

import dev.poddispatcher.engine.ResolvedLink
import dev.poddispatcher.engine.SourceMatch
import dev.poddispatcher.engine.Template
import dev.poddispatcher.model.LinkLevel
import dev.poddispatcher.model.ResolveConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * Fetches a page (the incoming URL, or a directory page built from the
 * schema's `url` template) and extracts the RSS feed URL — plus optionally
 * the iTunes id and episode info — using CSS-selector / JSON-LD steps.
 * Episode steps are consulted only for episode-level matches; when none of
 * them yields a value the resolution degrades to show level (mirroring the
 * iTunes resolver's graceful degradation).
 */
class ScrapeResolver(client: OkHttpClient) : Resolver {
    override val type = "scrape"

    private val scraper = Scraper(client)

    override suspend fun resolve(
        config: ResolveConfig,
        match: SourceMatch,
        originalUrl: String,
    ): ResolvedLink? = withContext(Dispatchers.IO) {
        val pageUrl = when (val template = config.url) {
            null -> originalUrl
            else -> Template.render(template, match.captures) ?: return@withContext null
        }
        val doc = scraper.fetchDocument(pageUrl) ?: return@withContext null

        val feedUrl = scraper.extractFirst(doc, config.feedUrl) ?: return@withContext null
        val isEpisode = match.level == LinkLevel.EPISODE
        val episodeUrl = if (isEpisode) scraper.extractFirst(doc, config.episodeUrl) else null
        val episodeTitle = if (isEpisode) scraper.extractFirst(doc, config.episodeTitle) else null
        val episodeGuid = if (isEpisode) scraper.extractFirst(doc, config.episodeGuid) else null
        val resolvedEpisode = episodeUrl != null || episodeTitle != null || episodeGuid != null
        ResolvedLink(
            level = if (resolvedEpisode) LinkLevel.EPISODE else LinkLevel.SHOW,
            feedUrl = feedUrl,
            itunesId = scraper.extractFirst(doc, config.itunesId),
            episodeGuid = episodeGuid,
            episodeTitle = episodeTitle,
            episodeUrl = episodeUrl,
        )
    }
}
