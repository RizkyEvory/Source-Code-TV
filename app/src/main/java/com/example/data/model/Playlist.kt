package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey val id: String,       // UUID
    val name: String,
    val url: String?,                 // null if added via file/paste
    val type: String,                 // "url", "file", "manual"
    val addedAt: Long,
    val lastRefreshed: Long?,
    val channelCount: Int = 0,
    val isDefault: Boolean = false
)
