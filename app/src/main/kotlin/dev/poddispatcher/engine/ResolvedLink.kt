package dev.poddispatcher.engine

import dev.poddispatcher.model.LinkLevel

/**
 * Canonical podcast identity produced by a resolver. The RSS feed URL is the
 * universal identifier; episode fields are present only when the resolver
 * could pin down an individual episode.
 */
data class ResolvedLink(
    val level: LinkLevel,
    val feedUrl: String,
    val itunesId: String? = null,
    val episodeGuid: String? = null,
    val episodeTitle: String? = null,
    /** The episode's enclosure (audio file) URL from the RSS feed. */
    val episodeUrl: String? = null,
) {
    /** Variables available to target deep link templates. */
    fun toVars(): Map<String, String> = buildMap {
        put("feedUrl", feedUrl)
        itunesId?.let { put("itunesId", it) }
        episodeGuid?.let { put("episodeGuid", it) }
        episodeTitle?.let { put("episodeTitle", it) }
        episodeUrl?.let { put("episodeUrl", it) }
    }
}
