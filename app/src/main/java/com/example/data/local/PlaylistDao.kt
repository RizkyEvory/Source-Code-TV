package com.example.data.local

import androidx.room.*
import com.example.data.model.Playlist
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY addedAt DESC")
    fun getAllPlaylistsFlow(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    suspend fun getPlaylistById(id: String): Playlist?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist)

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    @Query("DELETE FROM playlists")
    suspend fun clearAllPlaylists()

    @Query("SELECT COUNT(*) FROM playlists")
    fun getPlaylistCountFlow(): Flow<Int>
}
