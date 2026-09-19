package com.shqiptv.app.data

enum class ContentKind { LIVE, MOVIE, SERIES }

data class ProviderConfig(
    val name: String = "My IPTV",
    val server: String = "",
    val username: String = "",
    val password: String = "",
    val m3uUrl: String = "",
) {
    val isXtream get() = server.isNotBlank() && username.isNotBlank() && password.isNotBlank()
    val isM3u get() = m3uUrl.isNotBlank()
}

data class Category(val id: String, val name: String, val kind: ContentKind)

data class MediaItem(
    val id: String,
    val name: String,
    val streamUrl: String,
    val logo: String = "",
    val categoryId: String = "0",
    val kind: ContentKind = ContentKind.LIVE,
    val epgId: String = "",
    val nowPlaying: String = "Live now",
    val nextPlaying: String = "",
)

data class EpgProgram(
    val title: String,
    val description: String = "",
    val start: Long = 0L,
    val end: Long = 0L,
)

data class Catalog(
    val categories: List<Category> = emptyList(),
    val live: List<MediaItem> = emptyList(),
    val movies: List<MediaItem> = emptyList(),
    val series: List<MediaItem> = emptyList(),
) {
    fun items(kind: ContentKind) = when (kind) {
        ContentKind.LIVE -> live
        ContentKind.MOVIE -> movies
        ContentKind.SERIES -> series
    }
}
