package com.eagletv.player

data class Playlist(
    val id: String = System.currentTimeMillis().toString(),
    val name: String,
    val server: String,
    val username: String,
    val password: String
) {
    val normalizedServer: String get() = server.trim().trimEnd('/')
}

data class Category(val id: String, val name: String)

data class Channel(
    val id: Int,
    val name: String,
    val logo: String?,
    val categoryId: String,
    val epgId: String?,
    val streamType: String = "live",
    val extension: String = "ts",
    val rating: Double? = null,
    val plot: String? = null,
    val year: String? = null
)

data class Program(
    val title: String,
    val description: String,
    val start: Long,
    val end: Long
)

enum class BufferPreset(val label: String, val minMs: Int, val maxMs: Int, val playbackMs: Int) {
    AUTO("Auto", 15_000, 50_000, 1_500),
    NONE("None", 1_000, 4_000, 250),
    SMALL("Small", 5_000, 15_000, 750),
    NORMAL("Normal", 15_000, 35_000, 1_500),
    HIGH("High", 35_000, 90_000, 3_000)
}

enum class Destination { HOME, LIVE, MOVIES, SERIES, FAVORITES, SEARCH, SETTINGS }
