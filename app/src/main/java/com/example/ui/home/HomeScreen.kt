package com.example.ui.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.rememberAsyncImagePainter
import com.example.data.model.Channel
import com.example.ui.player.PlayerActivity
import com.example.util.NetworkUtils
import com.google.gson.Gson
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// Brand color palette
val BackgroundColor = Color(0xFF0D0A1A)
val SurfaceColor = Color(0xFF1A1530)
val SurfaceVariantColor = Color(0xFF241E3D)
val AccentColor = Color(0xFFFF6B00)
val TextPrimaryColor = Color(0xFFFFFFFF)
val TextSecondaryColor = Color(0xFFB0ABCC)
val DividerColor = Color(0xFF2E2750)
val PrimaryGradient = Brush.horizontalGradient(listOf(Color(0xFFFF6B00), Color(0xFFD400FF)))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToPlaylists: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val selectedGroup by viewModel.selectedGroup.collectAsStateWithLifecycle()
    val totalChannelCount by viewModel.totalChannelCount.collectAsStateWithLifecycle()
    val recentlyWatched by viewModel.recentlyWatched.collectAsStateWithLifecycle()
    val uniqueGroups by viewModel.uniqueGroups.collectAsStateWithLifecycle()
    val favoriteChannels by viewModel.favoriteChannels.collectAsStateWithLifecycle()

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var offersLastChannelToResume by remember { mutableStateOf<Channel?>(null) }
    var lastWatchedList by remember { mutableStateOf<List<Channel>>(emptyList()) }

    val pagedChannels = viewModel.channelsFlow.collectAsLazyPagingItems()

    // Add channel state
    var showAddChannelSheet by remember { mutableStateOf(false) }

    // EPG/Connection check
    val isOnline = remember { NetworkUtils.isNetworkAvailable(context) }

    LaunchedEffect(recentlyWatched) {
        if (recentlyWatched.isNotEmpty() && offersLastChannelToResume == null) {
            offersLastChannelToResume = recentlyWatched.first()
            lastWatchedList = recentlyWatched
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor),
        containerColor = BackgroundColor,
        topBar = {
            if (isSearchActive) {
                SearchBarView(
                    query = searchQuery,
                    onQueryChange = {
                        searchQuery = it
                        viewModel.updateSearchQuery(it)
                    },
                    onClose = {
                        isSearchActive = false
                        searchQuery = ""
                        viewModel.updateSearchQuery("")
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = "M4DiTV",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            style = LocalTextStyle.current.copy(
                                brush = PrimaryGradient
                            )
                        )
                    },
                    actions = {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = BackgroundColor
                    )
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddChannelSheet = true },
                containerColor = Color.Transparent,
                shape = CircleShape,
                modifier = Modifier
                    .shadow(8.dp, CircleShape)
                    .background(PrimaryGradient, CircleShape)
                    .testTag("add_channel_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Playlist", tint = Color.White)
            }
        },
        bottomBar = {
            // Adaptive: Show bottom bar on phone/tablet, or custom layout
            NavigationBar(
                containerColor = SurfaceColor,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = true,
                    onClick = { /* Already Home */ },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentColor,
                        selectedTextColor = AccentColor,
                        unselectedIconColor = TextSecondaryColor,
                        unselectedTextColor = TextSecondaryColor,
                        indicatorColor = SurfaceVariantColor
                    )
                )

                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToPlaylists,
                    icon = { Icon(Icons.Default.PlaylistPlay, contentDescription = "Playlists") },
                    label = { Text("Playlists") },
                    colors = NavigationBarItemDefaults.colors(
                        unselectedIconColor = TextSecondaryColor,
                        unselectedTextColor = TextSecondaryColor
                    )
                )

                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToSettings,
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    colors = NavigationBarItemDefaults.colors(
                        unselectedIconColor = TextSecondaryColor,
                        unselectedTextColor = TextSecondaryColor
                    )
                )
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isTablet = maxWidth > 600.dp

            if (isTablet) {
                // Side-by-side tablet view
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        MainContent(
                            viewModel = viewModel,
                            uniqueGroups = uniqueGroups,
                            selectedGroup = selectedGroup,
                            pagedChannels = pagedChannels,
                            recentlyWatched = recentlyWatched,
                            totalChannelCount = totalChannelCount,
                            isOnline = isOnline,
                            errorMessage = errorMessage,
                            isLoading = isLoading
                        )
                    }

                    // Mini preview player column
                    Box(
                        modifier = Modifier
                            .width(320.dp)
                            .fillMaxHeight()
                            .background(SurfaceColor)
                            .padding(16.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        MiniPreviewPlayer(viewModel = viewModel)
                    }
                }
            } else {
                // Mobile layout
                Box(modifier = Modifier.fillMaxSize()) {
                    MainContent(
                        viewModel = viewModel,
                        uniqueGroups = uniqueGroups,
                        selectedGroup = selectedGroup,
                        pagedChannels = pagedChannels,
                        recentlyWatched = recentlyWatched,
                        totalChannelCount = totalChannelCount,
                        isOnline = isOnline,
                        errorMessage = errorMessage,
                        isLoading = isLoading
                    )

                    // Auto resume snackbar invitation
                    offersLastChannelToResume?.let { last ->
                        Snackbar(
                            action = {
                                TextButton(
                                    onClick = {
                                        offersLastChannelToResume = null
                                        val intent = Intent(context, PlayerActivity::class.java).apply {
                                            putExtra("channel_id", last.id)
                                            putExtra("category_name", "Recently Watched")
                                            putExtra("channels_json", Gson().toJson(lastWatchedList))
                                        }
                                        context.startActivity(intent)
                                    }
                                ) {
                                    Text("RESUME", color = Color(0xFFFF6B00), fontWeight = FontWeight.Bold)
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                                .testTag("resume_snackbar"),
                            containerColor = SurfaceVariantColor,
                            contentColor = Color.White
                        ) {
                            Text("Resume last watched: ${last.name}?")
                        }
                    }
                }
            }
        }

        if (showAddChannelSheet) {
            AddPlaylistBottomSheet(
                viewModel = viewModel,
                onDismiss = { showAddChannelSheet = false }
            )
        }
    }
}

