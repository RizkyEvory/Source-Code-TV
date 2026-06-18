package com.example.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.SettingsData
import com.example.data.local.SettingsManager
import com.example.ui.home.*
import com.example.util.FileUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: HomeViewModel,
    settingsManager: SettingsManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val settings by settingsManager.settingsFlow.collectAsStateWithLifecycle(initialValue = null)
    val playlists by viewModel.allPlaylists.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteChannels.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalChannelCount.collectAsStateWithLifecycle()

    var showClearDataDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var pinValue by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Playback", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundColor)
            )
        },
        containerColor = BackgroundColor
    ) { innerPadding ->
        settings?.let { data ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Section Playback
                SettingsSectionHeader("Playback Engine")

                // Quality Selector
                SettingsOptionRow(
                    title = "Default Stream Quality",
                    subtitle = "Current: ${data.defaultQuality}",
                    onClick = {
                        val QualityEnum = listOf("Auto", "480p", "720p", "1080p")
                        val nextIdx = (QualityEnum.indexOf(data.defaultQuality) + 1) % QualityEnum.size
                        scope.launch { settingsManager.updateQuality(QualityEnum[nextIdx]) }
                    }
                )

                // Buffer Size Row
                SettingsOptionRow(
                    title = "Buffer Length Size",
                    subtitle = "Current: ${data.bufferSize} load control threshold",
                    onClick = {
                        val BufferEnum = listOf("Small", "Medium", "Large")
                        val nextIdx = (BufferEnum.indexOf(data.bufferSize) + 1) % BufferEnum.size
                        scope.launch { settingsManager.updateBufferSize(BufferEnum[nextIdx]) }
                    }
                )

                // Hard Accelerated Row
                SettingsSwitchRow(
                    title = "Hardware Acceleration",
                    subtitle = "Enable direct hardware decoding with codecs",
                    checked = data.hardwareAccel,
                    onCheckedChange = { scope.launch { settingsManager.updateHardwareAccel(it) } }
                )

                // Reconnect Row
                SettingsSwitchRow(
                    title = "Reconnect on Error",
                    subtitle = "Stream retries exponential backoff delay support",
                    checked = data.reconnectOnError,
                    onCheckedChange = { scope.launch { settingsManager.updateReconnectOnError(it) } }
                )

                // Max Retries Slider
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Text("Max Connection Retries (${data.maxRetries})", color = Color.White, fontSize = 14.sp)
                    Slider(
                        value = data.maxRetries.toFloat(),
                        onValueChange = { scope.launch { settingsManager.updateMaxRetries(it.toInt()) } },
                        valueRange = 1f..10f,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentColor,
                            activeTrackColor = AccentColor
                        )
                    )
                }

                SettingsSectionHeader("Display & UI layout")

                // ListView vs GridView setting
                SettingsOptionRow(
                    title = "Channels View Presentation",
                    subtitle = "View Mode: ${data.listViewType}",
                    onClick = {
                        val views = listOf("List", "Grid")
                        val nextView = views[(views.indexOf(data.listViewType) + 1) % views.size]
                        scope.launch { settingsManager.updateListViewType(nextView) }
                    }
                )

                SettingsSwitchRow(
                    title = "Show Channel Number",
                    subtitle = "Display quick zap index numbers in listing",
                    checked = data.showChannelNumber,
                    onCheckedChange = { scope.launch { settingsManager.updateShowChannelNumber(it) } }
                )

                SettingsSwitchRow(
                    title = "Show Group Title Badge",
                    subtitle = "Overlay group-title tags onto rows",
                    checked = data.showGroupBadge,
                    onCheckedChange = { scope.launch { settingsManager.updateShowGroupBadge(it) } }
                )

                SettingsSectionHeader("Playlist & Sync")

                SettingsOptionRow(
                    title = "Auto-Refresh Interval",
                    subtitle = "Re-fetch interval: ${data.autoRefreshInterval}",
                    onClick = {
                        val schedule = listOf("Never", "6h", "12h", "24h")
                        val next = schedule[(schedule.indexOf(data.autoRefreshInterval) + 1) % schedule.size]
                        scope.launch { settingsManager.updateAutoRefreshInterval(next) }
                    }
                )

                SettingsSwitchRow(
                    title = "Sync on WiFi only",
                    subtitle = "Saves mobile cellular bandwidth data usage",
                    checked = data.refreshWifiOnly,
                    onCheckedChange = { scope.launch { settingsManager.updateRefreshWifiOnly(it) } }
                )

                // Parental control PIN section
                SettingsSectionHeader("Security & Parenting")
                SettingsOptionRow(
                    title = "Parental Lock PIN",
                    subtitle = if (data.parentalPin.isEmpty()) "Disabled" else "Active (Tap to update PIN)",
                    onClick = { showPinDialog = true }
                )

                // Export favorites
                SettingsSectionHeader("Data & Export utilities")

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clickable {
                            if (favorites.isEmpty()) {
                                Toast
                                    .makeText(context, "No favorites to export! Tap ♡ on channels first", Toast.LENGTH_SHORT)
                                    .show()
                            } else {
                                val ok = FileUtils.exportToM3u(context, "M4DiTV_favorites.m3u", favorites)
                                if (ok) {
                                    Toast
                                        .makeText(context, "✓ Favorites exported to Downloads folder", Toast.LENGTH_SHORT)
                                        .show()
                                } else {
                                    Toast
                                        .makeText(context, "Export error: Permission issue", Toast.LENGTH_SHORT)
                                        .show()
                                }
                            }
                        },
                    colors = CardDefaults.cardColors(containerColor = SurfaceColor)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = AccentColor)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Export Favorites to File", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                            Text("Generates M3U file in device Downloads folder", color = TextSecondaryColor, fontSize = 11.sp)
                        }
                    }
                }

                // Danger Wipe Button
                Button(
                    onClick = { showClearDataDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Text("Clear All Loaded Channels", color = Color.White, fontWeight = FontWeight.Bold)
                }

                SettingsSectionHeader("About & Developer")

                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceVariantColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("App Name: M4DiTV", fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Version: 1.0.0", color = TextSecondaryColor)
                        Text("Developer: M4DI~UciH4", color = TextSecondaryColor)
                        Text("Total Channels: $totalCount", color = TextSecondaryColor)
                        Text("Total Playlists: ${playlists.size}", color = TextSecondaryColor)

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/RizkyEvory"))
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Developer Profile (GitHub)", color = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))
            }
        }

        // Parental Lock update dialog
        if (showPinDialog) {
            AlertDialog(
                onDismissRequest = { showPinDialog = false },
                title = { Text("Update Security PIN", color = Color.White) },
                text = {
                    Column {
                        Text("Set a 4-digit numeric PIN code to restrict access to adult/xxx content channels.", color = TextSecondaryColor)
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = pinValue,
                            onValueChange = {
                                if (it.length <= 4 && it.all { ch -> ch.isDigit() }) {
                                    pinValue = it
                                }
                            },
                            label = { Text("4-digit PIN", color = TextSecondaryColor) },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentColor,
                                unfocusedBorderColor = DividerColor,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("settings_pin_field")
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                settingsManager.updateParentalPin(pinValue)
                                showPinDialog = false
                                Toast.makeText(context, "Parental Lock Updated!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Confirm")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPinDialog = false }) {
                        Text("Cancel")
                    }
                },
                containerColor = SurfaceColor
            )
        }

        // Confirm Wipe Data Dialog
        if (showClearDataDialog) {
            AlertDialog(
                onDismissRequest = { showClearDataDialog = false },
                title = { Text("Clear All Data?", color = Color.White) },
                text = { Text("This will completely remove all playlists and IPTV streaming channels cached locally. You will have to re-import them.", color = TextSecondaryColor) },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearAllData()
                            showClearDataDialog = false
                            Toast.makeText(context, "Data wiped", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("Clear All")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDataDialog = false }) {
                        Text("Cancel")
                    }
                },
                containerColor = SurfaceColor
            )
        }
    }
}

@Composable
fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        color = Color(0xFFFF6B00),
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        modifier = Modifier.padding(top = 22.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsOptionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                Text(subtitle, color = TextSecondaryColor, fontSize = 11.sp)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondaryColor)
        }
    }
}

@Composable
fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                Text(subtitle, color = TextSecondaryColor, fontSize = 11.sp)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AccentColor
                )
            )
        }
    }
}
