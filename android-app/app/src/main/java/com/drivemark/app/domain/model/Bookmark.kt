package com.drivemark.app.domain.model

data class Bookmark(
    val id: String,
    val url: String,
    val title: String,
    val folder: String,
    val dateAdded: String,
    val notes: String = "",
    val excerpt: String = "",
    val coverUrl: String = "",
    val modified: String = "",
)
