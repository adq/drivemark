package com.drivemark.app.util

import android.util.Patterns

object UrlUtils {
    fun extractUrl(text: String): String {
        val matcher = Patterns.WEB_URL.matcher(text)
        return if (matcher.find()) matcher.group() ?: text else text
    }

    fun normalize(url: String): String {
        return url.trim().removeSuffix("/").lowercase()
    }
}
