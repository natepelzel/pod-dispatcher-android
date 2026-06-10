package dev.poddispatcher.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Declarative description of one podcast app/platform, loaded from a YAML
 * schema file. Mirrors pod-dispatcher.schema.json in the schemas/ submodule —
 * keep both in sync.
 */
@Serializable
data class PodSchema(
    val id: String,
    val name: String,
    val homepage: String? = null,
    val source: SourceConfig? = null,
    val target: TargetConfig? = null,
) {
    init {
        require(source != null || target != null) {
            "Schema '$id' must define a source block, a target block, or both"
        }
    }
}

@Serializable
data class SourceConfig(
    val hosts: List<String>,
    val patterns: List<SourcePattern>,
    val android: AndroidSource? = null,
    val resolve: ResolveConfig,
)

/**
 * Android-specific interception hints. Consumed at build time when the app
 * manifest is generated from the schemas; not used by the engine at runtime.
 */
@Serializable
data class AndroidSource(
    val paths: List<AndroidPath>,
)

/** Exactly one of [prefix] or [pattern] is set (enforced by the JSON Schema). */
@Serializable
data class AndroidPath(
    /** Literal path prefix (android:pathPrefix). */
    val prefix: String? = null,
    /** Android glob (android:pathPattern syntax), not a regex. */
    val pattern: String? = null,
)

@Serializable
data class SourcePattern(
    val level: LinkLevel,
    /** Regex matched against the URL path; named groups become captures. */
    val path: String,
    /** Required query parameter name → regex over its value. */
    val query: Map<String, String> = emptyMap(),
)

@Serializable
enum class LinkLevel {
    @SerialName("show")
    SHOW,

    @SerialName("episode")
    EPISODE,
}

@Serializable
data class ResolveConfig(
    /** One of: itunes-api, scrape, podcast-index. */
    val type: String,
    val params: Map<String, String> = emptyMap(),
    /**
     * Scrape resolver only: template for the page to fetch (e.g. a directory
     * page keyed by a captured id). Defaults to the incoming URL itself.
     */
    val url: String? = null,
    /** Scrape resolver only: extraction steps tried in order. */
    val feedUrl: List<ScrapeStep> = emptyList(),
    /** Scrape resolver only: optional iTunes id extraction steps. */
    val itunesId: List<ScrapeStep> = emptyList(),
    /**
     * Scrape resolver only: optional episode enclosure (audio URL), title and
     * GUID extraction steps. Consulted only when an episode-level pattern
     * matched; extracting at least one of them keeps the resolution at
     * episode level, otherwise it degrades to show level.
     */
    val episodeUrl: List<ScrapeStep> = emptyList(),
    val episodeTitle: List<ScrapeStep> = emptyList(),
    val episodeGuid: List<ScrapeStep> = emptyList(),
)

/** Exactly one of [css] or [jsonld] is set (enforced by the JSON Schema). */
@Serializable
data class ScrapeStep(
    val css: String? = null,
    val attr: String? = null,
    val jsonld: String? = null,
    /**
     * Optional regex applied to the extracted value; the first capture group
     * becomes the result. The step fails if the regex doesn't match.
     */
    val pattern: String? = null,
)

@Serializable
data class TargetConfig(
    val android: AndroidTarget? = null,
    val resolve: TargetResolve? = null,
    val links: TargetLinks,
    val episodeFallback: EpisodeFallback = EpisodeFallback.SHOW,
)

/**
 * Optional target-side resolution: scrapes a page reachable from the
 * canonical identity to mint extra link-template variables (e.g. a Spotify
 * show id that can't be derived from the RSS feed).
 */
@Serializable
data class TargetResolve(
    /** Only "scrape" for now. */
    val type: String,
    /** Template for the page to fetch, over the canonical variables. */
    val url: String,
    /** Variable name → extraction steps tried in order. */
    val vars: Map<String, List<ScrapeStep>> = emptyMap(),
)

@Serializable
data class AndroidTarget(
    @SerialName("package") val packageName: String,
)

@Serializable
data class TargetLinks(
    val show: List<String> = emptyList(),
    val episode: List<String> = emptyList(),
)

@Serializable
enum class EpisodeFallback {
    @SerialName("show")
    SHOW,

    @SerialName("none")
    NONE,
}
