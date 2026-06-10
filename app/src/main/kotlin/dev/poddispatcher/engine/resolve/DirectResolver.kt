package dev.poddispatcher.engine.resolve

import dev.poddispatcher.engine.ResolvedLink
import dev.poddispatcher.engine.SourceMatch
import dev.poddispatcher.engine.Template
import dev.poddispatcher.model.ResolveConfig

/**
 * For links that already carry the feed URL (subscribe deep links like
 * antennapod.org/deeplink/subscribe?url=… or subscribeonandroid.com/<feed>):
 * no network round trip, the captures *are* the canonical identity.
 */
class DirectResolver : Resolver {
    override val type = "direct"

    override suspend fun resolve(
        config: ResolveConfig,
        match: SourceMatch,
        originalUrl: String,
    ): ResolvedLink? {
        val feedTemplate = config.params["feedUrl"] ?: return null
        val feedUrl = Template.render(feedTemplate, match.captures) ?: return null
        return ResolvedLink(
            level = match.level,
            feedUrl = feedUrl,
            itunesId = config.params["itunesId"]?.let { Template.render(it, match.captures) },
        )
    }
}
