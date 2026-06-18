package com.example.ui.playlist

import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Playlist
import com.example.ui.home.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistManagerScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playlists by viewModel.allPlaylists.collectAsStateWithLifecycle()
    val selectedPlaylistId by viewModel.selectedPlaylistId.collectAsStateWithLifecycle()

    var sortBy by remember { mutableStateOf("date") } // "name", "date", "count"
    var showDeleteDialog by remember { mutableStateOf<Playlist?>(null) }

    val sortedPlaylists = remember(playlists, sortBy) {
        when (sortBy) {
            "name" -> playlists.sortedBy { it.name }
            "count" -> playlists.sortedByDescending { it.channelCount }
            else -> playlists.sortedByDescending { it.addedAt }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Playlists Collection", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    // Sorting options
                    var showSortMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Default.Sort, contentDescription = "Sort Options", tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        modifier = Modifier.background(SurfaceColor)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Sort by Name", color = Color.White) },
                            onClick = { sortBy = "name"; showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Sort by Channel Count", color = Color.White) },
                            onClick = { sortBy = "count"; showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Sort by Date Added", color = Color.White) },
                            onClick = { sortBy = "date"; showSortMenu = false }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundColor)
            )
        },
        containerColor = BackgroundColor
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // General merged playlist selector
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .clickable {
                        viewModel.selectPlaylist(null)
                        Toast.makeText(context, "Merge View Selected (All channels)", Toast.LENGTH_SHORT).show()
                        onBack()
                    },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedPlaylistId == null) SurfaceVariantColor else SurfaceColor
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AllInbox,
                        contentDescription = "Merge playlists",
                        tint = AccentColor,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Merge View (Default)",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 15.sp
                        )
                        Text(
                            "Combines all imported playlists",
                            color = TextSecondaryColor,
                            fontSize = 12.sp
                        )
                    }
                    if (selectedPlaylistId == null) {
                        Icon(Icons.Default.Check, contentDescription = "Active", tint = AccentColor)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "My Subscribed Lists (${sortedPlaylists.size})",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (sortedPlaylists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.PlaylistRemove,
                            contentDescription = "Empty playlists",
                            tint = TextSecondaryColor,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No playlists imported yet", color = TextSecondaryColor, fontSize = 14.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(sortedPlaylists) { playlist ->
                        val isSelected = selectedPlaylistId == playlist.id
                        PlaylistItemView(
                            playlist = playlist,
                            isSelected = isSelected,
                            onSelect = {
                                viewModel.selectPlaylist(playlist.id)
                                Toast.makeText(context, "${playlist.name} active filter", Toast.LENGTH_SHORT).show()
                                onBack()
                            },
                            onRefresh = {
                                scope.launch {
                                    viewModel.refreshPlaylist(playlist.id).collectLatest { res ->
                                        if (res.isSuccess) {
                                            Toast.makeText(context, "Playlist sync successful!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Sync failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            },
                            onDelete = {
                                showDeleteDialog = playlist
                            }
                        )
                    }
                }
            }
        }

        // Delete confirmation dialog
        showDeleteDialog?.let { playlist ->
            AlertDialog(
                onDismissRequest = { showDeleteDialog = null },
                title = { Text("Delete Playlist?", color = Color.White) },
                text = { Text("Are you sure you want to remove '${playlist.name}' and all associated IPTV channels?", color = TextSecondaryColor) },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deletePlaylist(playlist)
                            showDeleteDialog = null
                            Toast.makeText(context, "Playlist deleted", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("Remove", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = null }) {
                        Text("Cancel", color = Color.White)
                    }
                },
                containerColor = SurfaceColor
            )
        }
    }
}

@Composable
fun PlaylistItemView(
    playlist: Playlist,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit
) {
    val dateString = if (playlist.lastRefreshed != null) {
        DateFormat.format("MMM dd yyyy, HH:mm", playlist.lastRefreshed).toString()
    } else {
         "Never"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) SurfaceVariantColor else SurfaceColor)
            .clickable(onClick = onSelect)
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Star badge count or generic TV icon
            Box(
                modifier = Modifier
                    .size(45.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                if (playlist.isDefault) {
                    Icon(Icons.Default.Stars, contentDescription = "Default List", tint = Color(0xFFFFD700))
                } else {
                    Icon(Icons.Default.LiveTv, contentDescription = "IPTV List", tint = AccentColor)
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = playlist.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    // Type Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = playlist.type.uppercase(),
                            fontSize = 8.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Text(
                    text = "${playlist.channelCount} Channels",
                    color = TextSecondaryColor,
                    fontSize = 12.sp
                )

                Text(
                    text = "Sync: $dateString",
                    color = TextSecondaryColor.copy(alpha = 0.7f),
                    fontSize = 10.sp
                )
            }

            // Option Actions: Refresh & Delete
            Row {
                if (playlist.type == "url") {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync", tint = Color.White)
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                }
            }
        }
    }
}
