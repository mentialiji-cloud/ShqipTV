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
    fun signOut() { repository.clearProvider(); _state.value = AppState() }
    fun toggleFavorite(id: String) { _state.value = _state.value.copy(favorites = repository.toggleFavorite(id)) }
    fun clearError() { _state.value = _state.value.copy(error = null) }
}
