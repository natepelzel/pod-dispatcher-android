package dev.poddispatcher.engine

import dev.poddispatcher.engine.resolve.ItunesApiResolver
import dev.poddispatcher.model.LinkLevel
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class ItunesApiResolverTest {

    private val server = MockWebServer()
    private lateinit var resolver: ItunesApiResolver
    private val schema = TestSchemas.load("apple-podcasts.yml")
    private val config get() = schema.source!!.resolve

    @Before
    fun setUp() {
        server.start()
        resolver = ItunesApiResolver(OkHttpClient(), server.url("/").toString().trimEnd('/'))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `show link resolves to feed url`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"results": [{"wrapperType": "track", "feedUrl": "https://feeds.example.com/show.rss"}]}""",
            ),
        )
        val match = SourceMatch(schema, LinkLevel.SHOW, mapOf("showId" to "123"))

        val resolved = resolver.resolve(config, match, "https://example.com")

        assertNotNull(resolved)
        assertEquals(LinkLevel.SHOW, resolved!!.level)
        assertEquals("https://feeds.example.com/show.rss", resolved.feedUrl)
        assertEquals("123", resolved.itunesId)
        assertEquals("/lookup?id=123&entity=podcast", server.takeRequest().path)
    }

    @Test
    fun `episode link resolves guid, title and enclosure url`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {"results": [
                  {"wrapperType": "track", "feedUrl": "https://feeds.example.com/show.rss"},
                  {"wrapperType": "podcastEpisode", "trackId": 456,
                   "feedUrl": "https://feeds.example.com/show.rss",
                   "episodeGuid": "guid-456", "trackName": "Episode Title",
                   "episodeUrl": "https://cdn.example.com/456.mp3"}
                ]}
                """.trimIndent(),
            ),
        )
        val match = SourceMatch(
            schema,
            LinkLevel.EPISODE,
            mapOf("showId" to "123", "episodeId" to "456"),
        )

        val resolved = resolver.resolve(config, match, "https://example.com")

        assertNotNull(resolved)
        assertEquals(LinkLevel.EPISODE, resolved!!.level)
        assertEquals("https://feeds.example.com/show.rss", resolved.feedUrl)
        assertEquals("guid-456", resolved.episodeGuid)
        assertEquals("Episode Title", resolved.episodeTitle)
        assertEquals("https://cdn.example.com/456.mp3", resolved.episodeUrl)
    }

    @Test
    fun `unknown episode id degrades to show resolution`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"results": [{"wrapperType": "track", "feedUrl": "https://feeds.example.com/show.rss"},
                    {"wrapperType": "podcastEpisode", "trackId": 1, "episodeGuid": "other"}]}""",
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                """{"results": [{"wrapperType": "track", "feedUrl": "https://feeds.example.com/show.rss"}]}""",
            ),
        )
        val match = SourceMatch(
            schema,
            LinkLevel.EPISODE,
            mapOf("showId" to "123", "episodeId" to "999"),
        )

        val resolved = resolver.resolve(config, match, "https://example.com")

        assertNotNull(resolved)
        assertEquals(LinkLevel.SHOW, resolved!!.level)
        assertEquals("https://feeds.example.com/show.rss", resolved.feedUrl)
    }
}