@Composable
fun MainContent(
    viewModel: HomeViewModel,
    uniqueGroups: List<String>,
    selectedGroup: String,
    pagedChannels: androidx.paging.compose.LazyPagingItems<Channel>,
    recentlyWatched: List<Channel>,
    totalChannelCount: Int,
    isOnline: Boolean,
    errorMessage: String?,
    isLoading: Boolean
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        // Offline banner
        if (!isOnline) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFF3B30))
                    .padding(vertical = 4.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No internet connection — showing cached channels",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Top horizontal chip filters for groups
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // "All" filter
            val isAllSelected = selectedGroup == "All"
            CategoryChip(name = "All", isSelected = isAllSelected, onClick = { viewModel.selectGroup("All") })

            // "Favorites" filter
            val isFavSelected = selectedGroup == "Favorites"
            CategoryChip(name = "Favorites", isSelected = isFavSelected, onClick = { viewModel.selectGroup("Favorites") })

            // Other groups
            uniqueGroups.forEach { group ->
                val isSelected = selectedGroup == group
                CategoryChip(name = group, isSelected = isSelected, onClick = { viewModel.selectGroup(group) })
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AccentColor)
            }
        }

        if (errorMessage != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(errorMessage, color = Color.Red, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.loadDefaultPlaylist() },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentColor)
                ) {
                    Text("Retry", color = Color.White)
                }
            }
        }

        // Channel list with standard Paging list or blank placeholder
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Recently Watched at top of list
            if (recentlyWatched.isNotEmpty() && selectedGroup == "All") {
                item {
                    Text(
                        text = "Recently Watched",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        items(recentlyWatched) { channel ->
                            RecentlyWatchedCard(channel = channel, onClick = {
                                val intent = Intent(context, PlayerActivity::class.java).apply {
                                    putExtra("channel_id", channel.id)
                                    putExtra("category_name", "Recently Watched")
                                    putExtra("channels_json", Gson().toJson(recentlyWatched))
                                }
                                context.startActivity(intent)
                            })
                        }
                    }
                }
            }

            // Main Pager Channel Items
            item {
                Text(
                    text = if (selectedGroup == "Favorites") "Favorites" else "Live Channels ($totalChannelCount)",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            if (pagedChannels.itemCount == 0 && !isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.LiveTv, contentDescription = "No channels", tint = TextSecondaryColor, modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("No channels found", color = TextSecondaryColor, fontSize = 14.sp)
                        }
                    }
                }
            }

            items(pagedChannels.itemCount) { index ->
                val channel = pagedChannels[index]
                if (channel != null) {
                    val channelsJsonList = remember(pagedChannels) {
                        // Assemble simple list of available channels to feed the zapping queue
                        val list = mutableListOf<Channel>()
                        for (i in 0 until minOf(pagedChannels.itemCount, 50)) {
                            pagedChannels[i]?.let { list.add(it) }
                        }
                        list
                    }

                    ChannelListItem(
                        channel = channel,
                        onClick = {
                            val intent = Intent(context, PlayerActivity::class.java).apply {
                                putExtra("channel_id", channel.id)
                                putExtra("category_name", selectedGroup)
                                putExtra("channels_json", Gson().toJson(channelsJsonList))
                            }
                            context.startActivity(intent)
                        },
                        onToggleFavorite = { viewModel.toggleFavorite(channel) }
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryChip(name: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(end = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .background(if (isSelected) PrimaryGradient else Brush.linearGradient(listOf(SurfaceVariantColor, SurfaceVariantColor)))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (name == "Favorites") {
                Icon(Icons.Default.Star, contentDescription = null, tint = if (isSelected) Color.White else Color(0xFFFFD700), modifier = Modifier.size(16.dp).padding(end = 4.dp))
            }
            Text(
                text = name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun RecentlyWatchedCard(channel: Channel, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .size(100.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = rememberAsyncImagePainter(model = channel.logoUrl ?: "https://via.placeholder.com/150"),
                contentDescription = null,
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = channel.name,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1
            )
        }
    }
}

@Composable
fun ChannelListItem(
    channel: Channel,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceColor)
            .border(1.dp, DividerColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Image(
                painter = rememberAsyncImagePainter(model = channel.logoUrl ?: "https://via.placeholder.com/150"),
                contentDescription = "Logo",
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceVariantColor),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1
                )
                Text(
                    text = channel.group,
                    fontSize = 12.sp,
                    color = TextSecondaryColor,
                    maxLines = 1
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFFF3B30))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("LIVE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (channel.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Favorite Toggle",
                    tint = if (channel.isFavorite) Color(0xFFFFD700) else TextSecondaryColor
                )
            }
        }
    }
}

