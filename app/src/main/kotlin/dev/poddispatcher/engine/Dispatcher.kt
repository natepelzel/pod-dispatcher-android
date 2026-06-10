package dev.poddispatcher.engine

import dev.poddispatcher.Prefs
import dev.poddispatcher.engine.resolve.Resolver
import dev.poddispatcher.engine.resolve.TargetVarsResolver

/**
 * End-to-end pipeline: incoming URL → source match → canonical resolution →
 * deep link candidates for the user's preferred target app.
 */
class Dispatcher(
    private val repository: SchemaRepository,
    resolvers: List<Resolver>,
    private val prefs: Prefs,
    private val targetVarsResolver: TargetVarsResolver? = null,
) {
    private val resolversByType = resolvers.associateBy { it.type }

    sealed interface Result {
        /** Deep link URLs to try in order, pinned to [packageName] when set. */
        data class Launch(val candidates: List<String>, val packageName: String?) : Result
        data class Failure(val reason: String) : Result
    }

    suspend fun dispatch(url: String): Result {
        val parsed = LinkUrl.parse(url)
            ?: return Result.Failure("Not a valid URL")

        val match = UrlMatcher.match(parsed, repository.sources())
            ?: return Result.Failure("No schema recognises this link")

        val resolveConfig = match.schema.source!!.resolve
        val resolver = resolversByType[resolveConfig.type]
            ?: return Result.Failure("Unsupported resolver type '${resolveConfig.type}'")

        val resolved = resolver.resolve(resolveConfig, match, url)
            ?: return Result.Failure("Could not resolve the podcast feed")

        val targetSchema = prefs.preferredTargetId?.let { repository.byId(it) }
            ?.takeIf { it.target != null }
            ?: repository.androidTargets().firstOrNull()
            ?: return Result.Failure("No target app configured")

        // Targets keyed by a proprietary catalog id (Spotify, …) declare a
        // resolve block that mints extra variables at dispatch time.
        val extraVars = targetSchema.target!!.resolve
            ?.let { targetVarsResolver?.resolve(it, resolved.toVars()) }
            ?: emptyMap()

        val candidates = DeepLinkBuilder.candidates(targetSchema.target, resolved, extraVars)
        if (candidates.isEmpty()) {
            return Result.Failure("No deep link available for ${targetSchema.name}")
        }
        return Result.Launch(candidates, targetSchema.target.android?.packageName)
    }
}
