package dev.poddispatcher.engine.resolve

import dev.poddispatcher.engine.ResolvedLink
import dev.poddispatcher.engine.SourceMatch
import dev.poddispatcher.model.ResolveConfig

/**
 * Placeholder for Podcast Index (podcastindex.org) resolution. Their API
 * requires a key + HMAC-signed requests; wire credentials in via BuildConfig
 * (never into schema files) before implementing. Until then this resolver
 * type is accepted by the schema format but resolves nothing.
 */
class PodcastIndexResolver : Resolver {
    override val type = "podcast-index"

    override suspend fun resolve(
        config: ResolveConfig,
        match: SourceMatch,
        originalUrl: String,
    ): ResolvedLink? = null
}
