package dev.poddispatcher.engine

import dev.poddispatcher.model.EpisodeFallback
import dev.poddispatcher.model.LinkLevel
import dev.poddispatcher.model.TargetConfig
import dev.poddispatcher.model.TargetLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepLinkBuilderTest {

    private val pocketCasts = TestSchemas.load("pocket-casts.yml").target!!

    @Test
    fun `builds pocket casts show link from feed url`() {
        val resolved = ResolvedLink(
            level = LinkLevel.SHOW,
            feedUrl = "https://feeds.simplecast.com/54nAGcIl",
        )
        assertEquals(
            listOf("pktc://subscribe/feeds.simplecast.com/54nAGcIl"),
            DeepLinkBuilder.candidates(pocketCasts, resolved),
        )
    }

    @Test
    fun `episode resolution falls back to show link`() {
        val resolved = ResolvedLink(
            level = LinkLevel.EPISODE,
            feedUrl = "https://feeds.simplecast.com/54nAGcIl",
            episodeGuid = "abc-123",
        )
        assertEquals(
            listOf("pktc://subscribe/feeds.simplecast.com/54nAGcIl"),
            DeepLinkBuilder.candidates(pocketCasts, resolved),
        )
    }

    @Test
    fun `episodeFallback none yields no candidates for episodes`() {
        val target = TargetConfig(
            links = TargetLinks(show = listOf("app://show/{feedUrl|urlencode}")),
            episodeFallback = EpisodeFallback.NONE,
        )
        val resolved = ResolvedLink(level = LinkLevel.EPISODE, feedUrl = "https://x.example/feed")
        assertTrue(DeepLinkBuilder.candidates(target, resolved).isEmpty())
    }

    @Test
    fun `episode template with missing variable is skipped in favour of show fallback`() {
        val target = TargetConfig(
            links = TargetLinks(
                show = listOf("app://show/{feedUrl|urlencode}"),
                episode = listOf("app://episode/{episodeGuid}"),
            ),
        )
        val resolved = ResolvedLink(level = LinkLevel.EPISODE, feedUrl = "https://x.example/feed")
        assertEquals(
            listOf("app://show/https%3A%2F%2Fx.example%2Ffeed"),
            DeepLinkBuilder.candidates(target, resolved),
        )
    }

    @Test
    fun `episode template renders when variables are available`() {
        val target = TargetConfig(
            links = TargetLinks(
                show = listOf("app://show/{feedUrl|urlencode}"),
                episode = listOf("app://episode/{episodeGuid}"),
            ),
        )
        val resolved = ResolvedLink(
            level = LinkLevel.EPISODE,
            feedUrl = "https://x.example/feed",
            episodeGuid = "guid-1",
        )
        assertEquals(
            listOf("app://episode/guid-1"),
            DeepLinkBuilder.candidates(target, resolved),
        )
    }
}
