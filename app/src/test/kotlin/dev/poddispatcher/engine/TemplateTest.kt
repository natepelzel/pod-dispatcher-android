package dev.poddispatcher.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TemplateTest {

    @Test
    fun `renders plain variables`() {
        assertEquals(
            "https://example.com/lookup?id=42",
            Template.render("https://example.com/lookup?id={showId}", mapOf("showId" to "42")),
        )
    }

    @Test
    fun `missing variable fails the whole template`() {
        assertNull(Template.render("pktc://subscribe/{feedUrl}", emptyMap()))
    }

    @Test
    fun `strip-scheme filter removes the scheme`() {
        assertEquals(
            "feeds.example.com/show.rss",
            Template.render("{feedUrl|strip-scheme}", mapOf("feedUrl" to "https://feeds.example.com/show.rss")),
        )
    }

    @Test
    fun `urlencode filter escapes reserved characters`() {
        assertEquals(
            "q=a+b%26c",
            Template.render("q={term|urlencode}", mapOf("term" to "a b&c")),
        )
    }

    @Test
    fun `base64url filter matches YouTube Music addrssfeed encoding`() {
        // Reference vector taken from a live Odesli response for The Daily.
        assertEquals(
            "aHR0cHM6Ly9mZWVkcy5zaW1wbGVjYXN0LmNvbS9TbDVDU00zUw",
            Template.render("{feedUrl|base64url}", mapOf("feedUrl" to "https://feeds.simplecast.com/Sl5CSM3S")),
        )
    }

    @Test
    fun `literal text without placeholders is unchanged`() {
        assertEquals("no placeholders", Template.render("no placeholders", emptyMap()))
    }
}
