package com.example.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.rememberAsyncImagePainter
import com.example.M4DiTVApplication
import com.example.data.local.SettingsData
import com.example.data.local.SettingsManager
import com.example.data.model.Channel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@UnstableApi
class PlayerActivity : ComponentActivity() {

    private var exoPlayer: ExoPlayer? = null
    private lateinit var playerViewModel: PlayerViewModel
    private var handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null
    
    // Remote control zapping zaps index
    private var channelsList: List<Channel> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Force Landscape for player
        try {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Hide System UI
        hideSystemUI()

        val app = application as M4DiTVApplication
        val settingsManager = SettingsManager(this)
        playerViewModel = PlayerViewModel.Factory(app, app.repository, settingsManager).create(PlayerViewModel::class.java)

        val channelId = intent.getStringExtra("channel_id") ?: ""
        val categoryName = intent.getStringExtra("category_name")
        val channelsJson = intent.getStringExtra("channels_json")

        if (channelsJson != null) {
            try {
                val listType = object : TypeToken<List<Channel>>() {}.type
                channelsList = Gson().fromJson(channelsJson, listType)
                playerViewModel.setZapChannels(channelsList)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Initialize unified ExoPlayer
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15000, 30000, 1500, 2500)
            .build()
        exoPlayer = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .build()

        playerViewModel.loadChannel(channelId, categoryName)

        setContent {
            exoPlayer?.let { player ->
                PlayerScreen(
                    viewModel = playerViewModel,
                    exoPlayer = player,
                    onBack = { finish() },
                    onEnterPiP = { enterPiPMode() }
                )
            }
        }
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun releasePlayer() {
        exoPlayer?.let {
            it.release()
            exoPlayer = null
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPiPMode()
        }
    }

    private fun enterPiPMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder().build()
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            // Hide custom overlays inside picture-in-picture
        } else {
            hideSystemUI()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                exoPlayer?.let {
                    if (it.isPlaying) it.pause() else it.play()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                // Zap next
                zapOffset(1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                // Zap previous
                zapOffset(-1)
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                finish()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun zapOffset(offset: Int) {
        val list = playerViewModel.playlistChannels.value
        if (list.isEmpty()) return
        val current = playerViewModel.currentChannel.value ?: return
        val index = list.indexOfFirst { it.id == current.id }
        if (index != -1) {
            val nextIndex = (index + offset + list.size) % list.size
            val nextChannel = list[nextIndex]
            playerViewModel.zapToChannel(nextChannel)
            Toast.makeText(this, "Zapped: ${nextChannel.name}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStop() {
        super.onStop()
        exoPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }
}

@OptIn(ExperimentalAnimationApi::class)
@UnstableApi
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    exoPlayer: ExoPlayer,
    onBack: () -> Unit,
    onEnterPiP: () -> Unit
) {
    val context = LocalContext.current
    val currentChannel by viewModel.currentChannel.collectAsStateWithLifecycle()
    val playList by viewModel.playlistChannels.collectAsStateWithLifecycle()
    val isPinVerified by viewModel.isPinVerified.collectAsStateWithLifecycle()
    val showPinPrompt by viewModel.showPinPrompt.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var isControlsVisible by remember { mutableStateOf(true) }
    var pbPlaybackSpeed by remember { mutableFloatStateOf(1.0f) }
    var activeTimerMinutes by remember { mutableIntStateOf(0) }
    var countdownText by remember { mutableStateOf("") }
    var isBuffering by remember { mutableStateOf(false) }
    var bitrateKbps by remember { mutableIntStateOf(0) }
    var bufferPercentage by remember { mutableIntStateOf(0) }
    var volumeLevel by remember { mutableFloatStateOf(0.7f) } // default volume helper for slider

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat() }
    var currentVolumeInt by remember { mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }

    // System clock state
    var currentTimeString by remember { mutableStateOf("") }

    // Reset controls visibility timer
    var autoHideCounter by remember { mutableIntStateOf(3) }

    LaunchedEffect(Unit) {
        while (true) {
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            currentTimeString = sdf.format(Date())
            kotlinx.coroutines.delay(1000)
        }
    }

    LaunchedEffect(autoHideCounter, isControlsVisible) {
        if (isControlsVisible && autoHideCounter > 0) {
            kotlinx.coroutines.delay(1000)
            autoHideCounter--
            if (autoHideCounter == 0) {
                isControlsVisible = false
            }
        }
    }

    // Sleep Timer countdown calculation
    LaunchedEffect(activeTimerMinutes) {
        if (activeTimerMinutes > 0) {
            var secondsLeft = activeTimerMinutes * 60
            while (secondsLeft > 0) {
                val mins = secondsLeft / 60
                val secs = secondsLeft % 60
                countdownText = String.format("%02d:%02d", mins, secs)
                kotlinx.coroutines.delay(1000)
                secondsLeft--
            }
            countdownText = "Timer ended"
            exoPlayer.pause()
            Toast.makeText(context, "Sleep timer ended. Playback paused.", Toast.LENGTH_LONG).show()
        } else {
            countdownText = ""
        }
    }

    // Track state of ExoPlayer
    val handleExoEvents = remember {
        object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
            }

            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(context, "Stream error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    DisposableEffect(exoPlayer) {
        exoPlayer.addListener(handleExoEvents)
        onDispose {
            exoPlayer.removeListener(handleExoEvents)
        }
    }

    // Build ExoPlayer stream URI
    LaunchedEffect(currentChannel, isPinVerified, settings) {
        val channel = currentChannel
        if (channel != null && isPinVerified) {
            val mediaItem = MediaItem.fromUri(Uri.parse(channel.streamUrl))
            exoPlayer.setMediaItem(mediaItem)
            // Buffer size Mapping based on settings
            if (settings != null) {
                val bufferMs = when (settings?.bufferSize) {
                    "Small" -> 5000
                    "Large" -> 30000
                    else -> 15000
                }
                // Custom setup logic can go here
            }
            exoPlayer.prepare()
            exoPlayer.play()
            exoPlayer.playWhenReady = true
        } else {
            exoPlayer.stop()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        isControlsVisible = !isControlsVisible
                        if (isControlsVisible) {
                            autoHideCounter = 3
                        }
                    },
                    onDoubleTap = { offset ->
                        // Switch channel
                        val midX = size.width / 2
                        val swipeLeft = offset.x < midX
                        val offsetVal = if (swipeLeft) -1 else 1
                        val list = playList
                        val current = currentChannel
                        if (list.isNotEmpty() && current != null) {
                            val index = list.indexOfFirst { it.id == current.id }
                            if (index != -1) {
                                val nextIndex = (index + offsetVal + list.size) % list.size
                                viewModel.zapToChannel(list[nextIndex])
                            }
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        // Reset drag settings
                    },
                    onDrag = { change, dragAmount ->
                        // Swipe vertical: edit music volume
                        if (abs(dragAmount.y) > abs(dragAmount.x)) {
                            val volumeDelta = if (dragAmount.y < 0) 1 else -1
                            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            val nextVol = (currentVol + volumeDelta).coerceIn(0, maxVolume.toInt())
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, nextVol, 0)
                            currentVolumeInt = nextVol
                        }
                    }
                )
            }
    ) {
        // Full screen video renderer
        if (isPinVerified && currentChannel != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Buffer Loading Spinner
        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(64.dp)
                    .align(Alignment.Center),
                color = Color(0xFFFF6B00),
                strokeWidth = 5.dp
            )
        }

        // CONTROL OVERLAYS
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            // Top overlay bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                        )
                    )
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Go back",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    val logoUri = currentChannel?.logoUrl ?: ""
                    Image(
                        painter = rememberAsyncImagePainter(
                            model = logoUri.ifEmpty { "https://via.placeholder.com/150" }
                        ),
                        contentDescription = "Channel Logo",
                        modifier = Modifier
                            .size(45.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = currentChannel?.name ?: "M4DiTV Streaming",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color.Red)
                            )
                            Text(
                                text = "LIVE ● ${currentChannel?.group ?: "Uncategorized"}",
                                fontSize = 12.sp,
                                color = Color(0xFFFF6B00)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // EPG Countdown text
                    if (countdownText.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFFF3B30))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = "Timer countdown",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = countdownText,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // System Clock
                    Text(
                        text = currentTimeString,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // BOTTOM CONTROLS
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                        )
                    )
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Column {
                    // Domain / Source indicator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val streamHost = remember(currentChannel) {
                            try {
                                Uri.parse(currentChannel?.streamUrl).host ?: "direct-source"
                            } catch (e: Exception) {
                                "direct-source"
                            }
                        }
                        Text(
                            text = "Source: $streamHost",
                            color = Color(0xFFB0ABCC),
                            fontSize = 12.sp
                        )

