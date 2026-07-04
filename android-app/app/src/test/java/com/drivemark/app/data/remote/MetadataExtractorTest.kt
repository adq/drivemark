package com.drivemark.app.data.remote

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class MetadataExtractorTest {

    private lateinit var okHttpClient: OkHttpClient
    private lateinit var extractor: MetadataExtractor

    @Before
    fun setup() {
        okHttpClient = mockk()
        extractor = MetadataExtractor(okHttpClient)
    }

    private fun mockResponse(html: String) {
        val call = mockk<Call>()
        every { okHttpClient.newCall(any()) } returns call
        val request = Request.Builder().url("https://example.com").build()
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(html.toResponseBody())
            .build()
        every { call.execute() } returns response
    }

    private fun mockNullBody() {
        val call = mockk<Call>()
        every { okHttpClient.newCall(any()) } returns call
        val request = Request.Builder().url("https://example.com").build()
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(null)
            .build()
        every { call.execute() } returns response
    }

    @Test
    fun `extract with full OG tags returns all metadata`() = runTest {
        mockResponse("""
            <html><head>
                <title>Example Page</title>
                <meta property="og:description" content="OG description">
                <meta property="og:image" content="https://example.com/og.jpg">
            </head><body></body></html>
        """)

        val result = extractor.extract("https://example.com")

        assertEquals("Example Page", result.title)
        assertEquals("OG description", result.excerpt)
        assertEquals("https://example.com/og.jpg", result.coverUrl)
    }

    @Test
    fun `excerpt falls back to name=description when og missing`() = runTest {
        mockResponse("""
            <html><head>
                <title>Page</title>
                <meta name="description" content="Name description">
            </head><body></body></html>
        """)

        val result = extractor.extract("https://example.com")

        assertEquals("Name description", result.excerpt)
    }

    @Test
    fun `excerpt falls back to twitter description when og and name missing`() = runTest {
        mockResponse("""
            <html><head>
                <title>Page</title>
                <meta name="twitter:description" content="Twitter desc">
            </head><body></body></html>
        """)

        val result = extractor.extract("https://example.com")

        assertEquals("Twitter desc", result.excerpt)
    }

    @Test
    fun `coverUrl falls back to twitter image when og image missing`() = runTest {
        mockResponse("""
            <html><head>
                <title>Page</title>
                <meta name="twitter:image" content="https://example.com/tw.jpg">
            </head><body></body></html>
        """)

        val result = extractor.extract("https://example.com")

        assertEquals("https://example.com/tw.jpg", result.coverUrl)
    }

    @Test
    fun `no meta tags returns title only`() = runTest {
        mockResponse("""
            <html><head><title>Just Title</title></head><body></body></html>
        """)

        val result = extractor.extract("https://example.com")

        assertEquals("Just Title", result.title)
        assertEquals("", result.excerpt)
        assertEquals("", result.coverUrl)
    }

    @Test
    fun `null response body returns empty PageMetadata`() = runTest {
        mockNullBody()

        val result = extractor.extract("https://example.com")

        assertEquals(PageMetadata(), result)
    }

    @Test
    fun `network exception returns empty PageMetadata`() = runTest {
        val call = mockk<Call>()
        every { okHttpClient.newCall(any()) } returns call
        every { call.execute() } throws java.io.IOException("Connection refused")

        val result = extractor.extract("https://example.com")

        assertEquals(PageMetadata(), result)
    }

    @Test
    fun `empty HTML returns empty metadata`() = runTest {
        mockResponse("")

        val result = extractor.extract("https://example.com")

        assertEquals("", result.title)
        assertEquals("", result.excerpt)
        assertEquals("", result.coverUrl)
    }
}
