package com.example.data.local

import androidx.paging.PagingSource
import androidx.room.*
import com.example.data.model.Channel
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels ORDER BY sortOrder ASC, name ASC")
    fun getAllChannelsFlow(): Flow<List<Channel>>

    @Query("SELECT * FROM channels ORDER BY sortOrder ASC, name ASC")
    fun getAllChannelsPagingSource(): PagingSource<Int, Channel>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId ORDER BY sortOrder ASC, name ASC")
    fun getChannelsByPlaylistPagingSource(playlistId: String): PagingSource<Int, Channel>

    @Query("SELECT * FROM channels WHERE `group` = :group ORDER BY sortOrder ASC, name ASC")
    fun getChannelsByGroupPagingSource(group: String): PagingSource<Int, Channel>

    @Query("SELECT * FROM channels WHERE isFavorite = 1 ORDER BY sortOrder ASC, name ASC")
    fun getFavoriteChannelsFlow(): Flow<List<Channel>>

    @Query("SELECT * FROM channels WHERE isFavorite = 1 ORDER BY sortOrder ASC, name ASC")
    fun getFavoriteChannelsPagingSource(): PagingSource<Int, Channel>

    @Query("SELECT * FROM channels WHERE lastWatched IS NOT NULL ORDER BY lastWatched DESC LIMIT 20")
    fun getRecentlyWatchedChannelsFlow(): Flow<List<Channel>>

    @Query("SELECT * FROM channels WHERE name LIKE :query OR `group` LIKE :query OR streamUrl LIKE :query ORDER BY name ASC")
    fun searchChannelsPagingSource(query: String): PagingSource<Int, Channel>

    @Query("SELECT DISTINCT `group` FROM channels WHERE `group` != '' ORDER BY `group` ASC")
    fun getUniqueGroupsFlow(): Flow<List<String>>

    @Query("SELECT * FROM channels WHERE id = :id LIMIT 1")
    suspend fun getChannelById(id: String): Channel?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<Channel>)

    @Update
    suspend fun updateChannel(channel: Channel)

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylistId(playlistId: String)

    @Query("DELETE FROM channels")
    suspend fun clearAllChannels()

    @Query("SELECT COUNT(*) FROM channels")
    fun getChannelCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM channels")
    suspend fun getChannelCount(): Int
}
