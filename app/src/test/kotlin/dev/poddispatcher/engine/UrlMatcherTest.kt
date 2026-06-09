package dev.poddispatcher.engine

import dev.poddispatcher.model.LinkLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UrlMatcherTest {

    private val schemas = listOf(
        TestSchemas.load("apple-podcasts.yml"),
        TestSchemas.load("spotify.yml"),
        TestSchemas.load("overcast.yml"),
        TestSchemas.load("castbox.yml"),
    )

    private fun match(url: String): SourceMatch? =
        UrlMatcher.match(LinkUrl.parse(url)!!, schemas)

    @Test
    fun `matches apple show link`() {
        val m = match("https://podcasts.apple.com/us/podcast/the-daily/id1200361736")
        assertNotNull(m)
        assertEquals(LinkLevel.SHOW, m!!.level)
        assertEquals("1200361736", m.captures["showId"])
        assertEquals("us", m.captures["country"])
    }

    @Test
    fun `matches apple episode link via query parameter`() {
        val m = match(
            "https://podcasts.apple.com/us/podcast/the-daily/id1200361736?i=1000123456789",
        )
        assertNotNull(m)
        assertEquals(LinkLevel.EPISODE, m!!.level)
        assertEquals("1200361736", m.captures["showId"])
        assertEquals("1000123456789", m.captures["episodeId"])
    }

    @Test
    fun `matches apple link without country segment`() {
        val m = match("https://podcasts.apple.com/podcast/id1200361736")
        assertNotNull(m)
        assertEquals(LinkLevel.SHOW, m!!.level)
        assertEquals("1200361736", m.captures["showId"])
        assertNull(m.captures["country"])
    }

    @Test
    fun `matches legacy itunes host`() {
        val m = match("https://itunes.apple.com/us/podcast/the-daily/id1200361736")
        assertNotNull(m)
        assertEquals("1200361736", m!!.captures["showId"])
    }

    @Test
    fun `matches spotify show link`() {
        val m = match("https://open.spotify.com/show/3IM0lmZxpFAY7CwMuv9H4g")
        assertNotNull(m)
        assertEquals("spotify", m!!.schema.id)
        assertEquals(LinkLevel.SHOW, m.level)
        assertEquals("3IM0lmZxpFAY7CwMuv9H4g", m.captures["showId"])
    }

    @Test
    fun `matches spotify show link with locale segment`() {
        val m = match("https://open.spotify.com/intl-pt/show/3IM0lmZxpFAY7CwMuv9H4g")
        assertNotNull(m)
        assertEquals("3IM0lmZxpFAY7CwMuv9H4g", m!!.captures["showId"])
    }

    @Test
    fun `ignores spotify music links`() {
        assertNull(match("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT"))
        assertNull(match("https://open.spotify.com/episode/abc123DEF456"))
    }

    @Test
    fun `matches overcast and castbox apple-id links`() {
        val overcast = match("https://overcast.fm/itunes1200361736/the-daily")
        assertEquals("overcast", overcast!!.schema.id)
        assertEquals("1200361736", overcast.captures["showId"])

        val castbox = match("https://castbox.fm/vic/1200361736")
        assertEquals("castbox", castbox!!.schema.id)
        assertEquals("1200361736", castbox.captures["showId"])
    }

    @Test
    fun `ignores unknown hosts`() {
        assertNull(match("https://example.com/us/podcast/the-daily/id1200361736"))
    }

    @Test
    fun `ignores non-podcast apple paths`() {
        assertNull(match("https://podcasts.apple.com/us/charts"))
    }
}
