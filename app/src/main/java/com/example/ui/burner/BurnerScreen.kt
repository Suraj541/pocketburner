package com.example.ui.burner

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.CatalogSection
import com.example.data.local.CategoryEntity
import com.example.data.local.DownloadState
import com.example.data.local.FirmwareEntity
import com.example.data.local.RepoParserType
import com.example.data.local.RepoSourceEntity
import com.example.engine.ActiveConnection
import com.example.engine.FlashOptions
import com.example.engine.IntegrityStatus
import com.example.engine.IntegrityVerificationReport
import com.example.engine.RpiHeadlessConfig
import com.example.hardware.DeviceFamily
import com.example.hardware.FirmwareCategory
import com.example.hardware.FlashProtocol
import com.example.hardware.HardwareBoardSpec
import com.example.hardware.HardwareRegistry
import com.example.ui.components.ConnectionStatusPill
import com.example.ui.components.HardwareSpecInspectorDialog
import com.example.ui.theme.LocalHardwareStatusColors
import com.example.ui.theme.MonoTextStyles

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BurnerScreen(
    viewModel: BurnerViewModel,
    onNavigateConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val firmwares by viewModel.filteredFirmwares.collectAsStateWithLifecycle()
    val repos by viewModel.repos.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val deviceHierarchy by viewModel.deviceHierarchy.collectAsStateWithLifecycle()
    val selectedSection by viewModel.selectedSection.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val selectedSeriesId by viewModel.selectedSeriesId.collectAsStateWithLifecycle()
    val selectedDevice by viewModel.selectedDevice.collectAsStateWithLifecycle()
    val isDevicesExpanded by viewModel.isDevicesExpanded.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val flashSession by viewModel.flashSession.collectAsStateWithLifecycle()
    val integrityReports by viewModel.integrityReports.collectAsStateWithLifecycle()

    var showAddFirmwareDialog by remember { mutableStateOf(false) }
    var showRepoManagerDialog by remember { mutableStateOf(false) }
    var showBoardSpecsForId by remember { mutableStateOf<String?>(null) }
    var firmwareToFlash by remember { mutableStateOf<FirmwareEntity?>(null) }
    var firmwareToVerify by remember { mutableStateOf<FirmwareEntity?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Hardware & OS Download Center Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Hardware & OS Center", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Microcontrollers • Raspberry Pi OS • Verified Releases",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ConnectionStatusPill(connectionState = connectionState, onClick = onNavigateConnect)
            }

            // Hub Section Strip (Microcontrollers / Operating Systems / Firmware Releases / Verification)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .testTag("hub_sections_row"),
            ) {
                items(CatalogSection.entries) { section ->
                    FilterChip(
                        selected = selectedSection == section,
                        onClick = { viewModel.selectSection(section) },
                        leadingIcon = if (section == CatalogSection.VERIFICATION) {
                            { Icon(Icons.Filled.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        label = { Text(section.label, style = MaterialTheme.typography.labelLarge) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }

            // Search + Toolbar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    placeholder = { Text("Search firmware, chip, author...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("burner_search_input"),
                )
                IconButton(
                    onClick = { viewModel.syncAllRepos() },
                    modifier = Modifier.testTag("sync_repos_button"),
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = "Sync online catalogs")
                    }
                }
                IconButton(
                    onClick = { showRepoManagerDialog = true },
                    modifier = Modifier.testTag("repo_manager_button"),
                ) {
                    Icon(Icons.Filled.RssFeed, contentDescription = "Repository feeds")
                }
                IconButton(
                    onClick = {
                        showBoardSpecsForId = if (selectedDevice == "All devices") "CoreS3" else selectedDevice
                    },
                    modifier = Modifier.testTag("hardware_specs_button"),
                ) {
                    Icon(Icons.Filled.Memory, contentDescription = "Offline Hardware Registry")
                }
            }

            syncMessage?.let { msg ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = viewModel::clearSyncMessage, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // 18 Categories Filter Strip (Room-backed offline taxonomy)
            val categoryItems = remember(categories) {
                if (categories.isNotEmpty()) categories.map { FirmwareCategory.fromString(it.name) }
                else FirmwareCategory.entries
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag("categories_filter_row"),
            ) {
                items(categoryItems) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { viewModel.selectCategory(cat) },
                        label = { Text(cat.label) },
                    )
                }
            }

            // Collapsible Hierarchical Devices Filter (Series -> Child Devices from Room)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.DeveloperBoard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    val activeLabel = if (selectedDevice != "All devices") selectedDevice else (selectedSeriesId ?: "All devices")
                    Text(
                        text = "Devices ($activeLabel)",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                TextButton(
                    onClick = viewModel::toggleDevicesExpanded,
                    modifier = Modifier.testTag("collapse_devices_button"),
                ) {
                    Text(if (isDevicesExpanded) "Collapse" else "Expand")
                    Icon(
                        imageVector = if (isDevicesExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                    )
                }
            }

            AnimatedVisibility(visible = isDevicesExpanded) {
                val leafSeries = remember(deviceHierarchy) {
                    deviceHierarchy.filter { it.devices.isNotEmpty() }
                }
                val visibleDevices = remember(leafSeries, selectedSeriesId) {
                    if (selectedSeriesId == null) HardwareRegistry.allDeviceFilterNames
                    else listOf("All devices") + leafSeries.firstOrNull { it.series.id == selectedSeriesId }
                        ?.devices?.map { it.id }.orEmpty()
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                    if (leafSeries.isNotEmpty()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.testTag("device_series_row"),
                        ) {
                            item {
                                FilterChip(
                                    selected = selectedSeriesId == null,
                                    onClick = { viewModel.selectSeries(null) },
                                    label = { Text("All Series", style = MaterialTheme.typography.labelSmall) },
                                )
                            }
                            items(leafSeries, key = { it.series.id }) { group ->
                                FilterChip(
                                    selected = selectedSeriesId == group.series.id,
                                    onClick = { viewModel.selectSeries(group.series.id) },
                                    label = { Text("${group.series.name} (${group.devices.size})", style = MaterialTheme.typography.labelSmall) },
                                )
                            }
                        }
                    }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.testTag("devices_filter_row"),
                    ) {
                        items(visibleDevices) { devName ->
                            FilterChip(
                                selected = selectedDevice == devName,
                                onClick = { viewModel.selectDevice(devName) },
                                label = { Text(devName, style = MaterialTheme.typography.labelMedium) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                ),
                            )
                        }
                    }
                }
            }

            // Firmware & OS Cards Grid
            if (firmwares.isEmpty()) {
                EmptyCatalogView(
                    isSyncing = isSyncing,
                    onSync = viewModel::syncAllRepos,
                    onAddCustom = { showAddFirmwareDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            } else {
                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    val columns = when {
                        maxWidth >= 900.dp -> 3
                        maxWidth >= 560.dp -> 2
                        else -> 1
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(firmwares, key = { it.id }) { item ->
                            FirmwareShowcaseCard(
                                item = item,
                                onDownload = { viewModel.downloadFirmware(item) },
                                onFlash = { firmwareToFlash = item },
                                onDeleteDownload = { viewModel.deleteLocalDownload(item) },
                                onInspectBoard = {
                                    val firstDev = item.deviceTagsCsv.substringBefore(",").trim()
                                    showBoardSpecsForId = firstDev.ifBlank { "CoreS3" }
                                },
                                onInspectVerification = { firmwareToVerify = item },
                            )
                        }
                    }
                }
            }
        }

        // + Add Firmware FAB
        FloatingActionButton(
            onClick = { showAddFirmwareDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("add_custom_firmware_fab"),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Firmware")
                Spacer(Modifier.width(6.dp))
                Text("Add Firmware", style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    if (showAddFirmwareDialog) {
        AddCustomFirmwareDialog(
            onDismiss = { showAddFirmwareDialog = false },
            onAddLocal = { uri, title, cat, dev, fw, ver, offset, baud, desc ->
                viewModel.addCustomLocalFirmware(uri, title, cat, dev, fw, ver, offset, baud, desc)
                showAddFirmwareDialog = false
            },
            onAddUrl = { title, author, cat, dev, fw, ver, url, offset, baud, sha, desc ->
                viewModel.addCustomUrlFirmware(title, author, cat, dev, fw, ver, url, offset, baud, sha, desc)
                showAddFirmwareDialog = false
            },
        )
    }

    if (showRepoManagerDialog) {
        RepoManagerDialog(
            repos = repos,
            onDismiss = { showRepoManagerDialog = false },
            onToggle = viewModel::toggleRepoEnabled,
            onDelete = viewModel::deleteRepo,
            onAddFeed = { name, url, parser ->
                viewModel.addRepoFeed(name, url, parser)
            },
            onSyncAll = viewModel::syncAllRepos,
        )
    }

    showBoardSpecsForId?.let { boardId ->
        HardwareSpecInspectorDialog(
            initialBoardId = boardId,
            onDismiss = { showBoardSpecsForId = null },
            onSelectBoard = { },
        )
    }

    firmwareToFlash?.let { fw ->
        FlashConsoleDialog(
            firmware = fw,
            connectionState = connectionState,
            flashSession = flashSession,
            onDismiss = {
                viewModel.resetFlashSession()
                firmwareToFlash = null
            },
            onNavigateConnect = {
                firmwareToFlash = null
                onNavigateConnect()
            },
            onStartFlash = { board, options -> viewModel.flashFirmware(fw, board, options) },
            onEraseFlash = { board -> viewModel.eraseBoardFlash(board) },
        )
    }

    firmwareToVerify?.let { fw ->
        FirmwareProvenanceDialog(
            firmware = fw,
            integrityReport = fw.localFilePath?.let { integrityReports[it] },
            onVerifyBinary = { viewModel.verifyDownloadedFirmware(fw) },
            onDismiss = { firmwareToVerify = null },
        )
    }
}

@Composable
private fun EmptyCatalogView(
    isSyncing: Boolean,
    onSync: () -> Unit,
    onAddCustom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(24.dp),
    ) {
        Icon(
            Icons.Filled.CloudDownload,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text("No matching firmware loaded", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            "Sync online catalogs (M5Burner, Raspberry Pi OS, Tasmota, MicroPython, Arduino) or add your own custom firmware binary or URL.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = onSync, enabled = !isSyncing) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isSyncing) "Syncing..." else "Sync Feeds")
            }
            Button(onClick = onAddCustom) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add Firmware")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FirmwareShowcaseCard(
    item: FirmwareEntity,
    onDownload: () -> Unit,
    onFlash: () -> Unit,
    onDeleteDownload: () -> Unit,
    onInspectBoard: () -> Unit,
    onInspectVerification: () -> Unit,
) {
    val statusColors = LocalHardwareStatusColors.current
    val dlState = runCatching { DownloadState.valueOf(item.downloadState) }.getOrDefault(DownloadState.NOT_DOWNLOADED)
    val family = runCatching { DeviceFamily.valueOf(item.family) }.getOrDefault(DeviceFamily.ESP_IOT)
    val fallbackDrawableRes = remember(family, item.category, item.title) {
        when {
            item.category.contains("Launcher", ignoreCase = true) ||
                item.title.contains("Launcher", ignoreCase = true) -> R.drawable.img_fw_launcher_ota
            family == DeviceFamily.M5STACK -> R.drawable.img_fw_m5stack
            family == DeviceFamily.ARDUINO -> R.drawable.img_fw_arduino
            family == DeviceFamily.RASPBERRY_PI -> R.drawable.img_fw_rpi_os
            else -> R.drawable.img_fw_esp_iot
        }
    }
    val isOfficial = item.sourceType.equals("official", ignoreCase = true) && !item.isCustomUserAdded
    val publisherLabel = item.vendor.ifBlank { item.author }

    ElevatedCard(
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("firmware_card_${item.id}"),
    ) {
        Column {
            // Bundled Hardware/Firmware Artwork + Optional Remote Cover Image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Image(
                    painter = painterResource(id = fallbackDrawableRes),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (!item.coverImageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.coverImageUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.50f),
                                    Color.Black.copy(alpha = 0.15f),
                                    Color.Black.copy(alpha = 0.72f),
                                )
                            )
                        ),
                )
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.62f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = item.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    Surface(
                        color = if (item.isCustomUserAdded) MaterialTheme.colorScheme.tertiary else Color.Black.copy(alpha = 0.62f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = if (item.isCustomUserAdded) "Custom • ${item.version}" else "v${item.version}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(10.dp),
                ) {
                    Text(
                        text = item.framework,
                        style = MonoTextStyles.HexBadge.copy(color = Color.White.copy(alpha = 0.95f)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Surface(
                        color = if (isOfficial) statusColors.connected else Color(0xFF3A2E14).copy(alpha = 0.90f),
                        shape = RoundedCornerShape(8.dp),
                        onClick = onInspectVerification,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Icon(
                                imageVector = if (isOfficial) Icons.Filled.VerifiedUser else Icons.Filled.Security,
                                contentDescription = "Verify firmware provenance",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = if (isOfficial) "Official" else "Community",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = publisherLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (!item.sha256.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.clickable(onClick = onInspectVerification),
                        ) {
                            Text(
                                text = "SHA-256",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item.deviceTagsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }.take(4).forEach { tag ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(6.dp),
                            onClick = onInspectBoard,
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                val sizeLabel = if (item.sizeBytes > 0) "${(item.sizeBytes / 1024).coerceAtLeast(1)} KB" else "Remote"
                Text(
                    text = "${item.fileExtension.uppercase()} • $sizeLabel • Offset ${item.flashOffset} • ${item.defaultBaud} baud",
                    style = MonoTextStyles.HexBadge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                )

                Spacer(Modifier.height(10.dp))
                when (dlState) {
                    DownloadState.DOWNLOADING -> {
                        LinearProgressIndicator(
                            progress = { (item.downloadProgress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp)),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Downloading ${item.downloadProgress}%...", style = MaterialTheme.typography.labelSmall)
                    }
                    DownloadState.DOWNLOADED -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Button(
                                onClick = onFlash,
                                colors = ButtonDefaults.buttonColors(containerColor = statusColors.flashing),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("flash_button_${item.id}"),
                            ) {
                                Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Burn / Flash")
                            }
                            IconButton(onClick = onDeleteDownload) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove local file")
                            }
                        }
                    }
                    DownloadState.NOT_DOWNLOADED, DownloadState.ERROR -> {
                        Button(
                            onClick = onDownload,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("download_button_${item.id}"),
                        ) {
                            Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (dlState == DownloadState.ERROR) "Retry Download" else "Download")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCustomFirmwareDialog(
    onDismiss: () -> Unit,
    onAddLocal: (Uri, String, FirmwareCategory, String, String, String, String, Int, String) -> Unit,
    onAddUrl: (String, String, FirmwareCategory, String, String, String, String, String, Int, String?, String) -> Unit,
) {
    var tabIndex by remember { mutableIntStateOf(0) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(FirmwareCategory.TOOLS) }
    var selectedDevice by remember { mutableStateOf("CoreS3") }
    var framework by remember { mutableStateOf("Custom Binary") }
    var version by remember { mutableStateOf("1.0.0") }
    var flashOffset by remember { mutableStateOf("0x10000") }
    var baudRateText by remember { mutableStateOf("921600") }
    var sha256 by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            selectedUri = uri
            if (uri != null && title.isBlank()) {
                title = uri.lastPathSegment?.substringAfterLast("/")?.substringBeforeLast(".") ?: "Custom Firmware"
            }
        }
    )

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 16.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Add Custom Firmware", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                }

                SecondaryTabRow(selectedTabIndex = tabIndex) {
                    Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("Local File") })
                    Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("Remote URL") })
                }

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (tabIndex == 0) {
                        OutlinedButton(
                            onClick = { filePicker.launch(arrayOf("*/*")) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pick_local_firmware_button"),
                        ) {
                            Icon(Icons.Filled.FileOpen, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(selectedUri?.lastPathSegment ?: "Select .bin, .hex, .uf2, .img, or .zip")
                        }
                    } else {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text("Firmware Download URL (.bin/.hex/.uf2/.img)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Firmware Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (tabIndex == 1) {
                        OutlinedTextField(
                            value = author,
                            onValueChange = { author = it },
                            label = { Text("Author / Publisher") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Text("Target Hardware Device", style = MaterialTheme.typography.labelLarge)
                    var boardSearchQuery by remember { mutableStateOf("") }
                    val filteredTargetBoards = remember(boardSearchQuery) {
                        HardwareRegistry.searchBoards(boardSearchQuery)
                    }
                    OutlinedTextField(
                        value = boardSearchQuery,
                        onValueChange = { boardSearchQuery = it },
                        placeholder = { Text("Filter target boards (ESP32-C6, Pico 2, CoreS3)…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(filteredTargetBoards) { board ->
                            FilterChip(
                                selected = selectedDevice == board.id,
                                onClick = {
                                    selectedDevice = board.id
                                    flashOffset = board.appOffset
                                    baudRateText = board.defaultBaud.toString()
                                },
                                label = { Text(board.id) },
                            )
                        }
                    }

                    Text("Category", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(FirmwareCategory.entries.filter { it != FirmwareCategory.ALL }) { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat.label) },
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = flashOffset,
                            onValueChange = { flashOffset = it },
                            label = { Text("Offset") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = baudRateText,
                            onValueChange = { baudRateText = it },
                            label = { Text("Baud") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = version,
                            onValueChange = { version = it },
                            label = { Text("Version") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    OutlinedTextField(
                        value = framework,
                        onValueChange = { framework = it },
                        label = { Text("Framework (Arduino, MicroPython, ESP-IDF, RPi OS)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (tabIndex == 1) {
                        OutlinedTextField(
                            value = sha256,
                            onValueChange = { sha256 = it },
                            label = { Text("SHA-256 Checksum (Optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Release Notes / Description") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = if (tabIndex == 0) selectedUri != null else url.isNotBlank() && title.isNotBlank(),
                        onClick = {
                            val baud = baudRateText.toIntOrNull() ?: 921600
                            if (tabIndex == 0) {
                                selectedUri?.let { uri ->
                                    onAddLocal(uri, title, selectedCategory, selectedDevice, framework, version, flashOffset, baud, description)
                                }
                            } else {
                                onAddUrl(title, author, selectedCategory, selectedDevice, framework, version, url, flashOffset, baud, sha256, description)
                            }
                        },
                        modifier = Modifier.testTag("save_custom_firmware_button"),
                    ) {
                        Text(if (tabIndex == 0) "Import Binary" else "Add to Catalog")
                    }
                }
            }
        }
    }
}

@Composable
private fun RepoManagerDialog(
    repos: List<RepoSourceEntity>,
    onDismiss: () -> Unit,
    onToggle: (RepoSourceEntity) -> Unit,
    onDelete: (RepoSourceEntity) -> Unit,
    onAddFeed: (String, String, RepoParserType) -> Unit,
    onSyncAll: () -> Unit,
) {
    var feedName by remember { mutableStateOf("") }
    var feedUrl by remember { mutableStateOf("") }
    var parserType by remember { mutableStateOf(RepoParserType.M5BURNER) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 16.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Online Firmware Feeds", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                }
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    repos.forEach { repo ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(10.dp),
                            ) {
                                Switch(checked = repo.enabled, onCheckedChange = { onToggle(repo) })
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(repo.name, style = MaterialTheme.typography.titleSmall)
                                    Text(repo.url, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${repo.itemCount} items • ${repo.lastError ?: "Ready"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (repo.lastError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    )
                                }
                                IconButton(onClick = { onDelete(repo) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete feed")
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    Text("Add Custom Repository JSON Feed", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = feedName,
                        onValueChange = { feedName = it },
                        label = { Text("Feed Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = feedUrl,
                        onValueChange = { feedUrl = it },
                        label = { Text("Manifest JSON URL (M5Burner / GitHub / RPi / Custom)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(RepoParserType.entries) { pt ->
                            FilterChip(
                                selected = parserType == pt,
                                onClick = { parserType = pt },
                                label = { Text(pt.name) },
                            )
                        }
                    }
                    Button(
                        enabled = feedUrl.isNotBlank(),
                        onClick = {
                            onAddFeed(feedName, feedUrl, parserType)
                            feedName = ""
                            feedUrl = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Add & Sync Repository Feed")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onSyncAll) { Text("Sync All") }
                    TextButton(onClick = onDismiss) { Text("Done") }
                }
            }
        }
    }
}

@Composable
private fun FlashConsoleDialog(
    firmware: FirmwareEntity,
    connectionState: ActiveConnection,
    flashSession: FlashSessionState,
    onDismiss: () -> Unit,
    onNavigateConnect: () -> Unit,
    onStartFlash: (HardwareBoardSpec, FlashOptions) -> Unit,
    onEraseFlash: (HardwareBoardSpec) -> Unit,
) {
    val defaultBoardId = firmware.deviceTagsCsv.substringBefore(",").trim()
    var selectedBoard by remember {
        mutableStateOf(HardwareRegistry.findById(defaultBoardId) ?: HardwareRegistry.boards.first())
    }
    var baudRate by remember { mutableIntStateOf(firmware.defaultBaud) }
    var flashOffset by remember { mutableStateOf(firmware.flashOffset) }
    var flashMode by remember { mutableStateOf(firmware.flashMode) }
    var eraseBeforeFlash by remember { mutableStateOf(false) }

    // RPi headless options
    var rpiHostname by remember { mutableStateOf("pocketpi") }
    var rpiSsh by remember { mutableStateOf(true) }
    var rpiUser by remember { mutableStateOf("pi") }
    var rpiWifiSsid by remember { mutableStateOf("") }
    var rpiWifiPass by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Burn: ${firmware.title}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ConnectionStatusPill(connectionState = connectionState, onClick = onNavigateConnect)

                Text("Target Board Profile", style = MaterialTheme.typography.labelLarge)
                var boardSearchQuery by remember { mutableStateOf("") }
                val filteredFlashBoards = remember(boardSearchQuery) {
                    HardwareRegistry.searchBoards(boardSearchQuery)
                }
                OutlinedTextField(
                    value = boardSearchQuery,
                    onValueChange = { boardSearchQuery = it },
                    placeholder = { Text("Search board, chip, or protocol…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(filteredFlashBoards) { b ->
                        FilterChip(
                            selected = selectedBoard.id == b.id,
                            onClick = {
                                selectedBoard = b
                                baudRate = b.defaultBaud
                                flashOffset = b.appOffset
                                flashMode = b.flashMode
                            },
                            label = { Text(b.id) },
                        )
                    }
                }

                if (selectedBoard.protocol == FlashProtocol.RPI_RAW_IMG) {
                    Text("Raspberry Pi OS Headless Pre-Configuration", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(value = rpiHostname, onValueChange = { rpiHostname = it }, label = { Text("Hostname (.local)") }, singleLine = true)
                    OutlinedTextField(value = rpiUser, onValueChange = { rpiUser = it }, label = { Text("SSH Username") }, singleLine = true)
                    OutlinedTextField(value = rpiWifiSsid, onValueChange = { rpiWifiSsid = it }, label = { Text("Wi-Fi SSID (Optional)") }, singleLine = true)
                    OutlinedTextField(value = rpiWifiPass, onValueChange = { rpiWifiPass = it }, label = { Text("Wi-Fi Password") }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = rpiSsh, onCheckedChange = { rpiSsh = it })
                        Spacer(Modifier.width(8.dp))
                        Text("Enable SSH Service")
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = flashOffset,
                            onValueChange = { flashOffset = it },
                            label = { Text("Offset") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = flashMode,
                            onValueChange = { flashMode = it },
                            label = { Text("Mode") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf(57600, 115200, 460800, 921600)) { b ->
                            FilterChip(selected = baudRate == b, onClick = { baudRate = b }, label = { Text("$b") })
                        }
                    }
                    if (selectedBoard.protocol == FlashProtocol.ESPTOOL_SLIP) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = eraseBeforeFlash, onCheckedChange = { eraseBeforeFlash = it })
                            Spacer(Modifier.width(8.dp))
                            Text("Erase SPI Flash before writing")
                        }
                    }
                }

                val hasSha = !firmware.sha256.isNullOrBlank()
                Surface(
                    color = if (hasSha) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            imageVector = if (hasSha) Icons.Filled.VerifiedUser else Icons.Filled.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = if (hasSha) {
                                "Pre-flash SHA-256 check enabled (${firmware.sha256?.take(12)}…)"
                            } else {
                                "SHA-256 digest will be computed before flashing"
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }

                if (flashSession.isFlashing || flashSession.resultMessage != null) {
                    HorizontalDivider()
                    LinearProgressIndicator(progress = { flashSession.progress }, modifier = Modifier.fillMaxWidth())
                    Text(
                        text = flashSession.resultMessage ?: flashSession.statusText,
                        style = MonoTextStyles.HexBadge,
                        color = if (flashSession.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !flashSession.isFlashing,
                onClick = {
                    val opts = FlashOptions(
                        baudRate = baudRate,
                        flashOffsetHex = flashOffset,
                        flashMode = flashMode,
                        eraseBeforeFlash = eraseBeforeFlash,
                        rpiConfig = if (selectedBoard.protocol == FlashProtocol.RPI_RAW_IMG) {
                            RpiHeadlessConfig(
                                hostname = rpiHostname,
                                enableSsh = rpiSsh,
                                username = rpiUser,
                                wifiSsid = rpiWifiSsid,
                                wifiPassword = rpiWifiPass,
                            )
                        } else null,
                    )
                    onStartFlash(selectedBoard, opts)
                },
                modifier = Modifier.testTag("confirm_burn_button"),
            ) {
                Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (flashSession.isFlashing) "Flashing..." else "Start Burn")
            }
        },
        dismissButton = {
            Row {
                if (selectedBoard.protocol == FlashProtocol.ESPTOOL_SLIP) {
                    TextButton(enabled = !flashSession.isFlashing, onClick = { onEraseFlash(selectedBoard) }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Erase")
                    }
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

@Composable
private fun FirmwareProvenanceDialog(
    firmware: FirmwareEntity,
    integrityReport: IntegrityVerificationReport?,
    onVerifyBinary: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isOfficial = firmware.sourceType.equals("official", ignoreCase = true) && !firmware.isCustomUserAdded
    val isDownloaded = firmware.downloadState == DownloadState.DOWNLOADED.name && !firmware.localFilePath.isNullOrBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = if (isOfficial) Icons.Filled.VerifiedUser else Icons.Filled.Security,
                    contentDescription = null,
                    tint = if (isOfficial) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                )
                Text("Source & Checksum Verification", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(firmware.title, style = MaterialTheme.typography.titleMedium)
                Text("Publisher / Vendor: ${firmware.vendor.ifBlank { firmware.author }}", style = MaterialTheme.typography.bodySmall)
                Text(
                    "Classification: ${if (isOfficial) "Official Vendor Package" else "Community / Third-Party Build (unverified by vendor)"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isOfficial) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                )
                Text("Version: ${firmware.version}${if (firmware.releaseDate.isNotBlank()) " • Released ${firmware.releaseDate}" else ""}", style = MaterialTheme.typography.bodySmall)
                Text("Compatible Boards: ${firmware.deviceTagsCsv}", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text("Expected SHA-256 Checksum:", style = MaterialTheme.typography.labelMedium)
                Text(
                    text = firmware.sha256?.takeIf { it.isNotBlank() } ?: "Not supplied by upstream feed (verify manually before flashing).",
                    style = MonoTextStyles.HexBadge,
                )
                if (integrityReport != null) {
                    val badgeColor = when (integrityReport.status) {
                        IntegrityStatus.VERIFIED -> MaterialTheme.colorScheme.secondaryContainer
                        IntegrityStatus.MISMATCH, IntegrityStatus.MISSING_FILE, IntegrityStatus.ERROR ->
                            MaterialTheme.colorScheme.errorContainer
                        IntegrityStatus.COMPUTED_NO_EXPECTED -> MaterialTheme.colorScheme.tertiaryContainer
                    }
                    Surface(
                        color = badgeColor,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    imageVector = if (integrityReport.status == IntegrityStatus.VERIFIED) Icons.Filled.CheckCircle else Icons.Filled.Security,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(integrityReport.status.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            }
                            Text(integrityReport.summary, style = MaterialTheme.typography.bodySmall)
                            integrityReport.actualSha256?.let { computed ->
                                Text("Computed SHA-256: $computed", style = MonoTextStyles.HexBadge)
                            }
                        }
                    }
                }
                if (isDownloaded) {
                    FilledTonalButton(
                        onClick = onVerifyBinary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("verify_binary_sha256_button"),
                    ) {
                        Icon(Icons.Filled.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Verify Downloaded Binary (SHA-256)")
                    }
                }
                Text("Source URL:", style = MaterialTheme.typography.labelMedium)
                Text(firmware.sourceUrl.ifBlank { firmware.downloadUrl }, style = MonoTextStyles.HexBadge)
                Text("Download URL:", style = MaterialTheme.typography.labelMedium)
                Text(firmware.downloadUrl, style = MonoTextStyles.HexBadge)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
