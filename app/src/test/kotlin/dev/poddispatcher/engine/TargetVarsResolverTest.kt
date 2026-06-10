package dev.poddispatcher.engine

import dev.poddispatcher.engine.resolve.TargetVarsResolver
import dev.poddispatcher.model.ScrapeStep
import dev.poddispatcher.model.TargetResolve
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetVarsResolverTest {

    private val server = MockWebServer()
    private val resolver = TargetVarsResolver(OkHttpClient())

    @After
    fun tearDown() = server.shutdown()

    /** Trimmed-down version of a Podnews podcast page. */
    private val directoryPage = """
        <html><body>
          <a class="podcastsubscribe" aria-label="spotify-podcast"
             href="https://open.spotify.com/show/3IM0lmZxpFAY7CwMuv9H4g">Spotify</a>
          <a href="https://podcasts.apple.com/us/podcast/the-daily/id1200361736">Apple</a>
        </body></html>
    """.trimIndent()

    private fun spotifyResolve(url: String) = TargetResolve(
        type = "scrape",
        url = url,
        vars = mapOf(
            "spotifyShowId" to listOf(
                ScrapeStep(
                    css = """a[href^="https://open.spotify.com/show/"]""",
                    attr = "href",
                    pattern = "show/([A-Za-z0-9]+)",
                ),
            ),
        ),
    )

    @Test
    fun `mints variables from the fetched page`() = runTest {
        server.enqueue(MockResponse().setBody(directoryPage))
        server.start()
        // Concatenated so HttpUrl doesn't percent-encode the placeholder braces.
        val url = server.url("/podcast/").toString() + "{itunesId}"

        val vars = resolver.resolve(spotifyResolve(url), mapOf("itunesId" to "1200361736"))

        assertEquals(mapOf("spotifyShowId" to "3IM0lmZxpFAY7CwMuv9H4g"), vars)
        assertTrue(server.takeRequest().path!!.endsWith("/podcast/1200361736"))
    }

    @Test
    fun `returns no vars when the identity lacks a required variable`() = runTest {
        server.start()
        val url = server.url("/podcast/").toString() + "{itunesId}"

        // Resolved without an iTunes id (e.g. a direct feed link source).
        val vars = resolver.resolve(spotifyResolve(url), mapOf("feedUrl" to "https://x/feed"))

        assertEquals(emptyMap<String, String>(), vars)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `returns no vars when the page fetch fails`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        server.start()
        val url = server.url("/podcast/").toString() + "{itunesId}"

        val vars = resolver.resolve(spotifyResolve(url), mapOf("itunesId" to "1200361736"))

        assertEquals(emptyMap<String, String>(), vars)
    }

    @Test
    fun `missing variables are absent rather than failing the rest`() = runTest {
        server.enqueue(MockResponse().setBody(directoryPage))
        server.start()
        val resolve = spotifyResolve(server.url("/p/").toString() + "{itunesId}").let {
            it.copy(
                vars = it.vars + mapOf(
                    "neverThere" to listOf(ScrapeStep(css = "a.does-not-exist")),
                ),
            )
        }

        val vars = resolver.resolve(resolve, mapOf("itunesId" to "1200361736"))

        assertEquals(mapOf("spotifyShowId" to "3IM0lmZxpFAY7CwMuv9H4g"), vars)
    }
}
