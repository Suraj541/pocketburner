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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.engine.ActiveConnection
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
