package com.example.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.SettingsData
import com.example.data.local.SettingsManager
import com.example.data.model.Channel
import com.example.data.repository.ChannelRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class PlayerViewModel(
    application: Application,
    private val repository: ChannelRepository,
    private val settingsManager: SettingsManager
) : AndroidViewModel(application) {

    private val _currentChannel = MutableStateFlow<Channel?>(null)
    val currentChannel: StateFlow<Channel?> = _currentChannel.asStateFlow()

    private val _playlistChannels = MutableStateFlow<List<Channel>>(emptyList())
    val playlistChannels: StateFlow<List<Channel>> = _playlistChannels.asStateFlow()

    private val _isPinVerified = MutableStateFlow(false)
    val isPinVerified: StateFlow<Boolean> = _isPinVerified.asStateFlow()

    private val _showPinPrompt = MutableStateFlow(false)
    val showPinPrompt: StateFlow<Boolean> = _showPinPrompt.asStateFlow()

    val settings: StateFlow<SettingsData?> = settingsManager.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun loadChannel(channelId: String, categoryName: String? = null) {
        viewModelScope.launch {
            val channel = repository.getChannelById(channelId)
            _currentChannel.value = channel

            if (channel != null) {
                // Record last watched
                val updated = channel.copy(lastWatched = System.currentTimeMillis())
                repository.updateChannel(updated)

                // Check PIN requirement
                val pin = settings.value?.parentalPin ?: ""
                val isAdult = channel.group.lowercase().contains("adult") || 
                              channel.group.lowercase().contains("xxx") ||
                              channel.name.lowercase().contains("adult") ||
                              channel.name.lowercase().contains("xxx")
                if (pin.isNotEmpty() && isAdult) {
                    _showPinPrompt.value = true
                    _isPinVerified.value = false
                } else {
                    _showPinPrompt.value = false
                    _isPinVerified.value = true
                }

                // Retrieve sibling channels for zapping
                repository.favoriteChannels.collectLatest { favs ->
                    if (categoryName == "Favorites") {
                        _playlistChannels.value = favs
                    } else {
                        // Gather sibling channels for easy channel flip
                        // For simplicity, grab favorite lists or other repositories
                        // Or just use the playlist channels
                        _playlistChannels.value = listOf(channel)
                    }
                }
            }
        }
    }

    fun setZapChannels(channels: List<Channel>) {
        _playlistChannels.value = channels
    }

    fun verifyPin(enteredPin: String): Boolean {
        val pin = settings.value?.parentalPin ?: ""
        return if (pin == enteredPin) {
            _isPinVerified.value = true
            _showPinPrompt.value = false
            true
        } else {
            false
        }
    }

    fun dismissPin() {
        _showPinPrompt.value = false
    }

    fun toggleFavorite() {
        val channel = _currentChannel.value ?: return
        viewModelScope.launch {
            val updated = channel.copy(isFavorite = !channel.isFavorite)
            repository.updateChannel(updated)
            _currentChannel.value = updated
        }
    }

    fun zapToChannel(channel: Channel) {
        viewModelScope.launch {
            _currentChannel.value = channel
            val updated = channel.copy(lastWatched = System.currentTimeMillis())
            repository.updateChannel(updated)

            val pin = settings.value?.parentalPin ?: ""
            val isAdult = channel.group.lowercase().contains("adult") || 
                          channel.group.lowercase().contains("xxx") ||
                          channel.name.lowercase().contains("adult") ||
                          channel.name.lowercase().contains("xxx")
            if (pin.isNotEmpty() && isAdult) {
                _showPinPrompt.value = true
                _isPinVerified.value = false
            } else {
                _showPinPrompt.value = false
                _isPinVerified.value = true
            }
        }
    }

    class Factory(
        private val application: Application,
        private val repository: ChannelRepository,
        private val settingsManager: SettingsManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PlayerViewModel(application, repository, settingsManager) as T
        }
    }
}
