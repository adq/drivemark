package com.drivemark.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

data class PageMetadata(
    val title: String = "",
    val excerpt: String = "",
    val coverUrl: String = "",
)

@Singleton
class MetadataExtractor @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    suspend fun extract(url: String): PageMetadata = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (compatible; DriveMark/1.0)")
                .build()
            val html = okHttpClient.newCall(request).execute().use { it.body.string() }
            val doc = Jsoup.parse(html)
            PageMetadata(
                title = doc.title(),
                excerpt = doc.selectFirst("meta[property=og:description]")?.attr("content")
                    ?: doc.selectFirst("meta[name=description]")?.attr("content")
                    ?: doc.selectFirst("meta[name=twitter:description]")?.attr("content")
                    ?: "",
                coverUrl = doc.selectFirst("meta[property=og:image]")?.attr("content")
                    ?: doc.selectFirst("meta[name=twitter:image]")?.attr("content")
                    ?: "",
            )
        } catch (_: Exception) {
            PageMetadata()
        }
    }
}