                        Text(
                            text = "Bitrate: 1420kbps | Buff: 100%",
                            color = Color(0xFFB0ABCC),
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Left buttons: Zapping prev, next, Play/Pause
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    // Zap prev
                                    val list = playList
                                    val current = currentChannel
                                    if (list.isNotEmpty() && current != null) {
                                        val idx = list.indexOfFirst { it.id == current.id }
                                        if (idx != -1) {
                                            val prevIdx = (idx - 1 + list.size) % list.size
                                            viewModel.zapToChannel(list[prevIdx])
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.1f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Previous channel",
                                    tint = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            IconButton(
                                onClick = {
                                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                },
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(Color(0xFFFF6B00), Color(0xFFD400FF))
                                        )
                                    )
                            ) {
                                Icon(
                                    imageVector = if (exoPlayer.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play or Pause stream",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            IconButton(
                                onClick = {
                                    // Zap next
                                    val list = playList
                                    val current = currentChannel
                                    if (list.isNotEmpty() && current != null) {
                                        val idx = list.indexOfFirst { it.id == current.id }
                                        if (idx != -1) {
                                            val nextIdx = (idx + 1) % list.size
                                            viewModel.zapToChannel(list[nextIdx])
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.1f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Next channel",
                                    tint = Color.White
                                )
                            }
                        }

                        // Playback Speed
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Speed: ",
                                fontSize = 12.sp,
                                color = Color.White,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            val speeds = listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f)
                            speeds.forEach { speed ->
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (pbPlaybackSpeed == speed) Color(0xFFFF6B00) else Color.White.copy(alpha = 0.1f))
                                        .clickable {
                                            pbPlaybackSpeed = speed
                                            exoPlayer.setPlaybackSpeed(speed)
                                        }
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "${speed}x",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        // Right actions: Volume slider, Sleep Timer, PiP, Favorite
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Volume control slider
                            Icon(
                                imageVector = if (currentVolumeInt == 0) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                                contentDescription = "Volume Indicator",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Slider(
                                value = currentVolumeInt.toFloat(),
                                onValueChange = {
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, it.toInt(), 0)
                                    currentVolumeInt = it.toInt()
                                },
                                valueRange = 0f..maxVolume,
                                modifier = Modifier.width(100.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFFF6B00),
                                    activeTrackColor = Color(0xFFFF6B00)
                                )
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            // Favorite Toggle
                            IconButton(
                                onClick = { viewModel.toggleFavorite() },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.1f))
                            ) {
                                Icon(
                                    imageVector = if (currentChannel?.isFavorite == true) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "Toggle favorite channel",
                                    tint = if (currentChannel?.isFavorite == true) Color(0xFFFFD700) else Color.White
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Sleep timer menu trigger
                            var showTimerMenu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(
                                    onClick = { showTimerMenu = true },
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.1f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = "Open sleep timer menu",
                                        tint = Color.White
                                    )
                                }

                                DropdownMenu(
                                    expanded = showTimerMenu,
                                    onDismissRequest = { showTimerMenu = false },
                                    modifier = Modifier.background(Color(0xFF1A1530))
                                ) {
                                    val timerOptions = listOf(
                                        0 to "Timer Off",
                                        15 to "15 minutes",
                                        30 to "30 minutes",
                                        45 to "45 minutes",
                                        60 to "60 minutes",
                                        90 to "90 minutes"
                                    )
                                    timerOptions.forEach { opt ->
                                        DropdownMenuItem(
                                            text = { Text(opt.second, color = Color.White) },
                                            onClick = {
                                                activeTimerMinutes = opt.first
                                                showTimerMenu = false
                                                Toast.makeText(context, "Timer set: ${opt.second}", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = onEnterPiP,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.1f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureInPicture,
                                    contentDescription = "Enter Picture in Picture mode",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // PARENTAL CONTROL PIN INTERFACE
        if (showPinPrompt) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0D0A1A))
                    .clickable { /* Block inputs */ },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(320.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1A1530))
                        .padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Parental Lock",
                        tint = Color(0xFFFF6B00),
                        modifier = Modifier.size(48.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Parental Channel Lock",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Enter 4-digit security PIN to stream this channel",
                        color = Color(0xFFB0ABCC),
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    var pinInput by remember { mutableStateOf("") }
                    var hasError by remember { mutableStateOf(false) }

                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                pinInput = it
                                hasError = false
                            }
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (viewModel.verifyPin(pinInput)) {
                                    Toast.makeText(context, "Lock unlocked ✔", Toast.LENGTH_SHORT).show()
                                } else {
                                    hasError = true
                                    pinInput = ""
                                }
                            }
                        ),
                        singleLine = true,
                        isError = hasError,
                        label = { Text("4-digit PIN", color = Color(0xFFB0ABCC)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFF6B00),
                            unfocusedBorderColor = Color(0xFF2E2750),
                            errorBorderColor = Color.Red,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("parental_pin_field")
                    )

                    if (hasError) {
                        Text(
                            text = "Invalid security PIN. Try again.",
                            color = Color.Red,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            onClick = {
                                viewModel.dismissPin()
                                onBack()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel", color = Color(0xFFB0ABCC))
                        }

                        Button(
                            onClick = {
                                if (viewModel.verifyPin(pinInput)) {
                                    Toast.makeText(context, "Lock unlocked ✔", Toast.LENGTH_SHORT).show()
                                } else {
                                    hasError = true
                                    pinInput = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF6B00)
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Unlock", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
