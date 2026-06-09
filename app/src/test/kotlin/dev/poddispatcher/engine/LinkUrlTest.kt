package dev.poddispatcher.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkUrlTest {

    @Test
    fun `findInText returns a bare URL unchanged`() {
        assertEquals(
            "https://pca.st/itunes/1671669052",
            LinkUrl.findInText("https://pca.st/itunes/1671669052"),
        )
    }

    @Test
    fun `findInText extracts the URL from surrounding share text`() {
        assertEquals(
            "https://podcasts.apple.com/us/podcast/id123?i=456",
            LinkUrl.findInText("Check out this episode! https://podcasts.apple.com/us/podcast/id123?i=456 via Apple Podcasts"),
        )
    }

    @Test
    fun `findInText strips trailing prose punctuation`() {
        assertEquals(
            "https://overcast.fm/itunes123",
            LinkUrl.findInText("Loving this show (https://overcast.fm/itunes123)."),
        )
    }

    @Test
    fun `findInText takes the first URL when several are present`() {
        assertEquals(
            "https://castro.fm/itunes/1",
            LinkUrl.findInText("https://castro.fm/itunes/1 and https://castbox.fm/vic/2"),
        )
    }

    @Test
    fun `findInText returns null without a URL`() {
        assertNull(LinkUrl.findInText("no links here"))
        assertNull(LinkUrl.findInText(""))
        assertNull(LinkUrl.findInText(null))
        assertNull(LinkUrl.findInText("ftp://not.http/only"))
    }
}
