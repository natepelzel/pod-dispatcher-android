package dev.poddispatcher.engine

import dev.poddispatcher.engine.resolve.DirectResolver
import dev.poddispatcher.model.LinkLevel
import dev.poddispatcher.model.ResolveConfig
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DirectResolverTest {

    private val resolver = DirectResolver()
    private val schema = TestSchemas.load("antennapod.yml")

    private fun match(captures: Map<String, String>) =
        SourceMatch(schema, LinkLevel.SHOW, captures)

    @Test
    fun `renders feed url from captures without any network`() = runTest {
        val config = ResolveConfig(type = "direct", params = mapOf("feedUrl" to "{feedUrl}"))
        val resolved = resolver.resolve(
            config,
            match(mapOf("feedUrl" to "https://feeds.simplecast.com/BqbsxVfO")),
            "https://antennapod.org/deeplink/subscribe?url=…",
        )
        assertEquals("https://feeds.simplecast.com/BqbsxVfO", resolved?.feedUrl)
        assertEquals(LinkLevel.SHOW, resolved?.level)
        assertNull(resolved?.itunesId)
    }

    @Test
    fun `supports prefix templates for scheme-stripped links`() = runTest {
        val config = ResolveConfig(type = "direct", params = mapOf("feedUrl" to "https://{feed}"))
        val resolved = resolver.resolve(
            config,
            match(mapOf("feed" to "feeds.megaphone.fm/thedaily")),
            "https://subscribeonandroid.com/feeds.megaphone.fm/thedaily",
        )
        assertEquals("https://feeds.megaphone.fm/thedaily", resolved?.feedUrl)
    }

    @Test
    fun `fails when the capture is missing`() = runTest {
        val config = ResolveConfig(type = "direct", params = mapOf("feedUrl" to "{feedUrl}"))
        assertNull(resolver.resolve(config, match(emptyMap()), "https://example.com"))
    }

    @Test
    fun `fails when params lack feedUrl`() = runTest {
        val config = ResolveConfig(type = "direct")
        assertNull(resolver.resolve(config, match(mapOf("feedUrl" to "x")), "https://example.com"))
    }
}
