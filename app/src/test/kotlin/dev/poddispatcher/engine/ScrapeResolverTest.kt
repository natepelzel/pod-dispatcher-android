package dev.poddispatcher.engine

import dev.poddispatcher.engine.resolve.ScrapeResolver
import dev.poddispatcher.model.LinkLevel
import dev.poddispatcher.model.ResolveConfig
import dev.poddispatcher.model.ScrapeStep
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class ScrapeResolverTest {

    private val server = MockWebServer()
    private val resolver = ScrapeResolver(OkHttpClient())
    private val schema = TestSchemas.load("spotify.yml")

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /** Mirrors the markup of a Podnews podcast page (the spotify.yml source). */
    private val podnewsStyleHtml = """
        <html><head>
          <link rel="alternate" type="application/rss+xml" title="The Daily RSS feed"
                href="https://feeds.simplecast.com/Sl5CSM3S">
        </head><body>
          <a href="https://pca.st/itunes/1200361736">Pocket Casts</a>
        </body></html>
    """.trimIndent()

    @Test
    fun `extracts feed url and itunes id via url template`() = runTest {
        server.enqueue(MockResponse().setBody(podnewsStyleHtml))
        // Same steps as spotify.yml, but with the url template pointed at the mock server.
        val config = ResolveConfig(
            type = "scrape",
            // built by string concat: HttpUrl would percent-encode the braces
            url = server.url("/").toString().trimEnd('/') + "/podcast/{showId}",
            feedUrl = schema.source!!.resolve.feedUrl,
            itunesId = schema.source!!.resolve.itunesId,
        )
        val match = SourceMatch(schema, LinkLevel.SHOW, mapOf("showId" to "3IM0lmZxpFAY"))

        val resolved = resolver.resolve(config, match, "https://open.spotify.com/show/3IM0lmZxpFAY")

        assertNotNull(resolved)
        assertEquals("https://feeds.simplecast.com/Sl5CSM3S", resolved!!.feedUrl)
        assertEquals("1200361736", resolved.itunesId)
        assertEquals("/podcast/3IM0lmZxpFAY", server.takeRequest().path)
    }

    @Test
    fun `falls back to incoming url when no url template is set`() = runTest {
        server.enqueue(MockResponse().setBody(podnewsStyleHtml))
        val config = ResolveConfig(
            type = "scrape",
            feedUrl = listOf(ScrapeStep(css = "link[rel=alternate][type=\"application/rss+xml\"]", attr = "href")),
        )
        val match = SourceMatch(schema, LinkLevel.SHOW, emptyMap())

        val resolved = resolver.resolve(config, match, server.url("/page").toString())

        assertEquals("https://feeds.simplecast.com/Sl5CSM3S", resolved!!.feedUrl)
        assertEquals("/page", server.takeRequest().path)
    }

    @Test
    fun `jsonld step extracts dotted path`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """<html><head><script type="application/ld+json">
                   {"@type":"PodcastSeries","webFeed":"https://feeds.example.com/x.rss"}
                   </script></head></html>""",
            ),
        )
        val config = ResolveConfig(
            type = "scrape",
            feedUrl = listOf(ScrapeStep(jsonld = "webFeed")),
        )
        val match = SourceMatch(schema, LinkLevel.SHOW, emptyMap())

        val resolved = resolver.resolve(config, match, server.url("/").toString())

        assertEquals("https://feeds.example.com/x.rss", resolved!!.feedUrl)
    }
}
