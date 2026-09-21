package com.eagletv.player

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class XtreamClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private fun enc(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    private fun apiUrl(p: Playlist, action: String? = null, extras: String = ""): String = buildString {
        append(p.normalizedServer).append("/player_api.php?username=").append(enc(p.username))
        append("&password=").append(enc(p.password))
        action?.let { append("&action=").append(it) }
        append(extras)
    }

    private suspend fun json(url: String) = withContext(Dispatchers.IO) {
        val response = http.newCall(Request.Builder().url(url).header("User-Agent", "EagleTV/1.0").build()).execute()
        response.use {
            if (!it.isSuccessful) error("Server returned ${it.code}")
            JsonParser.parseString(it.body?.string().orEmpty())
        }
    }

    suspend fun authenticate(p: Playlist): String? = runCatching {
        val root = json(apiUrl(p)).asJsonObject
        val user = root.obj("user_info") ?: return@runCatching "Invalid server response"
        if (user.str("auth") == "1" || user.str("status").equals("Active", true)) null else "Login was rejected"
    }.getOrElse { it.message ?: "Unable to connect" }

    suspend fun categories(p: Playlist, kind: String): List<Category> =
        json(apiUrl(p, "get_${kind}_categories")).asJsonArray.mapNotNull { e ->
            val o = e.asJsonObject
            val id = o.str("category_id")
            if (id.isBlank()) null else Category(id, o.str("category_name").ifBlank { "Other" })
        }

    suspend fun liveChannels(p: Playlist, categoryId: String? = null): List<Channel> {
        val extra = categoryId?.let { "&category_id=${enc(it)}" }.orEmpty()
        return parseStreams(json(apiUrl(p, "get_live_streams", extra)).asJsonArray, "live")
    }

    suspend fun movies(p: Playlist, categoryId: String? = null): List<Channel> {
        val extra = categoryId?.let { "&category_id=${enc(it)}" }.orEmpty()
        return parseStreams(json(apiUrl(p, "get_vod_streams", extra)).asJsonArray, "movie")
    }

    suspend fun series(p: Playlist, categoryId: String? = null): List<Channel> {
        val extra = categoryId?.let { "&category_id=${enc(it)}" }.orEmpty()
        val array = json(apiUrl(p, "get_series", extra)).asJsonArray
        return array.map { e ->
            val o = e.asJsonObject
            Channel(
                id = o.int("series_id"), name = o.str("name"), logo = o.str("cover").ifBlank { null },
                categoryId = o.str("category_id"), epgId = null, streamType = "series",
                rating = o.str("rating_5based").toDoubleOrNull(), plot = o.str("plot").ifBlank { null }, year = o.str("releaseDate").take(4)
            )
        }
    }

    private fun parseStreams(array: JsonArray, kind: String) = array.map { e ->
        val o = e.asJsonObject
        Channel(
            id = o.int("stream_id"), name = o.str("name"), logo = o.str("stream_icon").ifBlank { null },
            categoryId = o.str("category_id"), epgId = o.str("epg_channel_id").ifBlank { null },
            streamType = kind, extension = o.str("container_extension").ifBlank { if (kind == "live") "ts" else "mp4" },
            rating = o.str("rating").toDoubleOrNull(), plot = o.str("plot").ifBlank { null }, year = o.str("year").ifBlank { null }
        )
    }

    suspend fun epg(p: Playlist, channelId: Int): List<Program> = runCatching {
        val root = json(apiUrl(p, "get_short_epg", "&stream_id=$channelId&limit=4")).asJsonObject
        (root.getAsJsonArray("epg_listings") ?: JsonArray()).map { e ->
            val o = e.asJsonObject
            Program(
                title = decode(o.str("title")), description = decode(o.str("description")),
                start = o.str("start_timestamp").toLongOrNull()?.times(1000) ?: 0,
                end = o.str("stop_timestamp").toLongOrNull()?.times(1000) ?: 0
            )
        }
    }.getOrDefault(emptyList())

    private fun decode(value: String): String = runCatching {
        String(android.util.Base64.decode(value, android.util.Base64.DEFAULT))
    }.getOrDefault(value)

    fun streamUrl(p: Playlist, item: Channel): String = when (item.streamType) {
        "movie" -> "${p.normalizedServer}/movie/${enc(p.username)}/${enc(p.password)}/${item.id}.${item.extension}"
        else -> "${p.normalizedServer}/live/${enc(p.username)}/${enc(p.password)}/${item.id}.${item.extension}"
    }
}

private fun JsonObject.str(name: String): String = get(name)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
private fun JsonObject.int(name: String): Int = str(name).toIntOrNull() ?: 0
private fun JsonObject.obj(name: String): JsonObject? = get(name)?.takeIf { it.isJsonObject }?.asJsonObject
