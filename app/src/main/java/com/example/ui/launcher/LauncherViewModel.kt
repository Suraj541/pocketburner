package com.example.ui.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import android.webkit.JavascriptInterface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.local.DownloadState
import com.example.data.local.FirmwareEntity
import com.example.data.local.SecurityAndTransportSettings
import com.example.engine.ActiveConnection
import com.example.engine.DiscoveredNetworkEndpoint
import com.example.engine.DiscoveredUsbDevice
import com.example.engine.FlashOptions
import com.example.engine.OtaUploadProtocol
import com.example.engine.ScriptLanguage
import com.example.engine.UsbFlashProgressState
import com.example.engine.UsbOtgManagerService
import com.example.engine.WifiOtaFlashingService
import com.example.engine.WifiOtaProgressState
import com.example.engine.WifiOtaTargetConfig
import com.example.hardware.FirmwareCategory
import com.example.hardware.HardwareRegistry
import java.io.File
import java.net.URI
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class LauncherPortalPreset(
    val id: String,
    val label: String,
    val url: String,
    val subtitle: String,
)

data class PendingPickAndFlashRequest(
    val baudRate: Int,
    val offsetHex: String,
)

class LauncherViewModel(private val container: AppContainer) : ViewModel() {

    private val appContext: Context = container.appContext
    private val transport = container.transportManager
    private val catalog = container.firmwareCatalogEngine
    private val scriptRepository = container.scriptRepository
    private val settingsStore = container.settingsStore
    private val firmwareDao = container.database.firmwareDao()

    val portalPresets: List<LauncherPortalPreset> = listOf(
        LauncherPortalPreset(
            id = "esphome_web",
            label = "ESPHome Web",
            url = ESPHOME_WEB_URL,
            subtitle = "Official ESPHome / ESP Web Tools portal for ESP32 & ESP8266 firmware tools",
        ),
        LauncherPortalPreset(
            id = "bmorcelli_web",
            label = "bmorcelli Launcher",
            url = "https://bmorcelli.github.io/Launcher/",
            subtitle = "Official bmorcelli/Launcher Web Flasher & M5Launcher portal",
        ),
        LauncherPortalPreset(
            id = "esp32_ap_wui",
            label = "ESP AP (192.168.4.1)",
            url = "http://192.168.4.1",
            subtitle = "Device hotspot WebUI: join your ESP board's Wi-Fi AP to open its built-in config & OTA page",
        ),
    )

