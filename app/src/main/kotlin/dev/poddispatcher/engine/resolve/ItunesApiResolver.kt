package dev.poddispatcher.engine.resolve

import dev.poddispatcher.engine.ResolvedLink
import dev.poddispatcher.engine.SourceMatch
import dev.poddispatcher.engine.Template
import dev.poddispatcher.model.LinkLevel
import dev.poddispatcher.model.ResolveConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Resolves via the public iTunes Lookup API (no auth required).
 *
 * Show level: `lookup?id=<id>&entity=podcast` → `results[0].feedUrl`.
 *
 * Episode level (when the schema provides `params.episodeId`):
 * `lookup?id=<id>&entity=podcastEpisode` returns recent episodes whose
 * `trackId` matches Apple's `?i=` URL parameter, carrying the episode GUID,
 * title, and enclosure URL. Episodes older than the lookup window can't be
 * matched and degrade to show level.
 */
class ItunesApiResolver(
    private val client: OkHttpClient,
    private val baseUrl: String = "https://itunes.apple.com",
) : Resolver {
    override val type = "itunes-api"

    override suspend fun resolve(
        config: ResolveConfig,
        match: SourceMatch,
        originalUrl: String,
    ): ResolvedLink? = withContext(Dispatchers.IO) {
        val idTemplate = config.params["id"] ?: return@withContext null
        val id = Template.render(idTemplate, match.captures) ?: return@withContext null
        if (id.isBlank() || !id.all { it.isDigit() }) return@withContext null

        if (match.level == LinkLevel.EPISODE) {
            val episodeId = config.params["episodeId"]?.let { Template.render(it, match.captures) }
            if (episodeId != null) {
                resolveEpisode(id, episodeId)?.let { return@withContext it }
                // episode not found — graceful degradation to show level
            }
        }
        resolveShow(id)
    }

    private fun resolveShow(id: String): ResolvedLink? = runCatching {
        val results = lookup("$baseUrl/lookup?id=$id&entity=podcast") ?: return null
        val feedUrl = results.firstOrNull()?.jsonObject
            ?.get("feedUrl")?.jsonPrimitive?.contentOrNull ?: return null
        ResolvedLink(level = LinkLevel.SHOW, feedUrl = feedUrl, itunesId = id)
    }.getOrNull()

    private fun resolveEpisode(showId: String, episodeId: String): ResolvedLink? = runCatching {
        val results = lookup(
            "$baseUrl/lookup?id=$showId&entity=podcastEpisode&limit=$EPISODE_LOOKUP_LIMIT",
        ) ?: return null
        val episode = results.map { it.jsonObject }.firstOrNull {
            it.str("wrapperType") == "podcastEpisode" && it.str("trackId") == episodeId
        } ?: return null
        val feedUrl = episode.str("feedUrl")
            ?: results.firstOrNull()?.jsonObject?.str("feedUrl")
            ?: return null
        ResolvedLink(
            level = LinkLevel.EPISODE,
            feedUrl = feedUrl,
            itunesId = showId,
            episodeGuid = episode.str("episodeGuid"),
            episodeTitle = episode.str("trackName"),
            episodeUrl = episode.str("episodeUrl"),
        )
    }.getOrNull()

    private fun lookup(url: String): JsonArray? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            Json.parseToJsonElement(body).jsonObject["results"]?.jsonArray
        }
    }

    private fun JsonObject.str(key: String): String? =
        get(key)?.jsonPrimitive?.contentOrNull

    private companion object {
        // iTunes caps lookup results; episodes beyond the most recent ones
        // simply fall back to show-level resolution.
        const val EPISODE_LOOKUP_LIMIT = 200
    }
}