@Composable
fun MiniPreviewPlayer(viewModel: HomeViewModel) {
    // A mini mock or active streaming player preview for tablets
    val context = LocalContext.current
    var previewPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    DisposableEffect(Unit) {
        val player = ExoPlayer.Builder(context).build()
        previewPlayer = player
        onDispose {
            player.release()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Channel Preview",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PlayCircleFilled, contentDescription = null, tint = AccentColor, modifier = Modifier.size(48.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Select a channel to play in fullscreen.",
            color = TextSecondaryColor,
            fontSize = 12.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun SearchBarView(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(BackgroundColor)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
        }

        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search channel, category, source url...", color = TextSecondaryColor) },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = SurfaceColor,
                unfocusedContainerColor = SurfaceColor,
                cursorColor = AccentColor
            ),
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .testTag("search_field")
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPlaylistBottomSheet(
    viewModel: HomeViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeTab by remember { mutableIntStateOf(0) }
    var playlistName by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("") }
    var pasteInput by remember { mutableStateOf("") }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedFileUri = uri
            selectedFileName = uri.lastPathSegment ?: "m3u_file.m3u"
            if (playlistName.isEmpty()) {
                playlistName = selectedFileName.substringBeforeLast(".")
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                "Add M3U IPTV Playlist",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Tabs
            SecondaryTabRow(
                selectedTabIndex = activeTab,
                containerColor = SurfaceColor,
                contentColor = Color.White
            ) {
                Tab(selected = activeTab == 0, onClick = { activeTab = 0 }) {
                    Text("URL Link", modifier = Modifier.padding(12.dp))
                }
                Tab(selected = activeTab == 1, onClick = { activeTab = 1 }) {
                    Text("Paste Text", modifier = Modifier.padding(12.dp))
                }
                Tab(selected = activeTab == 2, onClick = { activeTab = 2 }) {
                    Text("Local File", modifier = Modifier.padding(12.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Playlist Name field
            OutlinedTextField(
                value = playlistName,
                onValueChange = { playlistName = it },
                label = { Text("Playlist Name", color = TextSecondaryColor) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentColor,
                    unfocusedBorderColor = DividerColor,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            when (activeTab) {
                0 -> {
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("M3U Playlist URL", color = TextSecondaryColor) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentColor,
                            unfocusedBorderColor = DividerColor,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                1 -> {
                    OutlinedTextField(
                        value = pasteInput,
                        onValueChange = { pasteInput = it },
                        label = { Text("Paste M3U raw content", color = TextSecondaryColor) },
                        minLines = 6,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentColor,
                            unfocusedBorderColor = DividerColor,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                2 -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, DividerColor, RoundedCornerShape(8.dp))
                            .clickable { filePicker.launch("*/*") }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, tint = AccentColor)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (selectedFileName.isEmpty()) "Browse M3U File" else selectedFileName,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (playlistName.isEmpty()) {
                        Toast.makeText(context, "Please enter a playlist name", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    scope.launch {
                        when (activeTab) {
                            0 -> {
                                viewModel.addPlaylistFromUrl(playlistName, urlInput).collectLatest { res ->
                                    if (res.isSuccess) {
                                        Toast.makeText(context, "Playlist added! 🎉", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    } else {
                                        Toast.makeText(context, "Error: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                            1 -> {
                                viewModel.addPlaylistFromContent(playlistName, pasteInput).collectLatest { res ->
                                    if (res.isSuccess) {
                                        Toast.makeText(context, "Playlist parsed! 🎉", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    } else {
                                        Toast.makeText(context, "Parse error: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                            2 -> {
                                val uri = selectedFileUri
                                if (uri != null) {
                                    viewModel.addPlaylistFromFile(playlistName, uri).collectLatest { res ->
                                        if (res.isSuccess) {
                                            Toast.makeText(context, "File imported! 🎉", Toast.LENGTH_SHORT).show()
                                            onDismiss()
                                        } else {
                                            Toast.makeText(context, "Error: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                } else {
                                    Toast.makeText(context, "Select a file first", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentColor
                )
            ) {
                Text("Process & Add Playlist", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
