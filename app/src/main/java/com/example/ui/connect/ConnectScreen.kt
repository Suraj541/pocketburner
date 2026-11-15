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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.TransportType
import com.example.engine.ActiveConnection
import com.example.engine.BleBondStatus
import com.example.engine.CryptoEnvelopeEngine
import com.example.hardware.HardwareRegistry
import com.example.ui.components.ConnectionStatusPill
import com.example.ui.components.EncryptionTogglePill
import com.example.ui.components.HardwareSpecInspectorDialog
import com.example.ui.theme.MonoTextStyles

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConnectScreen(
    viewModel: ConnectViewModel,
    modifier: Modifier = Modifier,
) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val usbDevices by viewModel.usbDevices.collectAsStateWithLifecycle()
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

    var selectedTab by remember { mutableIntStateOf(0) }
    var wifiHost by remember { mutableStateOf("192.168.4.1") }
    var wifiPort by remember { mutableStateOf("8080") }
    var wifiUseTls by remember { mutableStateOf(settings.encryptionEnabled) }
    var showBoardRegistry by remember { mutableStateOf(false) }

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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(9600, 57600, 115200, 460800, 921600).forEach { baud ->
                                FilterChip(
                                    selected = settings.defaultBaudRate == baud,
                                    onClick = { viewModel.setDefaultBaudRate(baud) },
                                    label = { Text("$baud") },
                                )
                            }
                        }
                        IconButton(onClick = viewModel::refreshUsb, modifier = Modifier.testTag("refresh_usb_button")) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh USB devices")
                        }
                    }
                }
                if (usbDevices.isEmpty()) {
                    item {
                        EmptyTransportCard(
                            title = "No USB OTG Serial Device Detected",
                            subtitle = "Plug an ESP8266, ESP32/S3/C3/C6, M5Stack, Arduino, or Raspberry Pi Pico via USB-C OTG adapter, then tap Refresh.",
                        )
                    }
                } else {
                    items(usbDevices, key = { it.deviceName }) { dev ->
                        ElevatedCard(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(14.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(dev.productLabel, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "Detected: ${dev.matchedBoard.displayName} (%04X:%04X)".format(dev.vendorId, dev.productId),
                                        style = MonoTextStyles.HexBadge,
                                    )
                                }
                                Button(onClick = { viewModel.connectOrRequestUsb(dev.usbDevice) }) {
                                    Text(if (dev.hasPermission) "Connect" else "Grant USB")
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

            // WI-FI / TLS TAB
            2 -> {
                item {
                    ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                items(networkEndpoints, key = { "${it.host}:${it.port}" }) { ep ->
                    ElevatedCard(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ep.serviceName, style = MaterialTheme.typography.titleSmall)
                                Text("${ep.host}:${ep.port} (${ep.serviceType})", style = MonoTextStyles.HexBadge)
                            }
                            FilledTonalButton(onClick = {
                                wifiHost = ep.host
                                wifiPort = ep.port.toString()
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
