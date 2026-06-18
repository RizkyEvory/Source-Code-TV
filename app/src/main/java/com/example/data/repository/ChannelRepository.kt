package com.example.data.repository

import android.content.Context
import android.net.Uri
import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.example.data.local.ChannelDao
import com.example.data.local.PlaylistDao
import com.example.data.model.Channel
import com.example.data.model.Playlist
import com.example.data.parser.M3UParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

class ChannelRepository(
    private val channelDao: ChannelDao,
    private val playlistDao: PlaylistDao,
    private val context: Context
) {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val allPlaylists: Flow<List<Playlist>> = playlistDao.getAllPlaylistsFlow()
    val favoriteChannels: Flow<List<Channel>> = channelDao.getFavoriteChannelsFlow()
    val recentlyWatched: Flow<List<Channel>> = channelDao.getRecentlyWatchedChannelsFlow()
    val uniqueGroups: Flow<List<String>> = channelDao.getUniqueGroupsFlow()
    val channelCount: Flow<Int> = channelDao.getChannelCountFlow()

    fun getChannelsPager(
        playlistId: String?,
        group: String?,
        searchQuery: String?
    ): Pager<Int, Channel> {
        return Pager(
            config = PagingConfig(
                pageSize = 50,
                prefetchDistance = 30,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                if (!searchQuery.isNullOrBlank()) {
                    channelDao.searchChannelsPagingSource("%$searchQuery%")
                } else if (group != null && group != "All" && group != "Favorites") {
                    channelDao.getChannelsByGroupPagingSource(group)
                } else if (group == "Favorites") {
                    channelDao.getFavoriteChannelsPagingSource()
                } else if (playlistId != null) {
                    channelDao.getChannelsByPlaylistPagingSource(playlistId)
                } else {
                    channelDao.getAllChannelsPagingSource()
                }
            }
        )
    }

    suspend fun getChannelById(id: String): Channel? {
        return withContext(Dispatchers.IO) {
            channelDao.getChannelById(id)
        }
    }

    suspend fun updateChannel(channel: Channel) {
        withContext(Dispatchers.IO) {
            channelDao.updateChannel(channel)
        }
    }

    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            channelDao.clearAllChannels()
            playlistDao.clearAllPlaylists()
        }
    }

    suspend fun deletePlaylist(playlist: Playlist) {
        withContext(Dispatchers.IO) {
            channelDao.deleteByPlaylistId(playlist.id)
            playlistDao.deletePlaylist(playlist)
        }
    }

    suspend fun refreshPlaylist(playlistId: String): Result<Playlist> = withContext(Dispatchers.IO) {
        val playlist = playlistDao.getPlaylistById(playlistId)
            ?: return@withContext Result.failure(Exception("Playlist not found"))

        val url = playlist.url ?: return@withContext Result.failure(Exception("Cannot refresh file-based playlist"))

        try {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP error ${response.code}"))
            }

            val bytes = response.body?.bytes() ?: return@withContext Result.failure(Exception("Empty response body"))
            val channels = M3UParser.parse(ByteArrayInputStream(bytes), playlist.id)
            if (channels.isEmpty()) {
                return@withContext Result.failure(Exception("No channels found in playlist"))
            }

            channelDao.deleteByPlaylistId(playlist.id)
            channelDao.insertAll(channels)

            val updatedPlaylist = playlist.copy(
                channelCount = channels.size,
                lastRefreshed = System.currentTimeMillis()
            )
            playlistDao.insertPlaylist(updatedPlaylist)

            Result.success(updatedPlaylist)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addPlaylistFromUrl(name: String, url: String, isDefault: Boolean = false): Result<Playlist> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP error ${response.code}"))
            }

            val bytes = response.body?.bytes() ?: return@withContext Result.failure(Exception("Empty body"))

            val playlistId = if (isDefault) "default_playlist_id" else UUID.randomUUID().toString()
            val channels = M3UParser.parse(ByteArrayInputStream(bytes), playlistId)
            if (channels.isEmpty()) {
                return@withContext Result.failure(Exception("No valid channels found in the playlist"))
            }

            val playlist = Playlist(
                id = playlistId,
                name = name,
                url = url,
                type = "url",
                addedAt = System.currentTimeMillis(),
                lastRefreshed = System.currentTimeMillis(),
                channelCount = channels.size,
                isDefault = isDefault
            )

            // delete existing with same id if any, to avoid conflicts
            channelDao.deleteByPlaylistId(playlistId)
            channelDao.insertAll(channels)
            playlistDao.insertPlaylist(playlist)

            Result.success(playlist)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addPlaylistFromContent(name: String, content: String): Result<Playlist> = withContext(Dispatchers.IO) {
        try {
            val playlistId = UUID.randomUUID().toString()
            val channels = M3UParser.parse(ByteArrayInputStream(content.toByteArray(Charsets.UTF_8)), playlistId)
            if (channels.isEmpty()) {
                return@withContext Result.failure(Exception("No valid channels found in M3U content"))
            }

            val playlist = Playlist(
                id = playlistId,
                name = name,
                url = null,
                type = "manual",
                addedAt = System.currentTimeMillis(),
                lastRefreshed = System.currentTimeMillis(),
                channelCount = channels.size,
                isDefault = false
            )

            channelDao.insertAll(channels)
            playlistDao.insertPlaylist(playlist)

            Result.success(playlist)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addPlaylistFromFileUri(name: String, fileUri: Uri): Result<Playlist> = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val inputStream = resolver.openInputStream(fileUri)
                ?: return@withContext Result.failure(Exception("Cannot open file stream"))

            val playlistId = UUID.randomUUID().toString()
            val channels = inputStream.use { M3UParser.parse(it, playlistId) }
            if (channels.isEmpty()) {
                return@withContext Result.failure(Exception("No valid channels found in M3U file"))
            }

            val playlist = Playlist(
                id = playlistId,
                name = name,
                url = null,
                type = "file",
                addedAt = System.currentTimeMillis(),
                lastRefreshed = System.currentTimeMillis(),
                channelCount = channels.size,
                isDefault = false
            )

            channelDao.insertAll(channels)
            playlistDao.insertPlaylist(playlist)

            Result.success(playlist)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
