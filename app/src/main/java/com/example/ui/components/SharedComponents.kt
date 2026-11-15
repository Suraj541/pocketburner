package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BatteryUnknown
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.DownloadSortOrder
import com.example.data.local.FirmwareEntity
import com.example.engine.ActiveConnection
import com.example.engine.BatteryHealthStatus
import com.example.engine.HardwareBatteryTelemetry
import com.example.hardware.DeviceFamily
import com.example.hardware.HardwareBoardSpec
import com.example.hardware.HardwareRegistry
import com.example.ui.theme.LocalHardwareStatusColors
import com.example.ui.theme.MonoTextStyles

@Composable
fun ConnectionStatusPill(
    connectionState: ActiveConnection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusColors = LocalHardwareStatusColors.current
    val (label, icon, tint) = when (connectionState) {
        is ActiveConnection.Disconnected -> Triple("Offline", Icons.Outlined.LinkOff, MaterialTheme.colorScheme.onSurfaceVariant)
        is ActiveConnection.UsbConnected -> Triple("USB ${connectionState.board.id}", Icons.Filled.Usb, statusColors.connected)
        is ActiveConnection.BleConnected -> Triple("BLE ${connectionState.deviceName}", Icons.Filled.BluetoothConnected, statusColors.connected)
        is ActiveConnection.WifiConnected -> Triple(
            "${if (connectionState.isTls) "TLS" else "TCP"} ${connectionState.host}",
            Icons.Filled.Wifi,
            statusColors.connected,
        )
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (connectionState is ActiveConnection.Disconnected) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        } else {
            statusColors.connected.copy(alpha = 0.14f)
        },
        border = BorderStroke(1.dp, tint.copy(alpha = 0.45f)),
        modifier = modifier.testTag("connection_status_pill"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun EncryptionTogglePill(
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusColors = LocalHardwareStatusColors.current
    val tint = if (enabled) statusColors.encrypted else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(50),
        color = if (enabled) statusColors.encrypted.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, tint.copy(alpha = if (enabled) 0.7f else 0.3f)),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag("encryption_toggle_pill"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Icon(
                imageVector = if (enabled) Icons.Filled.Lock else Icons.Filled.LockOpen,
                contentDescription = if (enabled) "Encryption On" else "Encryption Off",
                tint = tint,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (enabled) "AES-256 + TLS ON" else "Encrypt OFF",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = tint,
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HardwareSpecInspectorDialog(
    initialBoardId: String,
    onDismiss: () -> Unit,
    onSelectBoard: (HardwareBoardSpec) -> Unit,
) {
    var selectedFamily by remember { mutableStateOf<DeviceFamily?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var activeBoard by remember(initialBoardId) {
        mutableStateOf(HardwareRegistry.findById(initialBoardId) ?: HardwareRegistry.boards.first())
    }

    val boardsToShow = remember(selectedFamily, searchQuery) {
        HardwareRegistry.searchBoards(searchQuery, selectedFamily)
    }

    val displayedBoard = remember(boardsToShow, activeBoard) {
        if (boardsToShow.any { it.id == activeBoard.id }) activeBoard
        else boardsToShow.firstOrNull()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 16.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Offline Hardware Registry", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "${boardsToShow.size} / ${HardwareRegistry.boards.size} verified ESP, M5Stack, Arduino & RPi specs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search board, chip (ESP32-C6, RP2350), protocol, or VID:PID…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search hardware registry") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.testTag("hardware_registry_search_clear"),
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hardware_registry_search_input"),
                )

                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = selectedFamily == null,
                        onClick = { selectedFamily = null },
                        label = { Text("All (${HardwareRegistry.boards.size})") },
                    )
                    DeviceFamily.entries.forEach { fam ->
                        FilterChip(
                            selected = selectedFamily == fam,
                            onClick = { selectedFamily = fam },
                            label = { Text(fam.label) },
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                if (boardsToShow.isEmpty() || displayedBoard == null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "No matching hardware profiles",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                "Try searching by board name (Cardputer, NanoC6, Pico 2), SoC (ESP32-S3, ATmega328P), or USB VID:PID (303A:1001, 10C4:EA60).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(
                                onClick = {
                                    searchQuery = ""
                                    selectedFamily = null
                                },
                                modifier = Modifier.testTag("hardware_registry_reset_filters"),
                            ) {
                                Text("Reset Filters")
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            boardsToShow.forEach { board ->
                                FilterChip(
                                    selected = displayedBoard.id == board.id,
                                    onClick = {
                                        activeBoard = board
                                        onSelectBoard(board)
                                    },
                                    label = { Text(board.id, style = MaterialTheme.typography.labelSmall) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    ),
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(14.dp))

                        Text(displayedBoard.displayName, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${displayedBoard.family.label} • ${displayedBoard.seriesGroup}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(10.dp))

                        SpecRow("SoC / MCU", displayedBoard.chip)
                        SpecRow("Architecture", displayedBoard.architecture)
                        SpecRow("Flash / RAM", "${displayedBoard.flashSizeLabel} • ${displayedBoard.ramLabel}")
                        SpecRow("Protocol", displayedBoard.protocol.label)
                        SpecRow("Boot / App Offset", "${displayedBoard.bootloaderOffset} -> ${displayedBoard.appOffset}")
                        SpecRow("Flash Mode & Baud", "${displayedBoard.flashMode} @ ${displayedBoard.defaultBaud} bps")
                        SpecRow("Chip Magic / Logic", "${displayedBoard.chipDetectMagic} • ${displayedBoard.logicVoltage}")
                        if (displayedBoard.usbSignatures.isNotEmpty()) {
                            SpecRow(
                                "USB VID:PID",
                                displayedBoard.usbSignatures.joinToString(", ") {
                                    "%04X:%04X (%s)".format(it.vendorId, it.productId, it.bridgeName)
                                },
                            )
                        }
                        SpecRow("Interfaces", displayedBoard.keyInterfaces.joinToString(" • "))
                        SpecRow("GPIO Pinout", displayedBoard.gpioPinoutSummary)
                        SpecRow("Languages", displayedBoard.supportedLanguages.joinToString(", "))
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = displayedBoard.notes,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp),
        )
        Text(
            text = value,
            style = MonoTextStyles.HexBadge.copy(color = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun DeviceBatteryMonitorCard(
    telemetry: HardwareBatteryTelemetry,
    connectionState: ActiveConnection,
    compact: Boolean = false,
    onProbeBattery: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val statusColors = LocalHardwareStatusColors.current
    val isConnected = connectionState !is ActiveConnection.Disconnected
    val (tint, containerColor, icon) = when {
        telemetry.status == BatteryHealthStatus.CRITICAL_BROWNOUT -> Triple(
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f),
            Icons.Filled.BatteryAlert,
        )
        telemetry.status == BatteryHealthStatus.LOW_WARNING -> Triple(
            Color(0xFFF59E0B),
            Color(0xFFF59E0B).copy(alpha = 0.14f),
            Icons.Filled.Battery3Bar,
        )
        telemetry.status == BatteryHealthStatus.SAFE -> Triple(
            statusColors.connected,
            statusColors.connected.copy(alpha = 0.12f),
            if (telemetry.isCharging == true) Icons.Filled.BatteryChargingFull else Icons.Filled.BatteryFull,
        )
        else -> Triple(
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f),
            Icons.AutoMirrored.Filled.BatteryUnknown,
        )
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(1.dp, tint.copy(alpha = 0.45f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("device_battery_monitor_card"),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = if (compact) 8.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = "Device battery status: ${telemetry.status.label}",
                        tint = tint,
                        modifier = Modifier.size(if (compact) 18.dp else 22.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        val headline = if (telemetry.hasReading) {
                            buildString {
                                append("Target Power: ")
                                append(telemetry.formattedVoltage())
                                telemetry.percentage?.let { append(" • $it%") }
                                if (telemetry.isCharging == true) append(" (CHG)")
                            }
                        } else if (isConnected) {
                            "Target Battery: Awaiting VBAT telemetry"
                        } else {
                            "Target Battery Monitor (Offline)"
                        }
                        Text(
                            text = headline,
                            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                            color = if (telemetry.status == BatteryHealthStatus.CRITICAL_BROWNOUT) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!compact || telemetry.hasReading) {
                            val sub = when {
                                telemetry.hasReading -> "${telemetry.status.label}${if (telemetry.sourceTransport.isNotBlank()) " • ${telemetry.sourceTransport}" else ""}"
                                isConnected -> "Parses VBAT/mV serial/BLE telemetry automatically or tap Probe"
                                else -> "Connect IoT board via USB, BLE, or Wi-Fi to guard against brownout"
                            }
                            Text(
                                text = sub,
                                style = MaterialTheme.typography.labelSmall,
                                color = tint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (isConnected && onProbeBattery != null) {
                    AssistChip(
                        onClick = onProbeBattery,
                        leadingIcon = {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        label = { Text("Probe", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("probe_battery_button"),
                    )
                }
            }

            if (telemetry.percentage != null || telemetry.voltageVolts != null) {
                val fraction = telemetry.percentage?.let { (it / 100f).coerceIn(0f, 1f) }
                    ?: telemetry.voltageVolts?.let { ((it - 3.0) / 1.2).toFloat().coerceIn(0f, 1f) }
                    ?: 0f
                LinearProgressIndicator(
                    progress = { fraction },
                    color = tint,
                    trackColor = tint.copy(alpha = 0.2f),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (telemetry.status == BatteryHealthStatus.CRITICAL_BROWNOUT || telemetry.status == BatteryHealthStatus.LOW_WARNING) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = if (telemetry.status == BatteryHealthStatus.CRITICAL_BROWNOUT) {
                            "Mid-flash failure warning (< 3.45V): Flashing now can corrupt SPI flash or bootloader. Connect USB-C power!"
                        } else {
                            "Low voltage (< 3.70V): Plug in external USB power before erasing or flashing."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (telemetry.status == BatteryHealthStatus.CRITICAL_BROWNOUT) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadSortChipsRow(
    selectedSort: DownloadSortOrder,
    onSelectSort: (DownloadSortOrder) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    LazyRow(
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.testTag("download_sort_row"),
    ) {
        items(DownloadSortOrder.entries) { order ->
            FilterChip(
                selected = selectedSort == order,
                onClick = { onSelectSort(order) },
                leadingIcon = if (order == DownloadSortOrder.MOST_DOWNLOADED) {
                    { Icon(Icons.Filled.SwapVert, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
                label = { Text(order.label, style = MaterialTheme.typography.labelMedium) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                modifier = Modifier.testTag("sort_chip_${order.name.lowercase()}"),
            )
        }
    }
}

@Composable
fun FirmwareNoteEditorDialog(
    firmware: FirmwareEntity,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var noteText by remember(firmware.id, firmware.userNote) { mutableStateOf(firmware.userNote) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Firmware Notes", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = firmware.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Wiring, pinout, credentials, or flash notes") },
                    placeholder = { Text("e.g. Hold BOOT (GPIO0) for CoreS3, SSID=LabNet, offset 0x10000") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("firmware_note_input"),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(noteText.trim())
                    onDismiss()
                },
                modifier = Modifier.testTag("save_firmware_note_button"),
            ) {
                Text("Save Note")
            }
        },
        dismissButton = {
            Row {
                if (noteText.isNotBlank()) {
                    TextButton(onClick = {
                        onSave("")
                        onDismiss()
                    }) {
                        Text("Clear")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
    )
}

