package com.eagletv.player

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class EagleStore(context: Context) {
    private val prefs = context.getSharedPreferences("eagletv", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun playlists(): List<Playlist> = runCatching {
        gson.fromJson<List<Playlist>>(prefs.getString("playlists", "[]"), object : TypeToken<List<Playlist>>() {}.type)
    }.getOrDefault(emptyList())

    fun savePlaylist(item: Playlist) {
        val updated = playlists().filterNot { it.id == item.id } + item
        prefs.edit().putString("playlists", gson.toJson(updated)).putString("active", item.id).apply()
    }

    fun removePlaylist(id: String) {
        prefs.edit().putString("playlists", gson.toJson(playlists().filterNot { it.id == id })).apply()
    }

    fun activePlaylist(): Playlist? {
        val items = playlists()
        val id = prefs.getString("active", null)
        return items.firstOrNull { it.id == id } ?: items.firstOrNull()
    }

    fun setActive(id: String) = prefs.edit().putString("active", id).apply()

    fun favorites(): Set<Int> = prefs.getStringSet("favorites", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()

    fun toggleFavorite(id: Int): Set<Int> {
        val values = favorites().toMutableSet().apply { if (!add(id)) remove(id) }
        prefs.edit().putStringSet("favorites", values.map(Int::toString).toSet()).apply()
        return values
    }

    fun recentChannels(): List<Int> = runCatching {
        gson.fromJson<List<Int>>(prefs.getString("recent_channels", "[]"), object : TypeToken<List<Int>>() {}.type)
    }.getOrDefault(emptyList())

    fun addRecentChannel(id: Int) {
        val updated = (listOf(id) + recentChannels().filterNot { it == id }).take(20)
        prefs.edit().putString("recent_channels", gson.toJson(updated)).apply()
    }

    fun buffer(): BufferPreset = runCatching {
        BufferPreset.valueOf(prefs.getString("buffer", BufferPreset.AUTO.name)!!)
    }.getOrDefault(BufferPreset.AUTO)

    fun setBuffer(value: BufferPreset) = prefs.edit().putString("buffer", value.name).apply()
}
