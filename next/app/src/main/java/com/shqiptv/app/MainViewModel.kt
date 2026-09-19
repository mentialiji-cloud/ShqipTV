package com.shqiptv.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shqiptv.app.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppState(
    val provider: ProviderConfig? = null,
    val catalog: Catalog = Catalog(),
    val favorites: Set<String> = emptySet(),
    val loading: Boolean = false,
    val loadingKinds: Set<ContentKind> = emptySet(),
    val episodes: List<MediaItem> = emptyList(),
    val loadingSeriesId: String? = null,
    val epg: Map<String, List<EpgProgram>> = emptyMap(),
    val error: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = IptvRepository(app)
    private val _state = MutableStateFlow(AppState(favorites = repository.favoriteIds()))
    val state: StateFlow<AppState> = _state.asStateFlow()

    init { repository.savedProvider()?.let(::connect) }

    fun connect(config: ProviderConfig) {
        _state.value = _state.value.copy(provider = config, loading = true, error = null)
        viewModelScope.launch {
            runCatching { repository.load(config) }
                .onSuccess { catalog ->
                    repository.saveProvider(config)
                    _state.value = _state.value.copy(catalog = catalog, loading = false)
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(loading = false, error = error.message ?: "Unable to load IPTV service.")
                }
        }
    }

    fun retry() = _state.value.provider?.let(::connect)
    fun loadKind(kind: ContentKind) {
        val current = _state.value
        val provider = current.provider ?: return
        if (!provider.isXtream || kind == ContentKind.LIVE || current.catalog.items(kind).isNotEmpty() || kind in current.loadingKinds) return
        _state.value = current.copy(loadingKinds = current.loadingKinds + kind)
        viewModelScope.launch {
            runCatching { repository.loadKind(provider, kind) }
                .onSuccess { (categories, items) ->
                    val latest = _state.value
                    val catalog = latest.catalog
                    _state.value = latest.copy(
                        catalog = catalog.copy(
                            categories = catalog.categories + categories,
                            movies = if (kind == ContentKind.MOVIE) items else catalog.movies,
                            series = if (kind == ContentKind.SERIES) items else catalog.series,
                        ),
                        loadingKinds = latest.loadingKinds - kind,
                    )
                }
                .onFailure { error ->
                    val latest = _state.value
                    _state.value = latest.copy(loadingKinds = latest.loadingKinds - kind, error = error.message)
                }
        }
    }
    fun loadSeriesEpisodes(series: MediaItem) {
        val current = _state.value
        val provider = current.provider ?: return
        if (current.loadingSeriesId == series.id || (current.episodes.firstOrNull()?.categoryId == series.id)) return
        _state.value = current.copy(episodes = emptyList(), loadingSeriesId = series.id)
        viewModelScope.launch {
            runCatching { repository.loadSeriesEpisodes(provider, series) }
                .onSuccess { episodes -> _state.value = _state.value.copy(episodes = episodes, loadingSeriesId = null) }
                .onFailure { error -> _state.value = _state.value.copy(loadingSeriesId = null, error = error.message) }
        }
    }

    fun loadEpg(channel: MediaItem) {
        val current = _state.value
        val provider = current.provider ?: return
        if (channel.kind != ContentKind.LIVE || channel.id in current.epg) return
        viewModelScope.launch {
            val programs = runCatching { repository.loadShortEpg(provider, channel) }.getOrDefault(emptyList())
            _state.value = _state.value.copy(epg = _state.value.epg + (channel.id to programs))
        }
    }
    fun signOut() { repository.clearProvider(); _state.value = AppState() }
    fun toggleFavorite(id: String) { _state.value = _state.value.copy(favorites = repository.toggleFavorite(id)) }
    fun clearError() { _state.value = _state.value.copy(error = null) }
}