    val connectionState: StateFlow<ActiveConnection> = transport.connectionState
    val usbDevices: StateFlow<List<DiscoveredUsbDevice>> = transport.usbDevices
    val usbFlashState: StateFlow<UsbFlashProgressState> = transport.usbFlashState
    val wifiOtaState: StateFlow<WifiOtaProgressState> = transport.wifiOtaState
    val networkEndpoints: StateFlow<List<DiscoveredNetworkEndpoint>> = transport.networkEndpoints
    val settings: StateFlow<SecurityAndTransportSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SecurityAndTransportSettings())

    val readyBinaries: StateFlow<List<FirmwareEntity>> = firmwareDao.observeAll()
        .map { list ->
            list.filter {
                it.downloadState == DownloadState.DOWNLOADED.name &&
                    !it.localFilePath.isNullOrBlank()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentUrl = MutableStateFlow(ESPHOME_WEB_URL)
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _importedWebFirmware = MutableStateFlow<FirmwareEntity?>(null)
    val importedWebFirmware: StateFlow<FirmwareEntity?> = _importedWebFirmware.asStateFlow()

    private val _pickAndFlashEvents = MutableSharedFlow<PendingPickAndFlashRequest>(extraBufferCapacity = 4)
    val pickAndFlashEvents: SharedFlow<PendingPickAndFlashRequest> = _pickAndFlashEvents.asSharedFlow()

    private val _navigateTabEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val navigateTabEvents: SharedFlow<String> = _navigateTabEvents.asSharedFlow()

    @Volatile
    private var activeWebOriginUrl: String = ESPHOME_WEB_URL

    private var boundUsbService: UsbOtgManagerService? = null
    private var isUsbBound = false

    private var boundWifiOtaService: WifiOtaFlashingService? = null
    private var isWifiOtaBound = false

    private val usbConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            boundUsbService = (service as? UsbOtgManagerService.UsbOtgBinder)?.getService()
            isUsbBound = boundUsbService != null
            boundUsbService?.scanSerialDevices()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            boundUsbService = null
            isUsbBound = false
        }
    }

    private val wifiOtaConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            boundWifiOtaService = (service as? WifiOtaFlashingService.WifiOtaBinder)?.getService()
            isWifiOtaBound = boundWifiOtaService != null
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            boundWifiOtaService = null
            isWifiOtaBound = false
        }
    }

    init {
        runCatching {
            val intent = Intent(appContext, UsbOtgManagerService::class.java)
            appContext.bindService(intent, usbConnection, Context.BIND_AUTO_CREATE)
        }
        runCatching {
            val otaIntent = Intent(appContext, WifiOtaFlashingService::class.java)
            appContext.bindService(otaIntent, wifiOtaConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onCleared() {
        if (isUsbBound) {
            runCatching { appContext.unbindService(usbConnection) }
            isUsbBound = false
            boundUsbService = null
        }
        if (isWifiOtaBound) {
            runCatching { appContext.unbindService(wifiOtaConnection) }
            isWifiOtaBound = false
            boundWifiOtaService = null
        }
        super.onCleared()
    }

    fun selectPortalUrl(url: String) {
        val trimmed = url.trim()
        val normalized = when {
            trimmed.startsWith("file://") || trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            else -> "http://$trimmed"
        }
        _currentUrl.value = normalized
    }

    fun updateActiveWebUrl(url: String?) {
        if (!url.isNullOrBlank()) {
            activeWebOriginUrl = url
            _currentUrl.value = url
        }
    }

    fun scanWifiEndpoints() {
        transport.startWifiNsdScan()
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun dismissImportedFirmwareBanner() {
        _importedWebFirmware.value = null
    }

    fun importAndDownloadBinaryFromWeb(downloadUrl: String, suggestedFilename: String?) {
        viewModelScope.launch {
            _statusMessage.value = "Downloading ${suggestedFilename ?: "firmware binary"} into local vault…"
            val boardId = settings.value.selectedBoardId
            val res = catalog.importAndDownloadWebBinary(downloadUrl, suggestedFilename, boardId)
            res.fold(
                onSuccess = { fw ->
                    _importedWebFirmware.value = fw
                    _statusMessage.value = "Downloaded '${fw.title}' (${fw.sizeBytes / 1024} KB). Ready to flash over USB OTG."
                },
                onFailure = { err ->
                    _statusMessage.value = "Web binary download failed: ${err.message}"
                },
            )
        }
    }

    fun importAndFlashPickedUri(uri: Uri, baudRate: Int, offsetHex: String) {
        val board = (connectionState.value as? ActiveConnection.UsbConnected)?.board
            ?: HardwareRegistry.findById(settings.value.selectedBoardId)
            ?: HardwareRegistry.boards.first()
        viewModelScope.launch {
            val res = catalog.addCustomLocalFirmware(
                uri = uri,
                title = "",
                category = FirmwareCategory.UI_LAUNCHER,
                targetDeviceId = board.id,
                framework = "M5Launcher / Local Binary",
                version = "local",
                flashOffset = offsetHex,
                baudRate = baudRate,
                description = "Picked via PocketLauncher WebUI for ${board.displayName}.",
            )
            res.fold(
                onSuccess = { fw -> flashFirmwareViaUsbOtg(fw, baudRate, offsetHex) },
                onFailure = { err -> _statusMessage.value = "Binary import failed: ${err.message}" },
            )
        }
    }

    fun flashFirmwareViaUsbOtg(fw: FirmwareEntity, baudRate: Int, offsetHex: String) {
        val path = fw.localFilePath
        if (path.isNullOrBlank() || !File(path).exists()) {
            _statusMessage.value = "Binary file unavailable on disk."
            return
        }
        val board = (connectionState.value as? ActiveConnection.UsbConnected)?.board
            ?: HardwareRegistry.findById(settings.value.selectedBoardId)
            ?: HardwareRegistry.boards.first()
        val effectiveBaud = baudRate.takeIf { it > 0 } ?: settings.value.defaultBaudRate
        val effectiveOffset = offsetHex.ifBlank { fw.flashOffset.ifBlank { board.appOffset } }

        val svc = boundUsbService
        if (svc != null) {
            svc.startBinaryFlash(
                file = File(path),
                firmwareTitle = fw.title,
                board = board,
                options = FlashOptions(
                    baudRate = effectiveBaud,
                    flashOffsetHex = effectiveOffset,
                    flashMode = board.flashMode,
                    eraseBeforeFlash = false,
                ),
            )
        } else {
            UsbOtgManagerService.startServiceAction(
                appContext,
                UsbOtgManagerService.createFlashBinaryIntent(
                    context = appContext,
                    firmwarePath = path,
                    firmwareTitle = fw.title,
                    boardId = board.id,
                    baudRate = effectiveBaud,
                    offsetHex = effectiveOffset,
                    flashMode = board.flashMode,
                    eraseBefore = false,
                ),
            )
        }
        _statusMessage.value = "Flashing '${fw.title}' to ${board.displayName} @ $effectiveBaud baud…"
    }

    fun pushFirmwareViaWifiOta(
        fw: FirmwareEntity,
        targetBaseUrl: String = _currentUrl.value,
        protocol: OtaUploadProtocol = OtaUploadProtocol.ESP_MULTIPART_UPDATE,
    ) {
        val path = fw.localFilePath
        if (path.isNullOrBlank() || !File(path).exists()) {
            _statusMessage.value = "Binary file unavailable on disk."
            return
        }
        val activeWifiHost = (connectionState.value as? ActiveConnection.WifiConnected)?.host
            ?: networkEndpoints.value.firstOrNull()?.host
        val config = parseTargetConfig(targetBaseUrl, protocol, fallbackHost = activeWifiHost)
        val file = File(path)
        val svc = boundWifiOtaService
        if (svc != null) {
            svc.startOtaPush(file, fw.title, config)
        } else {
            WifiOtaFlashingService.startServiceAction(
                appContext,
                WifiOtaFlashingService.createPushIntent(appContext, path, fw.title, config),
            )
        }
        _statusMessage.value = "Pushing '${fw.title}' over Wi-Fi OTA -> ${config.buildTargetUrl()}"
    }

    fun cancelWifiOta() {
        boundWifiOtaService?.cancelOtaTransfer() ?: WifiOtaFlashingService.startServiceAction(
            appContext,
            Intent(appContext, WifiOtaFlashingService::class.java).setAction(WifiOtaFlashingService.ACTION_CANCEL_OTA),
        )
    }

    fun createJsBridge(): AndroidLauncherBridge = AndroidLauncherBridge()

    inner class AndroidLauncherBridge {

        private fun isTrusted(): Boolean = isTrustedBridgeOrigin(activeWebOriginUrl)

        @JavascriptInterface
        fun getStatusJson(): String {
            if (!isTrusted()) return "{}"
            val conn = connectionState.value
            val label = when (conn) {
                is ActiveConnection.UsbConnected -> "USB: ${conn.board.id} @ ${conn.baudRate}"
                is ActiveConnection.BleConnected -> "BLE: ${conn.deviceName}"
                is ActiveConnection.WifiConnected -> "Wi-Fi: ${conn.host}:${conn.port}"
                is ActiveConnection.Disconnected -> "Disconnected"
            }
            val flash = usbFlashState.value
            val flashObj = JSONObject().apply {
                put("isFlashing", flash.isFlashing)
                put("progress", flash.progress.toDouble())
                put("statusText", flash.statusText)
                put("resultMessage", flash.resultMessage ?: JSONObject.NULL)
                put("isError", flash.isError)
            }
            val ota = wifiOtaState.value
            val otaObj = JSONObject().apply {
                put("isFlashing", ota.isFlashing)
                put("progress", ota.progress.toDouble())
                put("statusText", ota.statusText)
                put("targetUrl", ota.targetUrl)
                put("resultMessage", ota.resultMessage ?: JSONObject.NULL)
                put("isError", ota.isError)
            }
            return JSONObject().apply {
                put("connected", conn !is ActiveConnection.Disconnected)
                put("connectionLabel", label)
                put("defaultBaudRate", settings.value.defaultBaudRate)
                put("boardId", settings.value.selectedBoardId)
                put("flashState", flashObj)
                put("otaState", otaObj)
            }.toString()
        }

        @JavascriptInterface
        fun listUsbDevicesJson(): String {
            if (!isTrusted()) return "[]"
            val arr = JSONArray()
            usbDevices.value.forEach { d ->
                arr.put(
                    JSONObject().apply {
                        put("deviceName", d.deviceName)
                        put("productLabel", d.productLabel)
                        put("chipset", d.bridgeType.chipsetLabel)
                        put("vidPid", "%04X:%04X".format(d.vendorId, d.productId))
                        put("boardName", d.matchedBoard.displayName)
                        put("hasPermission", d.hasPermission)
                    }
                )
            }
            return arr.toString()
        }

        @JavascriptInterface
        fun listReadyFirmwaresJson(): String {
            if (!isTrusted()) return "[]"
            val arr = JSONArray()
            readyBinaries.value.take(25).forEach { fw ->
                arr.put(
                    JSONObject().apply {
                        put("id", fw.id)
                        put("title", fw.title)
                        put("device", fw.deviceTagsCsv)
                        put("sizeKb", (fw.sizeBytes / 1024L).coerceAtLeast(1L))
                        put("offset", fw.flashOffset)
                    }
                )
            }
            return arr.toString()
        }

        @JavascriptInterface
        fun scanUsbSerial() {
            if (!isTrusted()) return
            boundUsbService?.scanSerialDevices() ?: transport.refreshUsbDevices()
        }

        @JavascriptInterface
        fun connectUsbSerial(deviceName: String, baudRate: Int) {
            if (!isTrusted()) return
            val dev = usbDevices.value.firstOrNull { it.deviceName == deviceName }?.usbDevice
                ?: usbDevices.value.firstOrNull()?.usbDevice
                ?: return
            val targetBaud = baudRate.takeIf { it > 0 } ?: settings.value.defaultBaudRate
            boundUsbService?.connectSerial(dev, targetBaud) ?: viewModelScope.launch {
                if (!transport.usbDevices.value.any { it.usbDevice.deviceId == dev.deviceId && it.hasPermission }) {
                    transport.requestUsbPermission(dev)
                } else {
                    transport.connectUsb(dev, targetBaud)
                }
            }
        }

        @JavascriptInterface
        fun selectBaudRate(baudRate: Int) {
            if (!isTrusted() || baudRate <= 0) return
            viewModelScope.launch {
                settingsStore.setDefaultBaudRate(baudRate)
                boundUsbService?.updateBaudRate(baudRate) ?: transport.setUsbBaudRate(baudRate)
            }
        }

        @JavascriptInterface
        fun pulseBootloader() {
            if (!isTrusted()) return
            boundUsbService?.pulseBootloaderReset() ?: viewModelScope.launch {
                transport.pulseUsbBootloaderReset()
            }
        }

        @JavascriptInterface
        fun flashFirmwareById(firmwareId: String, baudRate: Int, offsetHex: String) {
            if (!isTrusted()) return
            val fw = readyBinaries.value.firstOrNull { it.id == firmwareId } ?: return
            flashFirmwareViaUsbOtg(fw, baudRate, offsetHex)
        }

        @JavascriptInterface
        fun pushWifiOta(firmwareId: String, targetUrl: String, protocolName: String) {
            if (!isTrusted()) return
            val fw = readyBinaries.value.firstOrNull { it.id == firmwareId } ?: return
            val proto = runCatching { OtaUploadProtocol.valueOf(protocolName) }
                .getOrDefault(OtaUploadProtocol.ESP_MULTIPART_UPDATE)
            pushFirmwareViaWifiOta(fw, targetUrl, proto)
        }

        @JavascriptInterface
        fun pickAndFlashBinary(baudRate: Int, offsetHex: String) {
            if (!isTrusted()) return
            _pickAndFlashEvents.tryEmit(
                PendingPickAndFlashRequest(
                    baudRate = baudRate.takeIf { it > 0 } ?: settings.value.defaultBaudRate,
                    offsetHex = offsetHex.ifBlank { "0x10000" },
                )
            )
        }

        @JavascriptInterface
        fun sendCommand(command: String) {
            if (!isTrusted() || command.isBlank()) return
            viewModelScope.launch {
                val res = transport.sendCommand(command)
                if (res.isFailure) {
                    _statusMessage.value = res.exceptionOrNull()?.message
                }
            }
        }

        @JavascriptInterface
        fun saveLauncherConfig(jsonText: String) {
            if (!isTrusted()) return
            viewModelScope.launch {
                scriptRepository.saveScript(
                    existingId = null,
                    fileName = "launcher.cfg",
                    content = jsonText,
                    language = ScriptLanguage.YAML_JSON,
                    targetDeviceId = settings.value.selectedBoardId,
                )
                _statusMessage.value = "Saved launcher.cfg to Script Vault."
            }
        }

        @JavascriptInterface
        fun openNativeTab(route: String) {
            if (!isTrusted()) return
            _navigateTabEvents.tryEmit(route)
        }
    }

    companion object {
        const val ESPHOME_WEB_URL = "https://web.esphome.io/"

        fun isTrustedBridgeOrigin(url: String?): Boolean {
            if (url.isNullOrBlank()) return false
            val lower = url.lowercase()
            return lower.startsWith("https://web.esphome.io") ||
                lower.startsWith("https://bmorcelli.github.io/launcher") ||
                lower.startsWith("http://192.168.") ||
                lower.startsWith("http://10.") ||
                lower.startsWith("http://172.")
        }

        fun isBinaryFirmwareUrl(url: String?, contentDisposition: String?, mimeType: String?): Boolean {
            val combined = "${url.orEmpty()} ${contentDisposition.orEmpty()} ${mimeType.orEmpty()}".lowercase()
            return combined.contains(".bin") ||
                combined.contains(".uf2") ||
                combined.contains(".hex") ||
                combined.contains(".img") ||
                mimeType.equals("application/octet-stream", ignoreCase = true)
        }

        fun parseTargetConfig(
            rawUrl: String,
            protocol: OtaUploadProtocol,
            fallbackHost: String? = null,
        ): WifiOtaTargetConfig {
            val trimmed = rawUrl.trim()
            val candidate = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed
            } else if (trimmed.startsWith("file://") || trimmed.isBlank()) {
                "http://${fallbackHost?.takeIf { it.isNotBlank() } ?: "192.168.4.1"}"
            } else {
                "http://$trimmed"
            }
            val parsed = runCatching { URI(candidate) }.getOrNull()
            val rawHost = parsed?.host.orEmpty()
            val isRemoteDocsHost = rawHost.contains("github.com", ignoreCase = true) ||
                rawHost.contains("github.io", ignoreCase = true)
            val resolvedHost = if (rawHost.isBlank() || isRemoteDocsHost) {
                fallbackHost?.takeIf { it.isNotBlank() } ?: "192.168.4.1"
            } else {
                rawHost
            }
            val useHttps = !isRemoteDocsHost && parsed?.scheme.equals("https", ignoreCase = true)
            val resolvedPort = parsed?.port?.takeIf { it > 0 } ?: if (useHttps) 443 else 80
            val path = parsed?.path?.takeIf { it.length > 1 } ?: protocol.defaultPath
            return WifiOtaTargetConfig(
                useHttps = useHttps,
                host = resolvedHost,
                port = resolvedPort,
                endpointPath = path,
                protocol = protocol,
            )
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = LauncherViewModel(container) as T
    }
}
