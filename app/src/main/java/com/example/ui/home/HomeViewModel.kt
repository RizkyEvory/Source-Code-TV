package com.example.ui.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.M4DiTVApplication
import com.example.data.local.SettingsManager
import com.example.data.model.Channel
import com.example.data.model.Playlist
import com.example.data.repository.ChannelRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel(
    application: Application,
    private val repository: ChannelRepository,
    private val settingsManager: SettingsManager
) : AndroidViewModel(application) {

    private val _selectedGroup = MutableStateFlow("All")
    val selectedGroup: StateFlow<String> = _selectedGroup.asStateFlow()

    private val _selectedPlaylistId = MutableStateFlow<String?>(null)
    val selectedPlaylistId: StateFlow<String?> = _selectedPlaylistId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val allPlaylists = repository.allPlaylists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), emptyList())
    val favoriteChannels = repository.favoriteChannels.stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), emptyList())
    val recentlyWatched = repository.recentlyWatched.stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), emptyList())
    val uniqueGroups = repository.uniqueGroups.stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), emptyList())
    val totalChannelCount = repository.channelCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), 0)

    // Current channels paging stream based on filters
    val channelsFlow: Flow<PagingData<Channel>> = combine(
        _selectedPlaylistId,
        _selectedGroup,
        _searchQuery
    ) { playlistId, group, search ->
        Triple(playlistId, group, search)
    }.flatMapLatest { (playlistId, group, search) ->
        repository.getChannelsPager(
            playlistId = playlistId,
            group = group,
            searchQuery = search.ifBlank { null }
        ).flow
    }.cachedIn(viewModelScope)

    init {
        // Automatically fetch default playlist if there are none
        viewModelScope.launch {
            allPlaylists.collectLatest { list ->
                if (list.isEmpty() && !_isLoading.value) {
                    loadDefaultPlaylist()
                }
            }
        }
    }

    fun selectGroup(group: String) {
        _selectedGroup.value = group
    }

    fun selectPlaylist(playlistId: String?) {
        _selectedPlaylistId.value = playlistId
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun loadDefaultPlaylist() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = repository.addPlaylistFromUrl(
                name = "M4DiTV Default",
                url = "https://rizkyevory.github.io/merged_iptv_simple.m3u",
                isDefault = true
            )
            _isLoading.value = false
            if (result.isFailure) {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "Unknown fetch error occurred"
            }
        }
    }

    fun addPlaylistFromUrl(name: String, url: String): Flow<Result<Playlist>> = flow {
        _isLoading.value = true
        _errorMessage.value = null
        val result = repository.addPlaylistFromUrl(name, url)
        _isLoading.value = false
        emit(result)
    }

    fun addPlaylistFromContent(name: String, content: String): Flow<Result<Playlist>> = flow {
        _isLoading.value = true
        _errorMessage.value = null
        val result = repository.addPlaylistFromContent(name, content)
        _isLoading.value = false
        emit(result)
    }

    fun addPlaylistFromFile(name: String, fileUri: Uri): Flow<Result<Playlist>> = flow {
        _isLoading.value = true
        _errorMessage.value = null
        val result = repository.addPlaylistFromFileUri(name, fileUri)
        _isLoading.value = false
        emit(result)
    }

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch {
            repository.updateChannel(channel.copy(isFavorite = !channel.isFavorite))
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            repository.deletePlaylist(playlist)
        }
    }

    fun refreshPlaylist(playlistId: String): Flow<Result<Playlist>> = flow {
        _isLoading.value = true
        val result = repository.refreshPlaylist(playlistId)
        _isLoading.value = false
        emit(result)
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    class Factory(
        private val application: Application,
        private val repository: ChannelRepository,
        private val settingsManager: SettingsManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(application, repository, settingsManager) as T
        }
    }
}
