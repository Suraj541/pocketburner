package com.example.ui.connect

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.hardware.usb.UsbDevice
import android.net.Uri
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.local.ConnectionProfileEntity
import com.example.data.local.DownloadState
import com.example.data.local.FirmwareEntity
import com.example.data.local.SecurityAndTransportSettings
import com.example.data.local.TransportType
import com.example.engine.ActiveConnection
import com.example.engine.BleConnectionService
import com.example.engine.DiscoveredBleDevice
import com.example.engine.DiscoveredNetworkEndpoint
import com.example.engine.DiscoveredUsbDevice
import com.example.engine.FlashOptions
import com.example.engine.TransportManager
import com.example.engine.UsbFlashProgressState
import com.example.engine.UsbOtgManagerService
import com.example.engine.WifiOtaFlashingService
import com.example.engine.WifiOtaProgressState
import com.example.engine.WifiOtaTargetConfig
import com.example.hardware.FirmwareCategory
import com.example.hardware.HardwareBoardSpec
import com.example.hardware.HardwareRegistry
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ConnectViewModel(private val container: AppContainer) : ViewModel() {

    private val appContext: Context = container.appContext
    private val transport = container.transportManager
    private val catalogEngine = container.firmwareCatalogEngine
    private val settingsStore = container.settingsStore
    private val profileDao = container.database.connectionProfileDao()
    private val firmwareDao = container.database.firmwareDao()

    val connectionState: StateFlow<ActiveConnection> = transport.connectionState
    val usbDevices: StateFlow<List<DiscoveredUsbDevice>> = transport.usbDevices
    val usbServiceRunning: StateFlow<Boolean> = transport.usbServiceRunning
    val usbFlashState: StateFlow<UsbFlashProgressState> = transport.usbFlashState

    val wifiOtaServiceRunning: StateFlow<Boolean> = transport.wifiOtaServiceRunning
    val wifiOtaState: StateFlow<WifiOtaProgressState> = transport.wifiOtaState

    val bleDevices: StateFlow<List<DiscoveredBleDevice>> = transport.bleDevices
    val isBleScanning: StateFlow<Boolean> = transport.isBleScanning
    val bleServiceRunning: StateFlow<Boolean> = transport.bleServiceRunning
    val blePairingAddress: StateFlow<String?> = transport.blePairingAddress
    val networkEndpoints: StateFlow<List<DiscoveredNetworkEndpoint>> = transport.networkEndpoints
    val isNsdScanning: StateFlow<Boolean> = transport.isNsdScanning

    val readyBinaries: StateFlow<List<FirmwareEntity>> = firmwareDao.observeAll()
        .map { list ->
            list.filter {
                it.downloadState == DownloadState.DOWNLOADED.name &&
                    !it.localFilePath.isNullOrBlank()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _bleSearchQuery = MutableStateFlow("")
    val bleSearchQuery: StateFlow<String> = _bleSearchQuery.asStateFlow()

    private val _bleBondedOnly = MutableStateFlow(false)
    val bleBondedOnly: StateFlow<Boolean> = _bleBondedOnly.asStateFlow()

    val filteredBleDevices: StateFlow<List<DiscoveredBleDevice>> = combine(
        transport.bleDevices,
        _bleSearchQuery,
        _bleBondedOnly,
    ) { devices, query, bondedOnly ->
        TransportManager.filterBleDevices(devices, query, bondedOnly)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<SecurityAndTransportSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SecurityAndTransportSettings())

    val savedProfiles: StateFlow<List<ConnectionProfileEntity>> = profileDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private var boundBleService: BleConnectionService? = null
    private var isBleServiceBound = false

    private var boundUsbService: UsbOtgManagerService? = null
    private var isUsbServiceBound = false

    private var boundWifiOtaService: WifiOtaFlashingService? = null
    private var isWifiOtaServiceBound = false

    private val bleServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? BleConnectionService.BleConnectionBinder
            boundBleService = binder?.getService()
            isBleServiceBound = boundBleService != null
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            boundBleService = null
            isBleServiceBound = false
        }
    }

    private val usbServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? UsbOtgManagerService.UsbOtgBinder
            boundUsbService = binder?.getService()
            isUsbServiceBound = boundUsbService != null
            boundUsbService?.scanSerialDevices()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            boundUsbService = null
            isUsbServiceBound = false
        }
    }

    private val wifiOtaServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? WifiOtaFlashingService.WifiOtaBinder
            boundWifiOtaService = binder?.getService()
            isWifiOtaServiceBound = boundWifiOtaService != null
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            boundWifiOtaService = null
            isWifiOtaServiceBound = false
        }
    }

    init {
        bindBleManagerService()
        bindUsbManagerService()
        bindWifiOtaManagerService()
    }

    private fun bindBleManagerService() {
        runCatching {
            val intent = Intent(appContext, BleConnectionService::class.java)
            appContext.bindService(intent, bleServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun bindUsbManagerService() {
        runCatching {
            val intent = Intent(appContext, UsbOtgManagerService::class.java)
            appContext.bindService(intent, usbServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun bindWifiOtaManagerService() {
        runCatching {
            val intent = Intent(appContext, WifiOtaFlashingService::class.java)
            appContext.bindService(intent, wifiOtaServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onCleared() {
        if (isBleServiceBound) {
            runCatching { appContext.unbindService(bleServiceConnection) }
            isBleServiceBound = false
            boundBleService = null
        }
        if (isUsbServiceBound) {
            runCatching { appContext.unbindService(usbServiceConnection) }
            isUsbServiceBound = false
            boundUsbService = null
        }
        if (isWifiOtaServiceBound) {
            runCatching { appContext.unbindService(wifiOtaServiceConnection) }
            isWifiOtaServiceBound = false
            boundWifiOtaService = null
        }
        super.onCleared()
    }

    fun hasBlePermissions(): Boolean = transport.hasBlePermissions()

    // ==================== USB OTG MANAGER SERVICE ACTIONS ====================
    fun refreshUsb() {
        boundUsbService?.scanSerialDevices() ?: transport.refreshUsbDevices()
    }

    fun connectOrRequestUsb(device: UsbDevice) {
        val baud = settings.value.defaultBaudRate
        val svc = boundUsbService
        if (svc != null) {
            svc.connectSerial(device, baud)
        } else {
            viewModelScope.launch {
                val hasPerm = transport.usbDevices.value
                    .firstOrNull { it.usbDevice.deviceId == device.deviceId }
                    ?.hasPermission == true
                if (!hasPerm) {
                    transport.requestUsbPermission(device)
                } else {
                    val res = transport.connectUsb(device, baud)
                    _statusBanner.value = res.exceptionOrNull()?.message
                }
            }
        }
    }

    fun pulseUsbBootloader() {
        val svc = boundUsbService
        if (svc != null) {
            svc.pulseBootloaderReset()
        } else {
            viewModelScope.launch {
                val ok = transport.pulseUsbBootloaderReset()
                if (!ok) {
                    _statusBanner.value = "Connect a USB OTG serial device before pulsing DTR/RTS."
                }
            }
        }
    }

    fun flashExistingBinaryViaUsb(
        firmware: FirmwareEntity,
        board: HardwareBoardSpec,
        baudRate: Int,
        flashOffsetHex: String,
        eraseBeforeFlash: Boolean,
    ) {
        val path = firmware.localFilePath
        if (path.isNullOrBlank() || !File(path).exists()) {
            _statusBanner.value = "Local firmware file is missing. Download or import a .bin/.hex/.uf2 first."
            return
        }
        val file = File(path)
        val svc = boundUsbService
        if (svc != null) {
            svc.startBinaryFlash(
                file = file,
                firmwareTitle = firmware.title,
                board = board,
                options = FlashOptions(
                    baudRate = baudRate,
                    flashOffsetHex = flashOffsetHex,
                    flashMode = board.flashMode,
                    eraseBeforeFlash = eraseBeforeFlash,
                ),
            )
        } else {
            UsbOtgManagerService.startServiceAction(
                appContext,
                UsbOtgManagerService.createFlashBinaryIntent(
                    context = appContext,
                    firmwarePath = file.absolutePath,
                    firmwareTitle = firmware.title,
                    boardId = board.id,
                    baudRate = baudRate,
                    offsetHex = flashOffsetHex,
                    flashMode = board.flashMode,
                    eraseBefore = eraseBeforeFlash,
                ),
            )
        }
    }

    fun importAndFlashLocalUri(
        uri: Uri,
        board: HardwareBoardSpec,
        baudRate: Int,
        flashOffsetHex: String,
        eraseBeforeFlash: Boolean,
    ) {
        viewModelScope.launch {
            val res = catalogEngine.addCustomLocalFirmware(
                uri = uri,
                title = "",
                category = FirmwareCategory.UTILITIES,
                targetDeviceId = board.id,
                framework = "USB OTG Binary",
                version = "local",
                flashOffset = flashOffsetHex,
                baudRate = baudRate,
                description = "Direct binary imported in USB OTG Manager for ${board.displayName}.",
            )
            res.fold(
                onSuccess = { imported ->
                    flashExistingBinaryViaUsb(
                        firmware = imported,
                        board = board,
                        baudRate = baudRate,
                        flashOffsetHex = flashOffsetHex,
                        eraseBeforeFlash = eraseBeforeFlash,
                    )
                },
                onFailure = { err ->
                    _statusBanner.value = "Could not import binary: ${err.message}"
                },
            )
        }
    }

    fun clearUsbFlashState() {
        transport.updateUsbFlashState(UsbFlashProgressState())
    }

    fun stopUsbService() {
        boundUsbService?.stopUsbService() ?: run {
            if (connectionState.value is ActiveConnection.UsbConnected) {
                transport.disconnect()
            }
        }
    }

    // ==================== BLE CONNECTION MANAGER SERVICE ACTIONS ====================
    fun setBleSearchQuery(query: String) {
        _bleSearchQuery.value = query
    }

    fun setBleBondedOnly(bondedOnly: Boolean) {
        _bleBondedOnly.value = bondedOnly
    }

    fun refreshBondedBleDevices() {
        boundBleService?.refreshBondedDevices() ?: transport.refreshBondedBleDevices()
    }

    fun startBleScan() {
        BleConnectionService.startServiceAction(
            appContext,
            BleConnectionService.createScanIntent(appContext),
        )
        if (boundBleService == null && !isBleScanning.value) {
            transport.startBleScan()
        }
    }

    fun stopBleScan() {
        boundBleService?.stopScan() ?: transport.stopBleScan()
    }

    fun pairBleDevice(device: DiscoveredBleDevice, autoConnectAfterPair: Boolean = false) {
        val result = boundBleService?.pairDevice(device.address, autoConnectAfterPair)
            ?: transport.pairBleDevice(device.address, autoConnectAfterPair)
        if (result.isFailure) {
            _statusBanner.value = result.exceptionOrNull()?.message ?: "BLE pairing failed."
        }
    }

    fun connectBle(device: DiscoveredBleDevice) {
        BleConnectionService.startServiceAction(
            appContext,
            BleConnectionService.createConnectIntent(appContext, device.address, device.name),
        )
        if (boundBleService == null) {
            transport.connectBle(device.address, device.name)
        }
        saveProfile(
            name = device.name,
            type = TransportType.BLE,
            address = device.address,
            port = 0,
            useTls = false,
        )
    }

    fun stopBleService() {
        boundBleService?.stopManagerService() ?: run {
            transport.stopBleScan()
            if (connectionState.value is ActiveConnection.BleConnected) {
                transport.disconnect()
            }
        }
    }

    // ==================== WI-FI TCP / TLS & HTTP/HTTPS OTA ====================
    fun startWifiDiscovery() = transport.startWifiNsdScan()

    fun probeWifiOtaEndpoint(config: WifiOtaTargetConfig) {
        val svc = boundWifiOtaService
        if (svc != null) {
            svc.probeDeviceEndpoint(config)
        } else {
            _statusBanner.value = "Wi-Fi OTA service initializing, retry in a moment."
        }
    }

    fun pushFirmwareViaWifiOta(
        firmware: FirmwareEntity,
        config: WifiOtaTargetConfig,
    ) {
        val path = firmware.localFilePath
        if (path.isNullOrBlank() || !File(path).exists()) {
            _statusBanner.value = "Download or import a .bin/.uf2 binary first before pushing OTA."
            return
        }
        val file = File(path)
        val svc = boundWifiOtaService
        if (svc != null) {
            svc.startOtaPush(file, firmware.title, config)
        } else {
            WifiOtaFlashingService.startServiceAction(
                appContext,
                WifiOtaFlashingService.createPushIntent(
                    context = appContext,
                    firmwarePath = file.absolutePath,
                    firmwareTitle = firmware.title,
                    config = config,
                ),
            )
        }
    }

    fun importAndPushWifiOtaUri(
        uri: Uri,
        board: HardwareBoardSpec,
        config: WifiOtaTargetConfig,
    ) {
        viewModelScope.launch {
            val res = catalogEngine.addCustomLocalFirmware(
                uri = uri,
                title = "",
                category = FirmwareCategory.UTILITIES,
                targetDeviceId = board.id,
                framework = "Wi-Fi OTA (${config.scheme.uppercase()})",
                version = "ota",
                flashOffset = board.appOffset,
                baudRate = board.defaultBaud,
                description = "Imported for Wi-Fi HTTP/HTTPS OTA push to ${config.host}.",
            )
            res.fold(
                onSuccess = { fw -> pushFirmwareViaWifiOta(fw, config) },
                onFailure = { err -> _statusBanner.value = "OTA binary import failed: ${err.message}" },
            )
        }
    }

    fun cancelWifiOta() {
        boundWifiOtaService?.cancelOtaTransfer() ?: WifiOtaFlashingService.startServiceAction(
            appContext,
            Intent(appContext, WifiOtaFlashingService::class.java).setAction(WifiOtaFlashingService.ACTION_CANCEL_OTA),
        )
    }

    fun clearWifiOtaState() {
        transport.updateWifiOtaState(WifiOtaProgressState())
    }

    fun stopWifiOtaService() {
        boundWifiOtaService?.stopOtaService()
    }

    fun connectWifi(host: String, port: Int, useTls: Boolean) {
        if (host.isBlank()) {
            _statusBanner.value = "Enter a valid IP address or hostname."
            return
        }
        viewModelScope.launch {
            val res = transport.connectWifi(host, port, useTls)
            if (res.isSuccess) {
                saveProfile(
                    name = "$host:$port",
                    type = TransportType.WIFI_TCP_TLS,
                    address = host.trim(),
                    port = port,
                    useTls = useTls || settings.value.encryptionEnabled,
                )
                _statusBanner.value = null
            } else {
                _statusBanner.value = res.exceptionOrNull()?.message
            }
        }
    }

    fun disconnect() {
        transport.disconnect()
    }

    fun setEncryptionEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setEncryptionEnabled(enabled) }
    }

    fun setPreSharedKey(psk: String) {
        viewModelScope.launch { settingsStore.setPreSharedKey(psk) }
    }

    fun setAllowSelfSignedTls(allow: Boolean) {
        viewModelScope.launch { settingsStore.setAllowSelfSignedTls(allow) }
    }

    fun setDefaultBaudRate(baud: Int) {
        val svc = boundUsbService
        if (svc != null && connectionState.value is ActiveConnection.UsbConnected) {
            svc.updateBaudRate(baud)
        } else {
            viewModelScope.launch {
                settingsStore.setDefaultBaudRate(baud)
                if (connectionState.value is ActiveConnection.UsbConnected) {
                    transport.setUsbBaudRate(baud)
                }
            }
        }
    }

    fun selectBoardSpec(boardId: String) {
        viewModelScope.launch { settingsStore.setSelectedBoardId(boardId) }
    }

    fun deleteProfile(profile: ConnectionProfileEntity) {
        viewModelScope.launch { profileDao.deleteById(profile.id) }
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    private fun saveProfile(
        name: String,
        type: TransportType,
        address: String,
        port: Int,
        useTls: Boolean,
    ) {
        viewModelScope.launch {
            profileDao.upsert(
                ConnectionProfileEntity(
                    id = "prof_${type.name}_${address}_$port".take(64),
                    name = name,
                    transportType = type.name,
                    address = address,
                    port = port,
                    useTls = useTls,
                    baudRate = settings.value.defaultBaudRate,
                    targetBoardId = settings.value.selectedBoardId,
                    lastConnectedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ConnectViewModel(container) as T
    }
}
