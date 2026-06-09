package dev.poddispatcher.engine

import dev.poddispatcher.model.EpisodeFallback
import dev.poddispatcher.model.LinkLevel
import dev.poddispatcher.model.TargetConfig

object DeepLinkBuilder {

    /**
     * Renders the target's deep link candidates in priority order. Episode
     * links come first for episode-level resolutions; if none can be built
     * (no episode templates, or missing variables) and the target allows it,
     * show links are used as the graceful fallback.
     */
    fun candidates(target: TargetConfig, resolved: ResolvedLink): List<String> {
        val vars = resolved.toVars()
        val out = mutableListOf<String>()

        if (resolved.level == LinkLevel.EPISODE) {
            target.links.episode.mapNotNullTo(out) { Template.render(it, vars) }
        }

        val episodeFellThrough = resolved.level == LinkLevel.EPISODE &&
            out.isEmpty() &&
            target.episodeFallback == EpisodeFallback.SHOW

        if (resolved.level == LinkLevel.SHOW || episodeFellThrough) {
            target.links.show.mapNotNullTo(out) { Template.render(it, vars) }
        }
        return out
    }
}
