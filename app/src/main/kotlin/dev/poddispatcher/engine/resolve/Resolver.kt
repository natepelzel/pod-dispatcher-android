package dev.poddispatcher.engine.resolve

import dev.poddispatcher.engine.ResolvedLink
import dev.poddispatcher.engine.SourceMatch
import dev.poddispatcher.model.ResolveConfig

const val USER_AGENT = "PodDispatcher/0.1 (https://github.com/natepelzel/pod-dispatcher-android)"

/**
 * Turns a matched incoming link into a canonical [ResolvedLink]. One
 * implementation per `resolve.type` value; schemas configure, never execute.
 */
interface Resolver {
    /** The `resolve.type` value this implementation handles. */
    val type: String

    /**
     * @param config the source schema's resolve block
     * @param match matched level + named captures
     * @param originalUrl the full incoming URL (the scrape resolver re-fetches it)
     * @return canonical identity, or null if resolution failed
     */
    suspend fun resolve(config: ResolveConfig, match: SourceMatch, originalUrl: String): ResolvedLink?
}
