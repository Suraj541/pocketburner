package com.example.engine

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.data.local.SettingsStore
import com.example.data.local.TransportType
import com.example.hardware.HardwareBoardSpec
import com.example.hardware.HardwareRegistry
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LogDirection { RX, TX, ENC, SYS, ERR }

data class TerminalLogEntry(
    val id: Long,
    val timestampMs: Long,
    val direction: LogDirection,
    val message: String,
    val detail: String? = null,
)

enum class UsbSerialBridgeType(
    val chipsetLabel: String,
    val driverProtocol: String,
) {
    CP210X("Silicon Labs CP210x", "CP210x Vendor Control"),
    CH340_CH9102("WCH CH340 / CH9102", "CH34x Vendor Serial"),
    FTDI("FTDI FT232R / FT2232", "FTDI D2XX/VCP"),
    PL2303("Prolific PL2303", "PL2303 Vendor Serial"),
    ESPRESSIF_NATIVE_USB("Espressif USB-Serial/JTAG", "USB CDC-ACM + JTAG"),
    ARDUINO_CDC("Arduino USB CDC-ACM", "CDC-ACM"),
    RP2040_RP2350("RPi Pico / RP2350 USB", "CDC-ACM / UF2"),
    CDC_ACM("Generic USB CDC-ACM", "Standard USB CDC-ACM"),
}

data class UsbFlashProgressState(
    val isFlashing: Boolean = false,
    val progress: Float = 0f,
    val statusText: String = "",
    val firmwareTitle: String = "",
    val boardId: String = "",
    val baudRate: Int = 115200,
    val resultMessage: String? = null,
    val isError: Boolean = false,
)

data class WifiOtaProgressState(
    val isFlashing: Boolean = false,
    val progress: Float = 0f,
    val bytesSent: Long = 0L,
    val totalBytes: Long = 0L,
    val statusText: String = "",
    val firmwareTitle: String = "",
    val targetUrl: String = "",
    val resultMessage: String? = null,
    val isError: Boolean = false,
)

data class DiscoveredUsbDevice(
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val productLabel: String,
    val matchedBoard: HardwareBoardSpec,
    val hasPermission: Boolean,
    val usbDevice: UsbDevice,
    val bridgeType: UsbSerialBridgeType = UsbSerialBridgeType.CDC_ACM,
    val serialNumber: String? = null,
)

enum class BleBondStatus(val label: String) {
    UNPAIRED("Unpaired"),
    PAIRING("Pairing…"),
    PAIRED("Paired");

    companion object {
        fun fromAndroidBondState(bondState: Int): BleBondStatus = when (bondState) {
            BluetoothDevice.BOND_BONDED -> PAIRED
            BluetoothDevice.BOND_BONDING -> PAIRING
            else -> UNPAIRED
        }
    }
}

data class DiscoveredBleDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val bondStatus: BleBondStatus = BleBondStatus.UNPAIRED,
    val serviceUuids: List<String> = emptyList(),
    val isBondedKnownDevice: Boolean = false,
    val matchedBoardHint: String? = null,
) {
    fun matchesQuery(query: String): Boolean {
        val tokens = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return true
        val haystack = buildString {
            append(name).append(' ')
            append(address).append(' ')
            append(bondStatus.label).append(' ')
            append(matchedBoardHint.orEmpty()).append(' ')
            append(serviceUuids.joinToString(" "))
        }.lowercase()
        return tokens.all { haystack.contains(it) }
    }
}

data class DiscoveredNetworkEndpoint(
    val serviceName: String,
    val host: String,
    val port: Int,
    val serviceType: String,
)

sealed class ActiveConnection {
    data object Disconnected : ActiveConnection()
    data class UsbConnected(
        val deviceLabel: String,
        val board: HardwareBoardSpec,
        val baudRate: Int,
    ) : ActiveConnection()

    data class BleConnected(
        val deviceName: String,
        val address: String,
        val mtu: Int,
    ) : ActiveConnection()

    data class WifiConnected(
        val host: String,
        val port: Int,
        val isTls: Boolean,
    ) : ActiveConnection()
}

enum class BatteryHealthStatus(val label: String) {
    UNKNOWN("Awaiting Telemetry"),
    SAFE("Power Safe"),
    LOW_WARNING("Low Voltage (< 3.70V)"),
    CRITICAL_BROWNOUT("CRITICAL — Brownout Risk"),
}

data class HardwareBatteryTelemetry(
    val voltageVolts: Double? = null,
    val millivolts: Int? = null,
    val percentage: Int? = null,
    val isCharging: Boolean? = null,
    val sourceTransport: String = "",
    val rawLine: String = "",
    val updatedAtMs: Long = 0L,
    val status: BatteryHealthStatus = BatteryHealthStatus.UNKNOWN,
) {
    val hasReading: Boolean get() = voltageVolts != null || percentage != null

    fun formattedVoltage(): String = when {
        voltageVolts != null && millivolts != null ->
            String.format(java.util.Locale.US, "%.2f V (%d mV)", voltageVolts, millivolts)
        voltageVolts != null ->
            String.format(java.util.Locale.US, "%.2f V", voltageVolts)
        millivolts != null -> "$millivolts mV"
        else -> "-- V"
    }
}

