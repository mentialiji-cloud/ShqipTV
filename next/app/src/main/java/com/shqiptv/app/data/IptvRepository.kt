package com.shqiptv.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class IptvRepository(private val context: Context) {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val prefs = context.getSharedPreferences("shqip_tv_fast", Context.MODE_PRIVATE)

    fun savedProvider(): ProviderConfig? {
        val server = prefs.getString("server", "").orEmpty()
        val m3u = prefs.getString("m3u", "").orEmpty()
        if (server.isBlank() && m3u.isBlank()) return null
        return ProviderConfig(
            name = prefs.getString("name", "My IPTV").orEmpty(),
            server = server,
            username = prefs.getString("username", "").orEmpty(),
            password = prefs.getString("password", "").orEmpty(),
            m3uUrl = m3u,
        )
    }

    fun saveProvider(config: ProviderConfig) {
        prefs.edit()
            .putString("name", config.name)
            .putString("server", config.server.trim().trimEnd('/'))
            .putString("username", config.username.trim())
            .putString("password", config.password)
            .putString("m3u", config.m3uUrl.trim())
            .apply()
    }

    fun clearProvider() = prefs.edit().clear().apply()

    fun favoriteIds(): Set<String> = prefs.getStringSet("favorites", emptySet())?.toSet().orEmpty()

    fun toggleFavorite(id: String): Set<String> {
        val ids = favoriteIds().toMutableSet()
        if (!ids.add(id)) ids.remove(id)
        prefs.edit().putStringSet("favorites", ids).apply()
        return ids
    }

    suspend fun load(config: ProviderConfig): Catalog = withContext(Dispatchers.IO) {
        if (config.isXtream) loadXtream(config) else loadM3u(config.m3uUrl)
    }

    private suspend fun loadXtream(config: ProviderConfig): Catalog = coroutineScope {
        val base = config.server.trimEnd('/')
        val user = enc(config.username)
        val pass = enc(config.password)
        val api = "$base/player_api.php?username=$user&password=$pass"

        val auth = getJson(api).asJsonObject
        if (auth["user_info"]?.asJsonObject?.get("auth")?.asInt != 1) {
            error("The IPTV username, password, or server address is incorrect.")
        }

        val liveCats = async { categories("$api&action=get_live_categories", ContentKind.LIVE) }
        val movieCats = async { categories("$api&action=get_vod_categories", ContentKind.MOVIE) }
        val seriesCats = async { categories("$api&action=get_series_categories", ContentKind.SERIES) }
        val live = async { xtreamItems("$api&action=get_live_streams", config, ContentKind.LIVE) }
        val movies = async { xtreamItems("$api&action=get_vod_streams", config, ContentKind.MOVIE) }
        val series = async { xtreamItems("$api&action=get_series", config, ContentKind.SERIES) }
        Catalog(
            categories = liveCats.await() + movieCats.await() + seriesCats.await(),
            live = live.await(), movies = movies.await(), series = series.await(),
        )
    }

    private fun categories(url: String, kind: ContentKind): List<Category> =
        getJson(url).asJsonArray.mapNotNull { element ->
            val o = element.asJsonObject
            val id = o.string("category_id")
            val name = o.string("category_name")
            if (id.isBlank() || name.isBlank()) null else Category(id, name, kind)
        }

    private fun xtreamItems(url: String, c: ProviderConfig, kind: ContentKind): List<MediaItem> {
        val base = c.server.trimEnd('/')
        return getJson(url).asJsonArray.mapNotNull { element ->
            val o = element.asJsonObject
            val rawId = when (kind) {
                ContentKind.LIVE -> o.string("stream_id")
                ContentKind.MOVIE -> o.string("stream_id")
                ContentKind.SERIES -> o.string("series_id")
            }
            val name = o.string("name")
            if (rawId.isBlank() || name.isBlank()) return@mapNotNull null
            val ext = o.string("container_extension").ifBlank { "mp4" }
            val stream = when (kind) {
                ContentKind.LIVE -> "$base/live/${enc(c.username)}/${enc(c.password)}/$rawId.ts"
                ContentKind.MOVIE -> "$base/movie/${enc(c.username)}/${enc(c.password)}/$rawId.$ext"
                ContentKind.SERIES -> ""
            }
            MediaItem(
                id = "${kind.name}:$rawId", name = name, streamUrl = stream,
                logo = o.string(if (kind == ContentKind.SERIES) "cover" else "stream_icon"),
                categoryId = o.string("category_id"), kind = kind,
                epgId = o.string("epg_channel_id"),
            )
        }
    }

    private fun loadM3u(url: String): Catalog {
        require(url.isNotBlank()) { "Enter an M3U playlist URL." }
        val text = getText(url)
        val items = ArrayList<MediaItem>(4096)
        var info = ""
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF", true) -> info = line
                line.isNotBlank() && !line.startsWith("#") && info.isNotBlank() -> {
                    val name = info.substringAfterLast(',').trim().ifBlank { "Channel" }
                    val group = attribute(info, "group-title").ifBlank { "All channels" }
                    val id = attribute(info, "tvg-id").ifBlank { line.hashCode().toString() }
                    items += MediaItem(
                        id = "LIVE:$id", name = name, streamUrl = line,
                        logo = attribute(info, "tvg-logo"), categoryId = group,
                        epgId = attribute(info, "tvg-id"),
                    )
                    info = ""
                }
            }
        }
        val categories = items.asSequence().map { it.categoryId }.distinct()
            .map { Category(it, it, ContentKind.LIVE) }.toList()
        return Catalog(categories = categories, live = items)
    }

    private fun attribute(line: String, key: String): String =
        Regex("""$key="([^"]*)""", RegexOption.IGNORE_CASE).find(line)?.groupValues?.get(1).orEmpty()

    private fun JsonObject.string(key: String): String = get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun getJson(url: String) = gson.fromJson(getText(url), com.google.gson.JsonElement::class.java)
    private fun getText(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", "ShqipTV/2.0 AndroidTV").build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("IPTV server returned ${response.code}.")
            response.body?.string() ?: error("The IPTV server returned an empty response.")
        }
    }
}
