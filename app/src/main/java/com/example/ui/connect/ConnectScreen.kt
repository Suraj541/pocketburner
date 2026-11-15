package com.example.ui.connect

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.TransportType
import com.example.engine.ActiveConnection
import com.example.engine.BleBondStatus
import com.example.engine.CryptoEnvelopeEngine
import com.example.engine.OtaUploadProtocol
import com.example.engine.TransportManager
import com.example.engine.WifiOtaTargetConfig
import com.example.hardware.HardwareRegistry
import com.example.ui.components.ConnectionStatusPill
import com.example.ui.components.EncryptionTogglePill
import com.example.ui.components.HardwareSpecInspectorDialog
import com.example.ui.theme.LocalHardwareStatusColors
import com.example.ui.theme.MonoTextStyles

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConnectScreen(
    viewModel: ConnectViewModel,
    modifier: Modifier = Modifier,
) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val usbDevices by viewModel.usbDevices.collectAsStateWithLifecycle()
    val usbServiceRunning by viewModel.usbServiceRunning.collectAsStateWithLifecycle()
    val usbFlashState by viewModel.usbFlashState.collectAsStateWithLifecycle()
    val wifiOtaServiceRunning by viewModel.wifiOtaServiceRunning.collectAsStateWithLifecycle()
    val wifiOtaState by viewModel.wifiOtaState.collectAsStateWithLifecycle()
    val readyBinaries by viewModel.readyBinaries.collectAsStateWithLifecycle()
    val bleDevices by viewModel.filteredBleDevices.collectAsStateWithLifecycle()
    val isBleScanning by viewModel.isBleScanning.collectAsStateWithLifecycle()
    val bleServiceRunning by viewModel.bleServiceRunning.collectAsStateWithLifecycle()
    val blePairingAddress by viewModel.blePairingAddress.collectAsStateWithLifecycle()
    val bleSearchQuery by viewModel.bleSearchQuery.collectAsStateWithLifecycle()
    val bleBondedOnly by viewModel.bleBondedOnly.collectAsStateWithLifecycle()
    val networkEndpoints by viewModel.networkEndpoints.collectAsStateWithLifecycle()
    val isNsdScanning by viewModel.isNsdScanning.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val savedProfiles by viewModel.savedProfiles.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    val activeBoardSpec = remember(settings.selectedBoardId, connectionState) {
        (connectionState as? ActiveConnection.UsbConnected)?.board
            ?: HardwareRegistry.findById(settings.selectedBoardId)
            ?: HardwareRegistry.boards.first()
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var wifiHost by remember { mutableStateOf("192.168.4.1") }
    var wifiPort by remember { mutableStateOf("8080") }
    var wifiUseTls by remember { mutableStateOf(settings.encryptionEnabled) }
    var showBoardRegistry by remember { mutableStateOf(false) }

    // Wi-Fi HTTP/HTTPS OTA target parameters
    var otaHost by remember { mutableStateOf("192.168.4.1") }
    var otaPort by remember { mutableStateOf("80") }
    var otaUseHttps by remember { mutableStateOf(false) }
    var otaProtocol by remember { mutableStateOf(OtaUploadProtocol.ESP_MULTIPART_UPDATE) }
    var otaEndpointPath by remember { mutableStateOf(OtaUploadProtocol.ESP_MULTIPART_UPDATE.defaultPath) }
    var otaPassword by remember { mutableStateOf("") }
    var selectedFirmwareIdForOta by remember { mutableStateOf<String?>(null) }

    val buildOtaConfig: () -> WifiOtaTargetConfig = {
        WifiOtaTargetConfig(
            useHttps = otaUseHttps,
            host = otaHost,
            port = otaPort.toIntOrNull() ?: if (otaUseHttps) 443 else 80,
            endpointPath = otaEndpointPath,
            protocol = otaProtocol,
            otaPassword = otaPassword,
            allowSelfSignedTls = settings.allowSelfSignedTls,
        )
    }

    var usbFlashOffset by remember(activeBoardSpec.id) { mutableStateOf(activeBoardSpec.appOffset) }
    var usbEraseBeforeFlash by remember { mutableStateOf(false) }
    var selectedFirmwareIdForUsb by remember { mutableStateOf<String?>(null) }

    val usbBinaryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.importAndFlashLocalUri(
                    uri = uri,
                    board = activeBoardSpec,
                    baudRate = settings.defaultBaudRate,
                    flashOffsetHex = usbFlashOffset,
                    eraseBeforeFlash = usbEraseBeforeFlash,
                )
            }
        },
    )

    val wifiOtaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.importAndPushWifiOtaUri(
                    uri = uri,
                    board = activeBoardSpec,
                    config = buildOtaConfig(),
                )
            }
        },
    )

    val blePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { results ->
            val bleGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                results[Manifest.permission.BLUETOOTH_SCAN] == true &&
                    results[Manifest.permission.BLUETOOTH_CONNECT] == true
            } else {
                results[Manifest.permission.ACCESS_FINE_LOCATION] == true
            }
            if (bleGranted) {
                viewModel.refreshBondedBleDevices()
                viewModel.startBleScan()
            }
        }
    )

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Text("Hardware Transports", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "USB OTG • BLE UART • Wi-Fi TCP/TLS",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ConnectionStatusPill(connectionState = connectionState, onClick = { })
                    if (connectionState !is ActiveConnection.Disconnected) {
                        IconButton(onClick = viewModel::disconnect) {
                            Icon(Icons.Outlined.LinkOff, contentDescription = "Disconnect")
                        }
                    }
                }
            }
        }

        statusBanner?.let { msg ->
            item {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(12.dp),
                    ) {
                        Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = viewModel::clearBanner) { Text("OK") }
                    }
                }
            }
        }

        // AES-256-GCM + TLS Encryption Control Card
        item {
            ElevatedCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Security, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Command & Script Encryption", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "AES-256-GCM Payload + TLS Transport",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        EncryptionTogglePill(
                            enabled = settings.encryptionEnabled,
                            onToggle = { viewModel.setEncryptionEnabled(!settings.encryptionEnabled) },
                        )
                    }

                    OutlinedTextField(
                        value = settings.preSharedKey,
                        onValueChange = viewModel::setPreSharedKey,
                        label = { Text("AES-256 Pre-Shared Key (PBKDF2-SHA256)") },
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "Key Fingerprint: ${CryptoEnvelopeEngine.keyFingerprint(settings.preSharedKey)}",
                        style = MonoTextStyles.HexBadge.copy(color = MaterialTheme.colorScheme.primary),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Trust self-signed local lab TLS certs", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = settings.allowSelfSignedTls, onCheckedChange = viewModel::setAllowSelfSignedTls)
                    }
                }
            }
        }

        // Active Offline Target Board Card
        item {
            val activeSpec = HardwareRegistry.findById(settings.selectedBoardId) ?: HardwareRegistry.boards.first()
            ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Active Target Spec: ${activeSpec.displayName}", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${activeSpec.chip} • Offset ${activeSpec.appOffset} • ${activeSpec.protocol.label}",
                            style = MonoTextStyles.HexBadge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        )
                    }
                    OutlinedButton(onClick = { showBoardRegistry = true }) {
                        Icon(Icons.Filled.Memory, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Specs")
                    }
                }
            }
        }

        // Transport Selector Tabs
        item {
            SecondaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("USB OTG") },
                    icon = { Icon(Icons.Filled.Usb, contentDescription = null) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("BLE") },
                    icon = { Icon(Icons.Filled.Bluetooth, contentDescription = null) },
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Wi-Fi / TLS") },
                    icon = { Icon(Icons.Filled.Wifi, contentDescription = null) },
                )
            }
        }

        when (selectedTab) {
            // USB OTG TAB
            0 -> {
                item {
                    ElevatedCard(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("usb_otg_service_card"),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Filled.Usb, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text("USB OTG Serial & Flasher Service", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = when {
                                                usbFlashState.isFlashing -> "Flashing ${usbFlashState.firmwareTitle} @ ${usbFlashState.baudRate} bps"
                                                connectionState is ActiveConnection.UsbConnected -> {
                                                    val usb = connectionState as ActiveConnection.UsbConnected
                                                    "Foreground Active • ${usb.deviceLabel} @ ${usb.baudRate} baud"
                                                }
                                                usbServiceRunning -> "Service Bound • Ready (CP210x, CH340, FTDI, PL2303, CDC)"
                                                else -> "Starts connectedDevice foreground service on connect/flash"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (usbServiceRunning || connectionState is ActiveConnection.UsbConnected) {
                                        TextButton(
                                            onClick = viewModel::stopUsbService,
                                            modifier = Modifier.testTag("usb_stop_service_button"),
                                        ) {
                                            Text("Stop")
                                        }
                                    }
                                    IconButton(onClick = viewModel::refreshUsb, modifier = Modifier.testTag("refresh_usb_button")) {
                                        Icon(Icons.Filled.Refresh, contentDescription = "Scan USB serial devices")
                                    }
                                }
                            }

                            Text("Serial Baud Rate Selection (bps)", style = MaterialTheme.typography.labelMedium)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                TransportManager.SUPPORTED_USB_BAUD_RATES.forEach { baud ->
                                    FilterChip(
                                        selected = settings.defaultBaudRate == baud,
                                        onClick = { viewModel.setDefaultBaudRate(baud) },
                                        label = { Text("$baud") },
                                        modifier = Modifier.testTag("usb_baud_$baud"),
                                    )
                                }
                            }

                            if (connectionState is ActiveConnection.UsbConnected) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = viewModel::pulseUsbBootloader,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("usb_bootloader_pulse_button"),
                                    ) {
                                        Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("DTR/RTS Boot Reset")
                                    }
                                    OutlinedButton(
                                        onClick = viewModel::disconnect,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text("Disconnect USB")
                                    }
                                }
                            }
                        }
                    }
                }

                if (usbDevices.isEmpty()) {
                    item {
                        EmptyTransportCard(
                            title = "No USB-to-Serial Device Detected",
                            subtitle = "Plug an ESP8266/ESP32/S3/C3/C6, M5Stack, Arduino, Raspberry Pi Pico, or CP210x/CH340/FTDI/PL2303 adapter via OTG, then tap Refresh.",
                        )
                    }
                } else {
                    items(usbDevices, key = { it.deviceName }) { dev ->
                        val isUsbActive = (connectionState as? ActiveConnection.UsbConnected)?.deviceLabel == dev.productLabel
                        ElevatedCard(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(dev.productLabel, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "VID:PID %04X:%04X • %s".format(dev.vendorId, dev.productId, dev.matchedBoard.displayName),
                                            style = MonoTextStyles.HexBadge,
                                        )
                                    }
                                    Button(
                                        onClick = { viewModel.connectOrRequestUsb(dev.usbDevice) },
                                        modifier = Modifier.testTag("usb_connect_${dev.vendorId}_${dev.productId}"),
                                    ) {
                                        Text(
                                            when {
                                                isUsbActive -> "Reconnect"
                                                dev.hasPermission -> "Connect"
                                                else -> "Grant USB"
                                            }
                                        )
                                    }
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(6.dp),
                                    ) {
                                        Text(
                                            text = dev.bridgeType.chipsetLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        )
                                    }
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(6.dp),
                                    ) {
                                        Text(
                                            text = dev.matchedBoard.protocol.label,
                                            style = MonoTextStyles.HexBadge,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Direct Binary Firmware Flasher via USB OTG Manager Service
                item {
                    val statusColors = LocalHardwareStatusColors.current
                    val chosenFirmware = remember(readyBinaries, selectedFirmwareIdForUsb) {
                        readyBinaries.firstOrNull { it.id == selectedFirmwareIdForUsb } ?: readyBinaries.firstOrNull()
                    }
                    ElevatedCard(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("usb_binary_flasher_card"),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("USB OTG Binary Firmware Flasher", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "Target: ${activeBoardSpec.displayName} (${activeBoardSpec.protocol.label})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = usbFlashOffset,
                                    onValueChange = { usbFlashOffset = it },
                                    label = { Text("Flash Offset") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Erase Flash", style = MaterialTheme.typography.bodySmall)
                                    Spacer(Modifier.width(6.dp))
                                    Switch(
                                        checked = usbEraseBeforeFlash,
                                        onCheckedChange = { usbEraseBeforeFlash = it },
                                    )
                                }
                            }

                            if (readyBinaries.isNotEmpty()) {
                                Text("Downloaded / Imported Binaries:", style = MaterialTheme.typography.labelMedium)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    readyBinaries.take(6).forEach { fw ->
                                        FilterChip(
                                            selected = chosenFirmware?.id == fw.id,
                                            onClick = {
                                                selectedFirmwareIdForUsb = fw.id
                                                usbFlashOffset = fw.flashOffset.ifBlank { activeBoardSpec.appOffset }
                                            },
                                            label = { Text(fw.title.take(22), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        )
                                    }
                                }
                            }

                            if (usbFlashState.isFlashing || usbFlashState.resultMessage != null) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (usbFlashState.isError) {
                                        MaterialTheme.colorScheme.errorContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text(
                                                text = usbFlashState.resultMessage ?: usbFlashState.statusText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = when {
                                                    usbFlashState.isError -> MaterialTheme.colorScheme.error
                                                    usbFlashState.progress >= 1f -> statusColors.connected
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                },
                                                modifier = Modifier.weight(1f),
                                            )
                                            if (!usbFlashState.isFlashing) {
                                                TextButton(onClick = viewModel::clearUsbFlashState) { Text("Clear") }
                                            }
                                        }
                                        if (usbFlashState.isFlashing) {
                                            LinearProgressIndicator(
                                                progress = { usbFlashState.progress },
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                FilledTonalButton(
                                    onClick = { usbBinaryPickerLauncher.launch(arrayOf("*/*")) },
                                    enabled = !usbFlashState.isFlashing,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("usb_pick_flash_binary_button"),
                                ) {
                                    Icon(Icons.Filled.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Pick .bin & Flash")
                                }
                                Button(
                                    onClick = {
                                        if (chosenFirmware != null) {
                                            viewModel.flashExistingBinaryViaUsb(
                                                firmware = chosenFirmware,
                                                board = activeBoardSpec,
                                                baudRate = settings.defaultBaudRate,
                                                flashOffsetHex = usbFlashOffset,
                                                eraseBeforeFlash = usbEraseBeforeFlash,
                                            )
                                        } else {
                                            usbBinaryPickerLauncher.launch(arrayOf("*/*"))
                                        }
                                    },
                                    enabled = !usbFlashState.isFlashing,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("usb_flash_selected_button"),
                                ) {
                                    Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (chosenFirmware != null) "Flash @ ${settings.defaultBaudRate}" else "Select Binary")
                                }
                            }
                        }
                    }
                }
            }

            // BLE TAB
            1 -> {
                item {
                    ElevatedCard(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = if (isBleScanning) Icons.Filled.BluetoothSearching else Icons.Filled.Bluetooth,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text("BLE Connection Manager Service", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = when {
                                                connectionState is ActiveConnection.BleConnected -> {
                                                    val bleConn = connectionState as ActiveConnection.BleConnected
                                                    "Foreground GATT Active • ${bleConn.deviceName} (MTU ${bleConn.mtu})"
                                                }
                                                isBleScanning -> "Service Active • Scanning BLE peripherals…"
                                                bleServiceRunning -> "Service Bound • Ready for pairing & GATT UART"
                                                else -> "Starts connectedDevice foreground service on scan/connect"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                if (bleServiceRunning || connectionState is ActiveConnection.BleConnected) {
                                    TextButton(
                                        onClick = viewModel::stopBleService,
                                        modifier = Modifier.testTag("ble_stop_service_button"),
                                    ) {
                                        Text("Stop")
                                    }
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Button(
                                    onClick = {
                                        if (isBleScanning) {
                                            viewModel.stopBleScan()
                                        } else if (viewModel.hasBlePermissions()) {
                                            viewModel.startBleScan()
                                        } else {
                                            val perms = buildList {
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                    add(Manifest.permission.BLUETOOTH_SCAN)
                                                    add(Manifest.permission.BLUETOOTH_CONNECT)
                                                } else {
                                                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                                                }
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                    add(Manifest.permission.POST_NOTIFICATIONS)
                                                }
                                            }.toTypedArray()
                                            blePermissionLauncher.launch(perms)
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("ble_scan_button"),
                                ) {
                                    Text(if (isBleScanning) "Stop Scan" else "Scan BLE")
                                }

                                OutlinedButton(
                                    onClick = {
                                        if (viewModel.hasBlePermissions()) {
                                            viewModel.refreshBondedBleDevices()
                                        } else {
                                            val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                                            } else {
                                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                                            }
                                            blePermissionLauncher.launch(perms)
                                        }
                                    },
                                    modifier = Modifier.testTag("ble_refresh_bonded_button"),
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Paired")
                                }
                            }
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = bleSearchQuery,
                            onValueChange = viewModel::setBleSearchQuery,
                            placeholder = { Text("Search BLE name, MAC address, or board hint…") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search BLE devices") },
                            trailingIcon = {
                                if (bleSearchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { viewModel.setBleSearchQuery("") },
                                        modifier = Modifier.testTag("ble_search_clear"),
                                    ) {
                                        Icon(Icons.Filled.Close, contentDescription = "Clear BLE search")
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ble_device_search_input"),
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            FilterChip(
                                selected = !bleBondedOnly,
                                onClick = { viewModel.setBleBondedOnly(false) },
                                label = { Text("All Discovered (${bleDevices.size})") },
                            )
                            FilterChip(
                                selected = bleBondedOnly,
                                onClick = { viewModel.setBleBondedOnly(!bleBondedOnly) },
                                label = { Text("Paired Only") },
                                leadingIcon = {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                modifier = Modifier.testTag("ble_bonded_only_chip"),
                            )
                        }
                    }
                }

                if (bleDevices.isEmpty()) {
                    item {
                        EmptyTransportCard(
                            title = when {
                                isBleScanning -> "Scanning BLE peripherals..."
                                bleSearchQuery.isNotBlank() || bleBondedOnly -> "No matching BLE peripherals"
                                else -> "Ready to Scan & Pair BLE Devices"
                            },
                            subtitle = "Discovers and pairs with ESP32, M5Stack, Nordic UART Service (NUS), Arduino Nano ESP32, and Raspberry Pi BLE GATT boards.",
                        )
                    }
                } else {
                    items(bleDevices, key = { it.address }) { ble ->
                        val isConnected = (connectionState as? ActiveConnection.BleConnected)?.address == ble.address
                        val isPairing = ble.bondStatus == BleBondStatus.PAIRING || blePairingAddress == ble.address
                        ElevatedCard(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(ble.name, style = MaterialTheme.typography.titleSmall)
                                        Text("${ble.address} • ${ble.rssi} dBm", style = MonoTextStyles.HexBadge)
                                    }
                                    AssistChip(
                                        onClick = {},
                                        enabled = false,
                                        label = {
                                            Text(
                                                text = if (isConnected) "Connected" else ble.bondStatus.label,
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        },
                                        colors = AssistChipDefaults.assistChipColors(
                                            disabledContainerColor = when {
                                                isConnected -> MaterialTheme.colorScheme.primaryContainer
                                                ble.bondStatus == BleBondStatus.PAIRED -> MaterialTheme.colorScheme.secondaryContainer
                                                else -> MaterialTheme.colorScheme.surfaceVariant
                                            },
                                            disabledLabelColor = MaterialTheme.colorScheme.onSurface,
                                        ),
                                    )
                                }

                                ble.matchedBoardHint?.let { hint ->
                                    Text(
                                        text = "Hardware Hint: $hint",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    if (ble.bondStatus != BleBondStatus.PAIRED) {
                                        OutlinedButton(
                                            onClick = { viewModel.pairBleDevice(ble, autoConnectAfterPair = false) },
                                            enabled = !isPairing,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("ble_pair_${ble.address}"),
                                        ) {
                                            if (isPairing) {
                                                CircularProgressIndicator(
                                                    strokeWidth = 2.dp,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text("Pairing…")
                                            } else {
                                                Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Pair")
                                            }
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            if (isConnected) {
                                                viewModel.disconnect()
                                            } else {
                                                viewModel.connectBle(ble)
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("ble_connect_${ble.address}"),
                                    ) {
                                        Text(if (isConnected) "Disconnect" else "Connect GATT")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // WI-FI / TLS & HTTP/HTTPS OTA TAB
            2 -> {
                item {
                    ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("TCP / TLS Command Socket", style = MaterialTheme.typography.titleSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = wifiHost,
                                    onValueChange = { wifiHost = it },
                                    label = { Text("Device IP / Hostname") },
                                    singleLine = true,
                                    modifier = Modifier.weight(2f),
                                )
                                OutlinedTextField(
                                    value = wifiPort,
                                    onValueChange = { wifiPort = it },
                                    label = { Text("Port") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(checked = wifiUseTls || settings.encryptionEnabled, onCheckedChange = { wifiUseTls = it })
                                    Spacer(Modifier.width(8.dp))
                                    Text("TLS Socket", style = MaterialTheme.typography.bodyMedium)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(onClick = viewModel::startWifiDiscovery) {
                                        Text(if (isNsdScanning) "Scanning..." else "mDNS Scan")
                                    }
                                    Button(
                                        onClick = {
                                            viewModel.connectWifi(wifiHost, wifiPort.toIntOrNull() ?: 8080, wifiUseTls)
                                        },
                                        modifier = Modifier.testTag("wifi_connect_button"),
                                    ) {
                                        Text("Connect")
                                    }
                                }
                            }
                        }
                    }
                }

                // Wi-Fi HTTP/HTTPS OTA Firmware Flashing Service Card
                item {
                    val statusColors = LocalHardwareStatusColors.current
                    val chosenOtaFirmware = remember(readyBinaries, selectedFirmwareIdForOta) {
                        readyBinaries.firstOrNull { it.id == selectedFirmwareIdForOta } ?: readyBinaries.firstOrNull()
                    }
                    ElevatedCard(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("wifi_ota_flashing_card"),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Filled.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text("Wi-Fi OTA (HTTP/HTTPS) Flasher Service", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = when {
                                                wifiOtaState.isFlashing -> "${wifiOtaState.firmwareTitle} -> ${wifiOtaState.targetUrl}"
                                                wifiOtaServiceRunning -> "Foreground Service Bound • Push .bin over LAN HTTP/HTTPS"
                                                else -> "Starts connectedDevice foreground service for OTA push"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(
                                        checked = otaUseHttps,
                                        onCheckedChange = { https ->
                                            otaUseHttps = https
                                            if (otaPort == "80" && https) otaPort = "443"
                                            else if (otaPort == "443" && !https) otaPort = "80"
                                        },
                                        modifier = Modifier.testTag("wifi_ota_https_switch"),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (otaUseHttps) "HTTPS" else "HTTP", style = MonoTextStyles.HexBadge)
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = otaHost,
                                    onValueChange = { otaHost = it },
                                    label = { Text("OTA Host / IP (e.g. 192.168.4.1)") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(2f)
                                        .testTag("wifi_ota_host_input"),
                                )
                                OutlinedTextField(
                                    value = otaPort,
                                    onValueChange = { otaPort = it },
                                    label = { Text("Port") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("wifi_ota_port_input"),
                                )
                            }

                            Text("OTA Endpoint Protocol", style = MaterialTheme.typography.labelMedium)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                OtaUploadProtocol.entries.forEach { proto ->
                                    FilterChip(
                                        selected = otaProtocol == proto,
                                        onClick = {
                                            otaProtocol = proto
                                            otaEndpointPath = proto.defaultPath
                                        },
                                        label = { Text(proto.label) },
                                        modifier = Modifier.testTag("wifi_ota_proto_${proto.name.lowercase()}"),
                                    )
                                }
                            }
                            Text(
                                text = otaProtocol.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = otaEndpointPath,
                                    onValueChange = { otaEndpointPath = it },
                                    label = { Text("Endpoint Path") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = otaPassword,
                                    onValueChange = { otaPassword = it },
                                    label = { Text("OTA Auth Pass (Optional)") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }

                            if (readyBinaries.isNotEmpty()) {
                                Text("Select Ready Binary for OTA Push:", style = MaterialTheme.typography.labelMedium)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    readyBinaries.take(6).forEach { fw ->
                                        FilterChip(
                                            selected = chosenOtaFirmware?.id == fw.id,
                                            onClick = { selectedFirmwareIdForOta = fw.id },
                                            label = { Text(fw.title.take(22), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        )
                                    }
                                }
                            }

                            if (wifiOtaState.isFlashing || wifiOtaState.resultMessage != null) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (wifiOtaState.isError) {
                                        MaterialTheme.colorScheme.errorContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text(
                                                text = wifiOtaState.resultMessage ?: wifiOtaState.statusText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = when {
                                                    wifiOtaState.isError -> MaterialTheme.colorScheme.error
                                                    wifiOtaState.progress >= 1f -> statusColors.connected
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                },
                                                modifier = Modifier.weight(1f),
                                            )
                                            if (!wifiOtaState.isFlashing) {
                                                TextButton(onClick = viewModel::clearWifiOtaState) { Text("Clear") }
                                            }
                                        }
                                        if (wifiOtaState.isFlashing) {
                                            LinearProgressIndicator(
                                                progress = { wifiOtaState.progress },
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.probeWifiOtaEndpoint(buildOtaConfig()) },
                                    enabled = !wifiOtaState.isFlashing,
                                    modifier = Modifier.testTag("wifi_ota_probe_button"),
                                ) {
                                    Text("Probe")
                                }
                                FilledTonalButton(
                                    onClick = { wifiOtaPickerLauncher.launch(arrayOf("*/*")) },
                                    enabled = !wifiOtaState.isFlashing,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("wifi_ota_pick_binary_button"),
                                ) {
                                    Icon(Icons.Filled.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Pick .bin")
                                }
                                if (wifiOtaState.isFlashing) {
                                    Button(
                                        onClick = viewModel::cancelWifiOta,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("wifi_ota_cancel_button"),
                                    ) {
                                        Text("Cancel OTA")
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            if (chosenOtaFirmware != null) {
                                                viewModel.pushFirmwareViaWifiOta(chosenOtaFirmware, buildOtaConfig())
                                            } else {
                                                wifiOtaPickerLauncher.launch(arrayOf("*/*"))
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("wifi_ota_push_button"),
                                    ) {
                                        Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (chosenOtaFirmware != null) "Push OTA" else "Select .bin")
                                    }
                                }
                            }
                        }
                    }
                }

                items(networkEndpoints, key = { "${it.host}:${it.port}" }) { ep ->
                    ElevatedCard(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(12.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ep.serviceName, style = MaterialTheme.typography.titleSmall)
                                Text("${ep.host}:${ep.port} (${ep.serviceType})", style = MonoTextStyles.HexBadge)
                            }
                            OutlinedButton(
                                onClick = {
                                    otaHost = ep.host
                                    otaPort = ep.port.toString()
                                },
                            ) { Text("Set OTA") }
                            FilledTonalButton(onClick = {
                                wifiHost = ep.host
                                wifiPort = ep.port.toString()
                                otaHost = ep.host
                                viewModel.connectWifi(ep.host, ep.port, wifiUseTls)
                            }) { Text("Use") }
                        }
                    }
                }
            }
        }

        if (savedProfiles.isNotEmpty()) {
            item { Text("Recent Device Profiles", style = MaterialTheme.typography.titleSmall) }
            items(savedProfiles, key = { it.id }) { prof ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(10.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(prof.name, style = MaterialTheme.typography.titleSmall)
                            Text("${prof.transportType} • ${prof.address}", style = MonoTextStyles.HexBadge)
                        }
                        when (prof.transportType) {
                            TransportType.WIFI_TCP_TLS.name -> {
                                TextButton(onClick = { viewModel.connectWifi(prof.address, prof.port, prof.useTls) }) {
                                    Text("Reconnect")
                                }
                            }
                            TransportType.BLE.name -> {
                                TextButton(
                                    onClick = {
                                        viewModel.connectBle(
                                            com.example.engine.DiscoveredBleDevice(
                                                name = prof.name,
                                                address = prof.address,
                                                rssi = -55,
                                            )
                                        )
                                    }
                                ) {
                                    Text("Reconnect")
                                }
                            }
                        }
                        IconButton(onClick = { viewModel.deleteProfile(prof) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete profile")
                        }
                    }
                }
            }
        }
    }

    if (showBoardRegistry) {
        HardwareSpecInspectorDialog(
            initialBoardId = settings.selectedBoardId,
            onDismiss = { showBoardRegistry = false },
            onSelectBoard = { viewModel.selectBoardSpec(it.id) },
        )
    }
}

@Composable
private fun EmptyTransportCard(title: String, subtitle: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