class TransportManager(
    private val context: Context,
    private val settingsStore: SettingsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager

    private val _connectionState = MutableStateFlow<ActiveConnection>(ActiveConnection.Disconnected)
    val connectionState: StateFlow<ActiveConnection> = _connectionState.asStateFlow()

    private val _usbDevices = MutableStateFlow<List<DiscoveredUsbDevice>>(emptyList())
    val usbDevices: StateFlow<List<DiscoveredUsbDevice>> = _usbDevices.asStateFlow()

    private val _usbServiceRunning = MutableStateFlow(false)
    val usbServiceRunning: StateFlow<Boolean> = _usbServiceRunning.asStateFlow()

    private val _usbFlashState = MutableStateFlow(UsbFlashProgressState())
    val usbFlashState: StateFlow<UsbFlashProgressState> = _usbFlashState.asStateFlow()

    private val _wifiOtaServiceRunning = MutableStateFlow(false)
    val wifiOtaServiceRunning: StateFlow<Boolean> = _wifiOtaServiceRunning.asStateFlow()

    private val _wifiOtaState = MutableStateFlow(WifiOtaProgressState())
    val wifiOtaState: StateFlow<WifiOtaProgressState> = _wifiOtaState.asStateFlow()

    private val _bleDevices = MutableStateFlow<List<DiscoveredBleDevice>>(emptyList())
    val bleDevices: StateFlow<List<DiscoveredBleDevice>> = _bleDevices.asStateFlow()

    private val _isBleScanning = MutableStateFlow(false)
    val isBleScanning: StateFlow<Boolean> = _isBleScanning.asStateFlow()

    private val _bleServiceRunning = MutableStateFlow(false)
    val bleServiceRunning: StateFlow<Boolean> = _bleServiceRunning.asStateFlow()

    private val _blePairingAddress = MutableStateFlow<String?>(null)
    val blePairingAddress: StateFlow<String?> = _blePairingAddress.asStateFlow()

    private var pendingAutoConnectAfterBondAddress: String? = null
    private var pendingAutoConnectAfterBondName: String? = null

    private val _networkEndpoints = MutableStateFlow<List<DiscoveredNetworkEndpoint>>(emptyList())
    val networkEndpoints: StateFlow<List<DiscoveredNetworkEndpoint>> = _networkEndpoints.asStateFlow()

    private val _isNsdScanning = MutableStateFlow(false)
    val isNsdScanning: StateFlow<Boolean> = _isNsdScanning.asStateFlow()

    private val _logs = MutableStateFlow<List<TerminalLogEntry>>(emptyList())
    val logs: StateFlow<List<TerminalLogEntry>> = _logs.asStateFlow()

    private val _rawIncomingBytes = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val rawIncomingBytes: SharedFlow<ByteArray> = _rawIncomingBytes.asSharedFlow()

    private val _batteryTelemetry = MutableStateFlow(HardwareBatteryTelemetry())
    val batteryTelemetry: StateFlow<HardwareBatteryTelemetry> = _batteryTelemetry.asStateFlow()

    private var logCounter = 0L

    // Active transport handles
    private var usbConnection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var usbEndpointIn: UsbEndpoint? = null
    private var usbEndpointOut: UsbEndpoint? = null
    private var activeUsbDevice: UsbDevice? = null
    private var usbReaderJob: Job? = null

    private var activeSocket: Socket? = null
    private var socketOutput: OutputStream? = null
    private var socketInput: InputStream? = null
    private var socketReaderJob: Job? = null

    private var bluetoothGatt: BluetoothGatt? = null
    private var bleTxChar: BluetoothGattCharacteristic? = null
    private var bleRxChar: BluetoothGattCharacteristic? = null
    private var bleBatteryChar: BluetoothGattCharacteristic? = null
    private var bleMtu = 23

    private val isFlashingMode = AtomicBoolean(false)

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    refreshUsbDevices()
                    if (granted && device != null) {
                        appendLog(LogDirection.SYS, "USB permission granted for ${device.productName ?: device.deviceName}")
                        scope.launch {
                            val baud = settingsStore.settings.first().defaultBaudRate
                            connectUsb(device, baud)
                        }
                    } else {
                        appendLog(LogDirection.ERR, "USB OTG permission denied by user.")
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED, UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    refreshUsbDevices()
                    if (intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                        val detached = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        }
                        if (detached?.deviceId == activeUsbDevice?.deviceId) {
                            disconnect()
                        }
                    }
                }
            }
        }
    }

    private val bleBondReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            } ?: return
            val newState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
            val prevState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE)
            val bondStatus = BleBondStatus.fromAndroidBondState(newState)
            val displayName = if (hasBlePermissions()) {
                device.name ?: "BLE IoT (${device.address.takeLast(5)})"
            } else {
                "BLE IoT (${device.address.takeLast(5)})"
            }

            if (newState != BluetoothDevice.BOND_BONDING && _blePairingAddress.value == device.address) {
                _blePairingAddress.value = null
            }

            val updated = _bleDevices.value.toMutableList()
            val idx = updated.indexOfFirst { it.address == device.address }
            if (idx >= 0) {
                updated[idx] = updated[idx].copy(
                    bondStatus = bondStatus,
                    isBondedKnownDevice = bondStatus == BleBondStatus.PAIRED,
                )
            } else if (bondStatus == BleBondStatus.PAIRED || bondStatus == BleBondStatus.PAIRING) {
                updated.add(
                    DiscoveredBleDevice(
                        name = displayName,
                        address = device.address,
                        rssi = -55,
                        bondStatus = bondStatus,
                        isBondedKnownDevice = bondStatus == BleBondStatus.PAIRED,
                        matchedBoardHint = inferBleBoardHint(displayName, emptyList()),
                    )
                )
            }
            _bleDevices.value = updated.sortedWith(
                compareByDescending<DiscoveredBleDevice> { it.bondStatus == BleBondStatus.PAIRED }
                    .thenByDescending { it.rssi }
            )

            when (newState) {
                BluetoothDevice.BOND_BONDING -> {
                    _blePairingAddress.value = device.address
                    appendLog(LogDirection.SYS, "BLE pairing in progress with $displayName (${device.address})...")
                }
                BluetoothDevice.BOND_BONDED -> {
                    appendLog(LogDirection.SYS, "BLE paired & bonded with $displayName (${device.address}).")
                    if (pendingAutoConnectAfterBondAddress == device.address) {
                        val targetName = pendingAutoConnectAfterBondName ?: displayName
                        pendingAutoConnectAfterBondAddress = null
                        pendingAutoConnectAfterBondName = null
                        connectBle(device.address, targetName)
                    }
                }
                BluetoothDevice.BOND_NONE -> {
                    if (prevState == BluetoothDevice.BOND_BONDING) {
                        appendLog(LogDirection.ERR, "BLE pairing failed or cancelled for $displayName (${device.address}).")
                    }
                    if (pendingAutoConnectAfterBondAddress == device.address) {
                        pendingAutoConnectAfterBondAddress = null
                        pendingAutoConnectAfterBondName = null
                    }
                }
            }
        }
    }

    init {
        val usbFilter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(context, usbReceiver, usbFilter, ContextCompat.RECEIVER_NOT_EXPORTED)

        val btFilter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        ContextCompat.registerReceiver(context, bleBondReceiver, btFilter, ContextCompat.RECEIVER_EXPORTED)

        refreshUsbDevices()
        if (hasBlePermissions()) {
            refreshBondedBleDevices()
        }
        appendLog(LogDirection.SYS, "PocketBurner Transport Engine ready. Hardware Registry loaded (${HardwareRegistry.boards.size} boards).")
    }

    fun appendLog(direction: LogDirection, message: String, detail: String? = null) {
        val entry = TerminalLogEntry(
            id = ++logCounter,
            timestampMs = System.currentTimeMillis(),
            direction = direction,
            message = message,
            detail = detail,
        )
        _logs.value = (_logs.value + entry).takeLast(400)
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun hasBlePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun setUsbServiceRunning(running: Boolean) {
        _usbServiceRunning.value = running
    }

    fun updateUsbFlashState(state: UsbFlashProgressState) {
        _usbFlashState.value = state
    }

    fun setWifiOtaServiceRunning(running: Boolean) {
        _wifiOtaServiceRunning.value = running
    }

    fun updateWifiOtaState(state: WifiOtaProgressState) {
        _wifiOtaState.value = state
    }

    fun refreshUsbDevices() {
        val list = usbManager.deviceList.values
            .filter { dev -> isSerialCapableUsbDevice(dev) }
            .map { dev ->
                val board = HardwareRegistry.identifyByUsb(dev.vendorId, dev.productId, dev.productName)
                val classes = (0 until dev.interfaceCount).map { idx -> dev.getInterface(idx).interfaceClass }
                val bridge = identifyUsbSerialBridge(dev.vendorId, dev.productId, classes)
                val safeSerial = runCatching {
                    if (usbManager.hasPermission(dev)) dev.serialNumber else null
                }.getOrNull()
                DiscoveredUsbDevice(
                    deviceName = dev.deviceName,
                    vendorId = dev.vendorId,
                    productId = dev.productId,
                    productLabel = dev.productName ?: "${bridge.chipsetLabel} (%04X:%04X)".format(dev.vendorId, dev.productId),
                    matchedBoard = board,
                    hasPermission = usbManager.hasPermission(dev),
                    usbDevice = dev,
                    bridgeType = bridge,
                    serialNumber = safeSerial,
                )
            }
        _usbDevices.value = list
    }

    private fun isSerialCapableUsbDevice(dev: UsbDevice): Boolean {
        if (dev.vendorId in setOf(0x10C4, 0x1A86, 0x0403, 0x067B, 0x303A, 0x2341, 0x2A03, 0x2E8A)) {
            return true
        }
        for (i in 0 until dev.interfaceCount) {
            val intf = dev.getInterface(i)
            val isCdc = intf.interfaceClass == UsbConstants.USB_CLASS_COMM ||
                intf.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA
            var hasBulkIn = false
            var hasBulkOut = false
            for (e in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(e)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == UsbConstants.USB_DIR_IN) hasBulkIn = true
                    if (ep.direction == UsbConstants.USB_DIR_OUT) hasBulkOut = true
                }
            }
            if (isCdc || (hasBulkIn && hasBulkOut)) return true
        }
        return false
    }

    fun requestUsbPermission(device: UsbDevice) {
        if (usbManager.hasPermission(device)) {
            scope.launch {
                val baud = settingsStore.settings.first().defaultBaudRate
                connectUsb(device, baud)
            }
            return
        }
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pending = PendingIntent.getBroadcast(context, 0, intent, flags)
        usbManager.requestPermission(device, pending)
    }

    suspend fun connectUsb(device: UsbDevice, baudRate: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            disconnect()
            if (!usbManager.hasPermission(device)) {
                requestUsbPermission(device)
                return@withContext Result.failure(IllegalStateException("Waiting for USB OTG permission"))
            }

            var targetInterface: UsbInterface? = null
            var inEp: UsbEndpoint? = null
            var outEp: UsbEndpoint? = null

            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                var candidateIn: UsbEndpoint? = null
                var candidateOut: UsbEndpoint? = null
                for (e in 0 until intf.endpointCount) {
                    val ep = intf.getEndpoint(e)
                    if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        if (ep.direction == UsbConstants.USB_DIR_IN) candidateIn = ep
                        else if (ep.direction == UsbConstants.USB_DIR_OUT) candidateOut = ep
                    }
                }
                if (candidateIn != null && candidateOut != null) {
                    targetInterface = intf
                    inEp = candidateIn
                    outEp = candidateOut
                    break
                }
            }

            val intf = targetInterface ?: return@withContext Result.failure(
                IllegalStateException("No bulk serial endpoints found on USB device")
            )
            val conn = usbManager.openDevice(device) ?: return@withContext Result.failure(
                IllegalStateException("Unable to open USB OTG connection")
            )
            if (!conn.claimInterface(intf, true)) {
                conn.close()
                return@withContext Result.failure(IllegalStateException("Failed to claim USB interface"))
            }

            usbConnection = conn
            usbInterface = intf
            usbEndpointIn = inEp
            usbEndpointOut = outEp
            activeUsbDevice = device

            configureUsbSerialLine(conn, device.vendorId, baudRate)
            val matched = HardwareRegistry.identifyByUsb(device.vendorId, device.productId, device.productName)
            _connectionState.value = ActiveConnection.UsbConnected(
                deviceLabel = device.productName ?: matched.displayName,
                board = matched,
                baudRate = baudRate,
            )
            appendLog(
                LogDirection.SYS,
                "Connected via USB OTG: ${device.productName ?: matched.displayName} @ $baudRate baud",
                detail = "VID:PID %04X:%04X • %s".format(device.vendorId, device.productId, matched.chip),
            )
            startUsbReaderLoop(conn, inEp!!)
            Result.success(Unit)
        } catch (e: Exception) {
            appendLog(LogDirection.ERR, "USB connect error: ${e.message}")
            Result.failure(e)
        }
    }

    private fun configureUsbSerialLine(conn: UsbDeviceConnection, vendorId: Int, baudRate: Int) {
        when (vendorId) {
            0x10C4 -> { // CP210x
                conn.controlTransfer(0x41, 0x00, 0x0001, 0, null, 0, 500) // IFC_ENABLE
                val baudBytes = byteArrayOf(
                    (baudRate and 0xFF).toByte(),
                    ((baudRate shr 8) and 0xFF).toByte(),
                    ((baudRate shr 16) and 0xFF).toByte(),
                    ((baudRate shr 24) and 0xFF).toByte(),
                )
                conn.controlTransfer(0x41, 0x1E, 0, 0, baudBytes, 4, 500) // SET_BAUDRATE
            }
            0x1A86 -> { // CH340 / CH9102
                conn.controlTransfer(0x40, 0xA1, 0, 0, null, 0, 500) // INIT
                val factor = 1532620800 / baudRate.coerceAtLeast(300)
                val divisor = ((0xFF00 and factor) or 0x03)
                conn.controlTransfer(0x40, 0x9A, 0x1312, divisor, null, 0, 500)
            }
            0x0403 -> { // FTDI
                conn.controlTransfer(0x40, 0x00, 0x0000, 0, null, 0, 500) // Reset
                val divisor = 3_000_000 / baudRate.coerceAtLeast(300)
                conn.controlTransfer(0x40, 0x03, divisor, 0, null, 0, 500)
            }
            0x067B -> { // Prolific PL2303
                val lineCoding = byteArrayOf(
                    (baudRate and 0xFF).toByte(),
                    ((baudRate shr 8) and 0xFF).toByte(),
                    ((baudRate shr 16) and 0xFF).toByte(),
                    ((baudRate shr 24) and 0xFF).toByte(),
                    0x00, 0x00, 0x08,
                )
                conn.controlTransfer(0x21, 0x20, 0, 0, lineCoding, 7, 500)
                conn.controlTransfer(0x21, 0x22, 0x03, 0, null, 0, 500)
            }
            else -> { // Standard USB CDC-ACM (Arduino / ESP32-S3/C3/C6 Native / RP2040)
                conn.controlTransfer(0x21, 0x22, 0x03, 0, null, 0, 500) // SET_CONTROL_LINE_STATE DTR+RTS
                val lineCoding = byteArrayOf(
                    (baudRate and 0xFF).toByte(),
                    ((baudRate shr 8) and 0xFF).toByte(),
                    ((baudRate shr 16) and 0xFF).toByte(),
                    ((baudRate shr 24) and 0xFF).toByte(),
                    0x00, 0x00, 0x08, // 1 stop bit, no parity, 8 data bits
                )
                conn.controlTransfer(0x21, 0x20, 0, 0, lineCoding, 7, 500) // SET_LINE_CODING
            }
        }
    }

    fun setUsbControlLines(dtr: Boolean, rts: Boolean) {
        val conn = usbConnection ?: return
        val dev = activeUsbDevice ?: return
        val value = (if (dtr) 0x01 else 0) or (if (rts) 0x02 else 0)
        when (dev.vendorId) {
            0x10C4 -> { // CP210x
                val ctrl = (if (dtr) 0x0101 else 0x0100) or (if (rts) 0x0202 else 0x0200)
                conn.controlTransfer(0x41, 0x07, ctrl, 0, null, 0, 200)
            }
            0x1A86 -> { // CH340
                val ctrl = ((if (dtr) 0x20 else 0) or (if (rts) 0x40 else 0)).inv() and 0xFF
                conn.controlTransfer(0x40, 0xA4, ctrl, 0, null, 0, 200)
            }
            else -> {
                conn.controlTransfer(0x21, 0x22, value, 0, null, 0, 200)
            }
        }
    }

    suspend fun pulseUsbBootloaderReset(): Boolean = withContext(Dispatchers.IO) {
        if (usbConnection == null || activeUsbDevice == null) return@withContext false
        setUsbControlLines(dtr = false, rts = true)
        kotlinx.coroutines.delay(100)
        setUsbControlLines(dtr = true, rts = false)
        kotlinx.coroutines.delay(60)
        setUsbControlLines(dtr = false, rts = false)
        appendLog(LogDirection.SYS, "Pulsed DTR/RTS hardware bootloader reset sequence.")
        true
    }

    fun setUsbBaudRate(baudRate: Int) {
        val conn = usbConnection ?: return
        val dev = activeUsbDevice ?: return
        configureUsbSerialLine(conn, dev.vendorId, baudRate)
        val cur = _connectionState.value
        if (cur is ActiveConnection.UsbConnected) {
            _connectionState.value = cur.copy(baudRate = baudRate)
        }
        appendLog(LogDirection.SYS, "USB serial baud updated to $baudRate")
    }

    private fun startUsbReaderLoop(conn: UsbDeviceConnection, inEp: UsbEndpoint) {
        usbReaderJob?.cancel()
        usbReaderJob = scope.launch {
            val buf = ByteArray(4096)
            while (usbConnection != null) {
                if (isFlashingMode.get()) {
                    kotlinx.coroutines.delay(50)
                    continue
                }
                val read = conn.bulkTransfer(inEp, buf, buf.size, 250)
                if (read > 0) {
                    val chunk = buf.copyOf(read)
                    _rawIncomingBytes.emit(chunk)
                    val text = chunk.toString(Charsets.UTF_8).trimEnd('\r', '\n')
                    if (text.isNotBlank()) {
                        handleIncomingText(text)
                    }
                }
            }
        }
    }

    // ==================== BLE GATT & PAIRING ====================
    private val bleScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name ?: result.scanRecord?.deviceName ?: "BLE IoT (${device.address.takeLast(5)})"
            val uuids = result.scanRecord?.serviceUuids?.map { it.uuid.toString() }.orEmpty()
            val bondStatus = if (hasBlePermissions()) {
                BleBondStatus.fromAndroidBondState(device.bondState)
            } else {
                BleBondStatus.UNPAIRED
            }
            val entry = DiscoveredBleDevice(
                name = name,
                address = device.address,
                rssi = result.rssi,
                bondStatus = bondStatus,
                serviceUuids = uuids,
                isBondedKnownDevice = bondStatus == BleBondStatus.PAIRED,
                matchedBoardHint = inferBleBoardHint(name, uuids),
            )
            val current = _bleDevices.value.toMutableList()
            val idx = current.indexOfFirst { it.address == entry.address }
            if (idx >= 0) {
                val existing = current[idx]
                current[idx] = entry.copy(
                    serviceUuids = (existing.serviceUuids + entry.serviceUuids).distinct(),
                    matchedBoardHint = entry.matchedBoardHint ?: existing.matchedBoardHint,
                )
            } else {
                current.add(entry)
            }
            _bleDevices.value = current.sortedWith(
                compareByDescending<DiscoveredBleDevice> { it.bondStatus == BleBondStatus.PAIRED }
                    .thenByDescending { it.rssi }
            )
        }

        override fun onScanFailed(errorCode: Int) {
            _isBleScanning.value = false
            appendLog(LogDirection.ERR, "BLE scan failed (code $errorCode)")
        }
    }

    fun setBleServiceRunning(running: Boolean) {
        _bleServiceRunning.value = running
    }

    @SuppressLint("MissingPermission")
    fun refreshBondedBleDevices() {
        if (!hasBlePermissions()) return
        val adapter = btManager?.adapter ?: return
        if (!adapter.isEnabled) return
        val bonded = adapter.bondedDevices.orEmpty()
            .filter { dev ->
                dev.type == BluetoothDevice.DEVICE_TYPE_LE ||
                    dev.type == BluetoothDevice.DEVICE_TYPE_DUAL ||
                    dev.type == BluetoothDevice.DEVICE_TYPE_UNKNOWN
            }
            .map { dev ->
                val name = dev.name ?: "Paired BLE (${dev.address.takeLast(5)})"
                val uuids = dev.uuids?.map { it.uuid.toString() }.orEmpty()
                DiscoveredBleDevice(
                    name = name,
                    address = dev.address,
                    rssi = -50,
                    bondStatus = BleBondStatus.PAIRED,
                    serviceUuids = uuids,
                    isBondedKnownDevice = true,
                    matchedBoardHint = inferBleBoardHint(name, uuids),
                )
            }

        val merged = _bleDevices.value.toMutableList()
        bonded.forEach { bondedDev ->
            val idx = merged.indexOfFirst { it.address == bondedDev.address }
            if (idx >= 0) {
                merged[idx] = merged[idx].copy(
                    bondStatus = BleBondStatus.PAIRED,
                    isBondedKnownDevice = true,
                    matchedBoardHint = merged[idx].matchedBoardHint ?: bondedDev.matchedBoardHint,
                )
            } else {
                merged.add(bondedDev)
            }
        }
        _bleDevices.value = merged.sortedWith(
            compareByDescending<DiscoveredBleDevice> { it.bondStatus == BleBondStatus.PAIRED }
                .thenByDescending { it.rssi }
        )
    }

    @SuppressLint("MissingPermission")
    fun pairBleDevice(address: String, autoConnectAfterPair: Boolean = false): Result<Unit> {
        if (!hasBlePermissions()) {
            appendLog(LogDirection.ERR, "Grant Bluetooth Connect permission before pairing.")
            return Result.failure(SecurityException("Missing Bluetooth permissions"))
        }
        val adapter = btManager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            appendLog(LogDirection.ERR, "Bluetooth adapter is disabled.")
            return Result.failure(IllegalStateException("Bluetooth disabled"))
        }
        val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid BLE address: $address"))

        val devName = device.name ?: "BLE IoT (${address.takeLast(5)})"
        if (device.bondState == BluetoothDevice.BOND_BONDED) {
            appendLog(LogDirection.SYS, "$devName ($address) is already paired.")
            refreshBondedBleDevices()
            if (autoConnectAfterPair) {
                connectBle(address, devName)
            }
            return Result.success(Unit)
        }

        if (autoConnectAfterPair) {
            pendingAutoConnectAfterBondAddress = address
            pendingAutoConnectAfterBondName = devName
        }

        val started = device.createBond()
        return if (started) {
            _blePairingAddress.value = address
            _bleDevices.value = _bleDevices.value.map { item ->
                if (item.address == address) item.copy(bondStatus = BleBondStatus.PAIRING) else item
            }
            appendLog(LogDirection.SYS, "Initiated BLE pairing (createBond) with $devName ($address)...")
            Result.success(Unit)
        } else {
            pendingAutoConnectAfterBondAddress = null
            pendingAutoConnectAfterBondName = null
            appendLog(LogDirection.ERR, "System declined BLE createBond() for $devName ($address).")
            Result.failure(IllegalStateException("createBond() returned false"))
        }
    }

    @SuppressLint("MissingPermission")
    fun startBleScan() {
        if (!hasBlePermissions()) {
            appendLog(LogDirection.ERR, "Grant Bluetooth Scan/Connect permissions first.")
            return
        }
        val adapter: BluetoothAdapter? = btManager?.adapter
        val scanner = adapter?.bluetoothLeScanner
        if (adapter == null || !adapter.isEnabled || scanner == null) {
            appendLog(LogDirection.ERR, "Bluetooth adapter is disabled or unavailable.")
            return
        }
        _bleDevices.value = emptyList()
        refreshBondedBleDevices()
        _isBleScanning.value = true
        scanner.startScan(bleScanCallback)
        appendLog(LogDirection.SYS, "BLE Service scanning for Nordic UART / ESP / Arduino / RPi GATT devices...")
        scope.launch {
            kotlinx.coroutines.delay(12_000)
            stopBleScan()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopBleScan() {
        if (_isBleScanning.value && hasBlePermissions()) {
            btManager?.adapter?.bluetoothLeScanner?.stopScan(bleScanCallback)
        }
        _isBleScanning.value = false
    }

    @SuppressLint("MissingPermission")
    fun connectBle(address: String, displayName: String) {
        if (!hasBlePermissions()) {
            appendLog(LogDirection.ERR, "Missing Bluetooth Connect permission.")
            return
        }
        stopBleScan()
        disconnect()
        val device: BluetoothDevice? = btManager?.adapter?.getRemoteDevice(address)
        if (device == null) {
            appendLog(LogDirection.ERR, "BLE device $address not found.")
            return
        }
        appendLog(LogDirection.SYS, "Connecting BLE GATT to $displayName ($address)...")
        bluetoothGatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt.requestMtu(512)
                    gatt.discoverServices()
                    _connectionState.value = ActiveConnection.BleConnected(displayName, address, bleMtu)
                    appendLog(LogDirection.SYS, "BLE connected to $displayName; discovering UART services...")
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (_connectionState.value is ActiveConnection.BleConnected) {
                        _connectionState.value = ActiveConnection.Disconnected
                    }
                    appendLog(LogDirection.SYS, "BLE disconnected ($displayName)")
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                bleMtu = mtu
                val cur = _connectionState.value
                if (cur is ActiveConnection.BleConnected) {
                    _connectionState.value = cur.copy(mtu = mtu)
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val nusService = gatt.getService(NUS_SERVICE_UUID) ?: gatt.getService(HM10_SERVICE_UUID)
                val targetService = nusService ?: gatt.services.firstOrNull { svc ->
                    svc.characteristics.any { (it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                        (it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0 }
                }
                if (targetService != null) {
                    bleTxChar = targetService.getCharacteristic(NUS_RX_CHAR_UUID)
                        ?: targetService.characteristics.firstOrNull {
                            (it.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0
                        }
                    bleRxChar = targetService.getCharacteristic(NUS_TX_CHAR_UUID)
                        ?: targetService.characteristics.firstOrNull {
                            (it.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0
                        }
                    bleRxChar?.let { rx ->
                        gatt.setCharacteristicNotification(rx, true)
                        rx.getDescriptor(CCCD_UUID)?.let { desc ->
                            val enableVal = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                gatt.writeDescriptor(desc, enableVal)
                            } else {
                                @Suppress("DEPRECATION")
                                desc.value = enableVal
                                @Suppress("DEPRECATION")
                                gatt.writeDescriptor(desc)
                            }
                        }
                    }
                    appendLog(LogDirection.SYS, "BLE GATT UART ready (${targetService.uuid.toString().take(8)}…)")
                } else {
                    appendLog(LogDirection.ERR, "No writable GATT UART characteristic found.")
                }

                // Also inspect standard BLE Battery Service (0x180F / 0x2A19) if exposed by the IoT board
                val basChar = gatt.getService(BATTERY_SERVICE_UUID)?.getCharacteristic(BATTERY_LEVEL_CHAR_UUID)
                bleBatteryChar = basChar
                if (basChar != null && hasBlePermissions()) {
                    if (bleRxChar == null) {
                        gatt.readCharacteristic(basChar)
                    } else {
                        scope.launch {
                            kotlinx.coroutines.delay(250)
                            runCatching { gatt.readCharacteristic(basChar) }
                        }
                    }
                }
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int,
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == BATTERY_LEVEL_CHAR_UUID) {
                    updateBatteryFromBleLevelByte(value)
                }
            }

            @Deprecated("Deprecated on API 33")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == BATTERY_LEVEL_CHAR_UUID) {
                    @Suppress("DEPRECATION")
                    characteristic.value?.let(::updateBatteryFromBleLevelByte)
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                if (characteristic.uuid == BATTERY_LEVEL_CHAR_UUID) {
                    updateBatteryFromBleLevelByte(value)
                    return
                }
                scope.launch {
                    _rawIncomingBytes.emit(value)
                    handleIncomingText(value.toString(Charsets.UTF_8).trimEnd('\r', '\n'))
                }
            }

            @Deprecated("Deprecated on API 33")
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                @Suppress("DEPRECATION")
                val data = characteristic.value ?: return
                if (characteristic.uuid == BATTERY_LEVEL_CHAR_UUID) {
                    updateBatteryFromBleLevelByte(data)
                    return
                }
                scope.launch {
                    _rawIncomingBytes.emit(data)
                    handleIncomingText(data.toString(Charsets.UTF_8).trimEnd('\r', '\n'))
                }
            }
        }, BluetoothDevice.TRANSPORT_LE)
    }

    private fun updateBatteryFromBleLevelByte(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val pct = bytes[0].toInt() and 0xFF
        val current = _batteryTelemetry.value
        val status = classifyBatteryStatus(
            voltageVolts = current.voltageVolts,
            percentage = pct,
            isCharging = current.isCharging,
            isUsbVbus = false,
        )
        _batteryTelemetry.value = current.copy(
            percentage = pct.coerceIn(0, 100),
            sourceTransport = "BLE GATT 0x2A19",
            rawLine = "GATT Battery Level: $pct%",
            updatedAtMs = System.currentTimeMillis(),
            status = status,
        )
    }

    // ==================== WI-FI TCP / TLS & NSD ====================
    private var activeNsdListeners: MutableList<NsdManager.DiscoveryListener> = mutableListOf()

    fun startWifiNsdScan() {
        val mgr = nsdManager ?: return
        stopWifiNsdScan()
        _networkEndpoints.value = emptyList()
        _isNsdScanning.value = true
        val serviceTypes = listOf("_http._tcp.", "_arduino._tcp.", "_esphomelib._tcp.", "_ssh._tcp.")
        serviceTypes.forEach { svcType ->
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) = Unit
                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    @Suppress("DEPRECATION")
                    mgr.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = Unit
                        override fun onServiceResolved(info: NsdServiceInfo) {
                            @Suppress("DEPRECATION")
                            val host = info.host?.hostAddress ?: return
                            val ep = DiscoveredNetworkEndpoint(
                                serviceName = info.serviceName,
                                host = host,
                                port = info.port,
                                serviceType = svcType.trimEnd('.'),
                            )
                            val cur = _networkEndpoints.value
                            if (cur.none { it.host == ep.host && it.port == ep.port }) {
                                _networkEndpoints.value = cur + ep
                            }
                        }
                    })
                }
                override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
                override fun onDiscoveryStopped(serviceType: String) = Unit
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            }
            activeNsdListeners.add(listener)
            runCatching { mgr.discoverServices(svcType, NsdManager.PROTOCOL_DNS_SD, listener) }
        }
        appendLog(LogDirection.SYS, "Scanning LAN via mDNS/NSD for ESP, Arduino & Raspberry Pi devices...")
        scope.launch {
            kotlinx.coroutines.delay(8_000)
            stopWifiNsdScan()
        }
    }

    fun stopWifiNsdScan() {
        val mgr = nsdManager
        activeNsdListeners.forEach { listener ->
            runCatching { mgr?.stopServiceDiscovery(listener) }
        }
        activeNsdListeners.clear()
        _isNsdScanning.value = false
    }

    suspend fun connectWifi(host: String, port: Int, useTls: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            disconnect()
            val settings = settingsStore.settings.first()
            val effectiveTls = useTls || settings.encryptionEnabled
            val socket: Socket = if (effectiveTls) {
                val ssl = CryptoEnvelopeEngine.createTlsSocketFactory(settings.allowSelfSignedTls)
                    .createSocket() as SSLSocket
                ssl.connect(InetSocketAddress(host.trim(), port), 8000)
                ssl.startHandshake()
                ssl
            } else {
                Socket().apply { connect(InetSocketAddress(host.trim(), port), 8000) }
            }
            activeSocket = socket
            socketOutput = socket.getOutputStream()
            socketInput = socket.getInputStream()

            _connectionState.value = ActiveConnection.WifiConnected(host.trim(), port, effectiveTls)
            appendLog(
                LogDirection.SYS,
                "Connected over Wi-Fi (${if (effectiveTls) "TLS 1.2/1.3" else "Plain TCP"}) -> $host:$port"
            )

            socketReaderJob?.cancel()
            socketReaderJob = scope.launch {
                val buf = ByteArray(4096)
                val input = socketInput ?: return@launch
                try {
                    while (activeSocket?.isConnected == true && activeSocket?.isClosed == false) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        val chunk = buf.copyOf(read)
                        _rawIncomingBytes.emit(chunk)
                        val text = chunk.toString(Charsets.UTF_8).trimEnd('\r', '\n')
                        if (text.isNotBlank()) handleIncomingText(text)
                    }
                } catch (_: Exception) {
                } finally {
                    if (_connectionState.value is ActiveConnection.WifiConnected) {
                        _connectionState.value = ActiveConnection.Disconnected
                        appendLog(LogDirection.SYS, "Wi-Fi socket closed.")
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            appendLog(LogDirection.ERR, "Wi-Fi ${if (useTls) "TLS " else ""}connect failed: ${e.message}")
            Result.failure(e)
        }
    }

    // ==================== COMMAND & BINARY TX ====================
    suspend fun sendCommand(command: String, lineEnding: String = "\n"): Result<Unit> = withContext(Dispatchers.IO) {
        val settings = settingsStore.settings.first()
        val rawBytes = (command + lineEnding).toByteArray(Charsets.UTF_8)

        return@withContext if (settings.encryptionEnabled) {
            val env = CryptoEnvelopeEngine.encryptCommand(rawBytes, settings.preSharedKey)
            appendLog(
                LogDirection.ENC,
                "AES-256-GCM TX: $command",
                detail = "IV=${env.ivHex} • Key=${env.keyFingerprint} • Wire=${env.wirePayload.take(44)}…",
            )
            writeWireBytes(env.wirePayload.toByteArray(Charsets.UTF_8))
        } else {
            appendLog(LogDirection.TX, command)
            writeWireBytes(rawBytes)
        }
    }

    suspend fun writeWireBytes(bytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        when (val state = _connectionState.value) {
            is ActiveConnection.Disconnected -> {
                Result.failure(IllegalStateException("No device connected. Connect via USB OTG, BLE, or Wi-Fi first."))
            }
            is ActiveConnection.UsbConnected -> {
                val conn = usbConnection ?: return@withContext Result.failure(IllegalStateException("USB closed"))
                val out = usbEndpointOut ?: return@withContext Result.failure(IllegalStateException("No USB OUT endpoint"))
                var offset = 0
                while (offset < bytes.size) {
                    val len = (bytes.size - offset).coerceAtMost(out.maxPacketSize.coerceAtLeast(64))
                    val slice = bytes.copyOfRange(offset, offset + len)
                    val sent = conn.bulkTransfer(out, slice, slice.size, 1500)
                    if (sent < 0) return@withContext Result.failure(IllegalStateException("USB bulkTransfer failed ($sent)"))
                    offset += sent.coerceAtLeast(1)
                }
                Result.success(Unit)
            }
            is ActiveConnection.WifiConnected -> {
                try {
                    val out = socketOutput ?: return@withContext Result.failure(IllegalStateException("Socket closed"))
                    out.write(bytes)
                    out.flush()
                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            is ActiveConnection.BleConnected -> {
                sendBleChunked(bytes, state.mtu)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun sendBleChunked(bytes: ByteArray, mtu: Int): Result<Unit> {
        val gatt = bluetoothGatt ?: return Result.failure(IllegalStateException("BLE disconnected"))
        val tx = bleTxChar ?: return Result.failure(IllegalStateException("BLE TX characteristic unavailable"))
        val chunkSize = (mtu - 3).coerceIn(20, 244)
        var offset = 0
        while (offset < bytes.size) {
            val end = (offset + chunkSize).coerceAtMost(bytes.size)
            val chunk = bytes.copyOfRange(offset, end)
            val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(tx, chunk, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == android.bluetooth.BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                tx.value = chunk
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(tx)
            }
            if (!ok) return Result.failure(IllegalStateException("BLE GATT write failed at offset $offset"))
            offset = end
            kotlinx.coroutines.delay(15)
        }
        return Result.success(Unit)
    }

    suspend fun readUsbBytes(timeoutMs: Int = 300): ByteArray? = withContext(Dispatchers.IO) {
        val conn = usbConnection ?: return@withContext null
        val inEp = usbEndpointIn ?: return@withContext null
        val buf = ByteArray(1024)
        val count = conn.bulkTransfer(inEp, buf, buf.size, timeoutMs)
        if (count > 0) buf.copyOf(count) else null
    }

    fun setFlashingMode(active: Boolean) {
        isFlashingMode.set(active)
    }

    private suspend fun handleIncomingText(text: String) {
        val settings = settingsStore.settings.first()
        val effectiveText = if (text.startsWith("PBENC1:")) {
            val decrypted = CryptoEnvelopeEngine.decryptEnvelope(text, settings.preSharedKey)
            if (decrypted != null) {
                val plain = decrypted.toString(Charsets.UTF_8).trimEnd()
                appendLog(LogDirection.ENC, "AES-256-GCM RX: $plain")
                plain
            } else {
                appendLog(LogDirection.RX, text)
                text
            }
        } else {
            appendLog(LogDirection.RX, text)
            text
        }
        val transportLabel = currentTransportLabel()
        effectiveText.lineSequence().forEach { line ->
            parseBatteryTelemetryLine(line, transportLabel)?.let { parsed ->
                _batteryTelemetry.value = parsed
            }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun probeHardwareBattery(): Result<String> = withContext(Dispatchers.IO) {
        val conn = _connectionState.value
        if (conn is ActiveConnection.Disconnected) {
            return@withContext Result.failure(IllegalStateException("Connect hardware via USB OTG, BLE, or Wi-Fi before probing voltage."))
        }
        val gatt = bluetoothGatt
        val basChar = bleBatteryChar
        if (conn is ActiveConnection.BleConnected && gatt != null && basChar != null && hasBlePermissions()) {
            val started = runCatching { gatt.readCharacteristic(basChar) }.getOrDefault(false)
            if (started) {
                appendLog(LogDirection.SYS, "Reading BLE Battery Service (0x2A19)...")
                return@withContext Result.success("BLE Battery Service read dispatched.")
            }
        }
        // Dispatch clean voltage query command over active serial/socket/UART link
        val res = sendCommand("AT+CBC")
        return@withContext res.fold(
            onSuccess = { Result.success("Sent hardware battery telemetry probe (AT+CBC).") },
            onFailure = { err -> Result.failure(err) },
        )
    }

    private fun currentTransportLabel(): String = when (val c = _connectionState.value) {
        is ActiveConnection.UsbConnected -> "USB ${c.board.id}"
        is ActiveConnection.BleConnected -> "BLE ${c.deviceName}"
        is ActiveConnection.WifiConnected -> "Wi-Fi ${c.host}"
        is ActiveConnection.Disconnected -> ""
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        usbReaderJob?.cancel()
        usbReaderJob = null
        runCatching { usbInterface?.let { usbConnection?.releaseInterface(it) } }
        runCatching { usbConnection?.close() }
        usbConnection = null
        usbInterface = null
        usbEndpointIn = null
        usbEndpointOut = null
        activeUsbDevice = null

        socketReaderJob?.cancel()
        socketReaderJob = null
        runCatching { activeSocket?.close() }
        activeSocket = null
        socketInput = null
        socketOutput = null

        if (hasBlePermissions()) {
            runCatching { bluetoothGatt?.disconnect() }
            runCatching { bluetoothGatt?.close() }
        }
        bluetoothGatt = null
        bleTxChar = null
        bleRxChar = null
        bleBatteryChar = null

        _batteryTelemetry.value = HardwareBatteryTelemetry()
        _connectionState.value = ActiveConnection.Disconnected
    }

    fun currentTransportType(): TransportType? = when (_connectionState.value) {
        is ActiveConnection.UsbConnected -> TransportType.USB_OTG
        is ActiveConnection.BleConnected -> TransportType.BLE
        is ActiveConnection.WifiConnected -> TransportType.WIFI_TCP_TLS
        is ActiveConnection.Disconnected -> null
    }

    companion object {
        private const val ACTION_USB_PERMISSION = "com.example.POCKETBURNER_USB_PERMISSION"
        private val NUS_SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        private val NUS_RX_CHAR_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        private val NUS_TX_CHAR_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
        private val HM10_SERVICE_UUID = UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB")
        private val BATTERY_SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")
        private val BATTERY_LEVEL_CHAR_UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        const val CRITICAL_VOLTAGE_THRESHOLD = 3.45
        const val LOW_VOLTAGE_THRESHOLD = 3.70

        private val AT_CBC_REGEX = Regex("""\+CBC:\s*(\d+)\s*,\s*(\d{1,3})\s*,\s*(\d{3,5})""")
        private val MV_REGEX = Regex("""(?:bat(?:tery)?(?:_mv)?|vbat(?:_mv)?|volt(?:age)?(?:_mv)?|cell)?\s*[:=,"]?\s*(\d{3,5})\s*mV""", RegexOption.IGNORE_CASE)
        private val MV_KEY_REGEX = Regex("""(?:battery_mv|vbat_mv|voltage_mv)["']?\s*[:=]\s*(\d{3,5})""", RegexOption.IGNORE_CASE)
        private val VOLT_REGEX = Regex("""(?:vbat|bat(?:tery)?|volt(?:age)?|vcell|vbus)?\s*[:=,"]?\s*(\d\.\d{1,3})\s*V\b""", RegexOption.IGNORE_CASE)
        private val VOLT_KEY_REGEX = Regex("""(?:vbat|battery_v|voltage)["']?\s*[:=]\s*(\d\.\d{1,3})""", RegexOption.IGNORE_CASE)
        private val PCT_REGEX = Regex("""(\d{1,3})\s*%""")
        private val PCT_KEY_REGEX = Regex("""(?:pct|percent|battery_pct|soc|level)["']?\s*[:=]\s*(\d{1,3})""", RegexOption.IGNORE_CASE)

        fun classifyBatteryStatus(
            voltageVolts: Double?,
            percentage: Int?,
            isCharging: Boolean? = null,
            isUsbVbus: Boolean = false,
        ): BatteryHealthStatus {
            if (voltageVolts == null && percentage == null) return BatteryHealthStatus.UNKNOWN
            if (voltageVolts != null) {
                if (isUsbVbus && voltageVolts >= 4.5) return BatteryHealthStatus.SAFE
                return when {
                    voltageVolts < CRITICAL_VOLTAGE_THRESHOLD -> BatteryHealthStatus.CRITICAL_BROWNOUT
                    voltageVolts < LOW_VOLTAGE_THRESHOLD -> BatteryHealthStatus.LOW_WARNING
                    else -> {
                        if (percentage != null && percentage < 15 && isCharging != true) {
                            BatteryHealthStatus.CRITICAL_BROWNOUT
                        } else {
                            BatteryHealthStatus.SAFE
                        }
                    }
                }
            }
            val pct = percentage ?: return BatteryHealthStatus.UNKNOWN
            return when {
                pct < 15 && isCharging != true -> BatteryHealthStatus.CRITICAL_BROWNOUT
                pct < 35 -> BatteryHealthStatus.LOW_WARNING
                else -> BatteryHealthStatus.SAFE
            }
        }

        fun parseBatteryTelemetryLine(
            rawLine: String,
            transportLabel: String = "",
            nowMs: Long = System.currentTimeMillis(),
        ): HardwareBatteryTelemetry? {
            val trimmed = rawLine.trim()
            if (trimmed.isBlank()) return null

            val lower = trimmed.lowercase()
            val hasBatteryKeyword = lower.contains("bat") ||
                lower.contains("vbat") ||
                lower.contains("volt") ||
                lower.contains("+cbc") ||
                lower.contains("vbus") ||
                lower.contains("axp") ||
                lower.contains("power") ||
                lower.contains("cell")

            if (!hasBatteryKeyword && !MV_REGEX.containsMatchIn(trimmed) && !VOLT_REGEX.containsMatchIn(trimmed)) {
                return null
            }

            var voltageVolts: Double? = null
            var millivolts: Int? = null
            var percentage: Int? = null
            var cbcCharging: Boolean? = null

            AT_CBC_REGEX.find(trimmed)?.let { m ->
                val bcs = m.groupValues[1].toIntOrNull()
                cbcCharging = when (bcs) {
                    1, 2 -> true
                    0 -> false
                    else -> null
                }
                percentage = m.groupValues[2].toIntOrNull()?.coerceIn(0, 100)
                millivolts = m.groupValues[3].toIntOrNull()
                voltageVolts = millivolts?.let { it / 1000.0 }
            }

            if (millivolts == null) {
                val mvMatch = MV_KEY_REGEX.find(trimmed) ?: MV_REGEX.find(trimmed)
                val mvVal = mvMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
                if (mvVal != null && mvVal in 1500..6500) {
                    millivolts = mvVal
                    voltageVolts = mvVal / 1000.0
                }
            }

            if (voltageVolts == null) {
                val vMatch = VOLT_REGEX.find(trimmed) ?: VOLT_KEY_REGEX.find(trimmed)
                val vVal = vMatch?.groupValues?.getOrNull(1)?.toDoubleOrNull()
                if (vVal != null && vVal in 1.5..6.5) {
                    voltageVolts = vVal
                    millivolts = kotlin.math.round(vVal * 1000.0).toInt()
                }
            }

            if (percentage == null) {
                val pMatch = PCT_KEY_REGEX.find(trimmed) ?: PCT_REGEX.find(trimmed)
                val pVal = pMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
                if (pVal != null && pVal in 0..100) {
                    percentage = pVal
                }
            }

            if (voltageVolts == null && percentage == null) return null

            val isCharging = cbcCharging ?: when {
                lower.contains("discharg") -> false
                lower.contains("charging") || lower.contains("chg=1") || lower.contains("\"charging\":true") -> true
                else -> null
            }
            val isUsbVbus = lower.contains("vbus") || lower.contains("usb_power")

            val status = classifyBatteryStatus(
                voltageVolts = voltageVolts,
                percentage = percentage,
                isCharging = isCharging,
                isUsbVbus = isUsbVbus,
            )

            return HardwareBatteryTelemetry(
                voltageVolts = voltageVolts,
                millivolts = millivolts,
                percentage = percentage,
                isCharging = isCharging,
                sourceTransport = transportLabel,
                rawLine = trimmed.take(120),
                updatedAtMs = nowMs,
                status = status,
            )
        }

        fun filterBleDevices(
            devices: List<DiscoveredBleDevice>,
            query: String,
            bondedOnly: Boolean,
        ): List<DiscoveredBleDevice> {
            return devices
                .asSequence()
                .filter { !bondedOnly || it.bondStatus == BleBondStatus.PAIRED }
                .filter { it.matchesQuery(query) }
                .toList()
        }

        fun inferBleBoardHint(name: String, serviceUuids: List<String>): String? {
            val lower = name.lowercase()
            val hasNus = serviceUuids.any { it.contains("6e400001", ignoreCase = true) }
            val hasHm10 = serviceUuids.any { it.contains("ffe0", ignoreCase = true) }
            return when {
                lower.contains("cardputer") -> "M5 Cardputer / ESP32-S3"
                lower.contains("cores3") -> "M5 CoreS3 / ESP32-S3"
                lower.contains("core2") -> "M5 Core2 / ESP32"
                lower.contains("stickc") -> "M5StickC / ESP32"
                lower.contains("atoms3") -> "M5 AtomS3 / ESP32-S3"
                lower.contains("m5") -> "M5Stack BLE"
                lower.contains("arduino") || lower.contains("nano") -> "Arduino BLE"
                lower.contains("pico") || lower.contains("rp2040") || lower.contains("rp2350") -> "RPi Pico W / Pico 2 W"
                lower.contains("raspberry") || lower.contains("rpi") -> "Raspberry Pi BLE"
                lower.contains("esp32-c6") || lower.contains("c6") -> "ESP32-C6 BLE 5.3"
                lower.contains("esp32-s3") || lower.contains("s3") -> "ESP32-S3 BLE 5.0"
                lower.contains("esp32") || lower.contains("esp") -> "ESP32 BLE"
                hasNus -> "Nordic UART (NUS)"
                hasHm10 -> "HM-10 BLE Serial"
                else -> null
            }
        }

        val SUPPORTED_USB_BAUD_RATES = listOf(9600, 19200, 38400, 57600, 115200, 230400, 460800, 921600, 1500000)

        fun identifyUsbSerialBridge(
            vendorId: Int,
            productId: Int,
            interfaceClasses: List<Int> = emptyList(),
        ): UsbSerialBridgeType = when (vendorId) {
            0x10C4 -> UsbSerialBridgeType.CP210X
            0x1A86 -> UsbSerialBridgeType.CH340_CH9102
            0x0403 -> UsbSerialBridgeType.FTDI
            0x067B -> UsbSerialBridgeType.PL2303
            0x303A -> UsbSerialBridgeType.ESPRESSIF_NATIVE_USB
            0x2341, 0x2A03 -> UsbSerialBridgeType.ARDUINO_CDC
            0x2E8A -> UsbSerialBridgeType.RP2040_RP2350
            else -> {
                if (interfaceClasses.contains(UsbConstants.USB_CLASS_COMM) ||
                    interfaceClasses.contains(UsbConstants.USB_CLASS_CDC_DATA)
                ) {
                    UsbSerialBridgeType.CDC_ACM
                } else {
                    UsbSerialBridgeType.CDC_ACM
                }
            }
        }
    }
}
