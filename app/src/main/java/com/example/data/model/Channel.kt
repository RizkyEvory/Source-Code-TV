package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "channels")
data class Channel(
    @PrimaryKey val id: String,       // UUID
    val name: String,
    val logoUrl: String?,
    val group: String,
    val streamUrl: String,
    val playlistId: String,
    val isFavorite: Boolean = false,
    val lastWatched: Long? = null,    // timestamp
    val sortOrder: Int = 0
)
