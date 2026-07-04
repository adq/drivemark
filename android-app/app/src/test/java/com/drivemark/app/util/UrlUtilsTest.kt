package com.drivemark.app.util

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UrlUtilsTest {

    @Test
    fun `normalize trims whitespace`() {
        assertEquals("https://example.com", UrlUtils.normalize("  https://example.com  "))
    }

    @Test
    fun `normalize removes trailing slash`() {
        assertEquals("https://example.com", UrlUtils.normalize("https://example.com/"))
    }

    @Test
    fun `normalize lowercases URL`() {
        assertEquals("https://example.com/path", UrlUtils.normalize("HTTPS://EXAMPLE.COM/PATH"))
    }

    @Test
    fun `normalize handles combined whitespace trailing slash and case`() {
        assertEquals("https://example.com/page", UrlUtils.normalize("  HTTPS://Example.COM/Page/  "))
    }

    @Test
    fun `normalize with empty string returns empty`() {
        assertEquals("", UrlUtils.normalize(""))
    }

    @Test
    fun `extractUrl extracts URL from mixed text`() {
        val result = UrlUtils.extractUrl("Check out https://example.com for more info")
        assertEquals("https://example.com", result)
    }

    @Test
    fun `extractUrl returns full text when no URL present`() {
        val input = "just some plain text"
        assertEquals(input, UrlUtils.extractUrl(input))
    }

    @Test
    fun `extractUrl extracts first URL from text with multiple URLs`() {
        val result = UrlUtils.extractUrl("Visit https://first.com or https://second.com")
        assertEquals("https://first.com", result)
    }

    @Test
    fun `extractUrl handles URL-only input`() {
        assertEquals("https://example.com/path?q=1", UrlUtils.extractUrl("https://example.com/path?q=1"))
    }
}
