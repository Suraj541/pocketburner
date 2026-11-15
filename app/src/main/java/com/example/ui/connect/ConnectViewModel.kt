package com.example.ui.connect

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.hardware.usb.UsbDevice
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.local.ConnectionProfileEntity
import com.example.data.local.SecurityAndTransportSettings
import com.example.data.local.TransportType
import com.example.engine.ActiveConnection
import com.example.engine.BleConnectionService
import com.example.engine.DiscoveredBleDevice
import com.example.engine.DiscoveredNetworkEndpoint
import com.example.engine.DiscoveredUsbDevice
import com.example.engine.TransportManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ConnectViewModel(private val container: AppContainer) : ViewModel() {

    private val appContext: Context = container.appContext
    private val transport = container.transportManager
    private val settingsStore = container.settingsStore
    private val profileDao = container.database.connectionProfileDao()

    val connectionState: StateFlow<ActiveConnection> = transport.connectionState
    val usbDevices: StateFlow<List<DiscoveredUsbDevice>> = transport.usbDevices
    val bleDevices: StateFlow<List<DiscoveredBleDevice>> = transport.bleDevices
    val isBleScanning: StateFlow<Boolean> = transport.isBleScanning
    val bleServiceRunning: StateFlow<Boolean> = transport.bleServiceRunning
    val blePairingAddress: StateFlow<String?> = transport.blePairingAddress
    val networkEndpoints: StateFlow<List<DiscoveredNetworkEndpoint>> = transport.networkEndpoints
    val isNsdScanning: StateFlow<Boolean> = transport.isNsdScanning

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
    private var isServiceBound = false

    private val bleServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? BleConnectionService.BleConnectionBinder
            boundBleService = binder?.getService()
            isServiceBound = boundBleService != null
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            boundBleService = null
            isServiceBound = false
        }
    }

    init {
        bindBleManagerService()
    }

    private fun bindBleManagerService() {
        runCatching {
            val intent = Intent(appContext, BleConnectionService::class.java)
            appContext.bindService(intent, bleServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onCleared() {
        if (isServiceBound) {
            runCatching { appContext.unbindService(bleServiceConnection) }
            isServiceBound = false
            boundBleService = null
        }
        super.onCleared()
    }

    fun hasBlePermissions(): Boolean = transport.hasBlePermissions()

    fun refreshUsb() {
        transport.refreshUsbDevices()
    }

    fun connectOrRequestUsb(device: UsbDevice) {
        viewModelScope.launch {
            if (!transport.usbDevices.value.firstOrNull { it.usbDevice.deviceId == device.deviceId }?.hasPermission.let { it == true }) {
                transport.requestUsbPermission(device)
            } else {
                val res = transport.connectUsb(device, settings.value.defaultBaudRate)
                _statusBanner.value = res.exceptionOrNull()?.message
            }
        }
    }

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

    fun startWifiDiscovery() = transport.startWifiNsdScan()

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
        viewModelScope.launch {
            settingsStore.setDefaultBaudRate(baud)
            if (connectionState.value is ActiveConnection.UsbConnected) {
                transport.setUsbBaudRate(baud)
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
