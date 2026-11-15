package com.example.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.AppContainer
import com.example.MainActivity
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Foreground-capable Android Service orchestrating Wi-Fi OTA (Over-the-Air) HTTP/HTTPS
 * firmware binary transfers to compatible ESP8266/ESP32/Arduino targets on the local network.
 */
class WifiOtaFlashingService : Service() {

    inner class WifiOtaBinder : Binder() {
        fun getService(): WifiOtaFlashingService = this@WifiOtaFlashingService
    }

    private val binder = WifiOtaBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var transportManager: TransportManager
    private lateinit var integrityService: FirmwareIntegrityService
    private lateinit var otaUploader: WifiOtaUploader

    private var monitorJob: Job? = null
    private var otaJob: Job? = null
    private var isForegroundActive = false

    val otaProgressState: StateFlow<WifiOtaProgressState>
        get() = transportManager.wifiOtaState

    override fun onCreate() {
        super.onCreate()
        val container = AppContainer.getInstance(applicationContext)
        transportManager = container.transportManager
        integrityService = container.firmwareIntegrityService
        otaUploader = WifiOtaUploader(integrityService = integrityService)
        ensureNotificationChannel()
        transportManager.setWifiOtaServiceRunning(true)

        monitorJob = serviceScope.launch {
            transportManager.wifiOtaState.collect { ota ->
                if (ota.isFlashing) {
                    val pct = (ota.progress * 100f).toInt().coerceIn(0, 100)
                    promoteForeground(
                        title = "Wi-Fi OTA: ${ota.firmwareTitle.ifBlank { "Firmware Binary" }}",
                        text = "${ota.targetUrl} • ${ota.statusText}",
                        progressPercent = pct,
                    )
                } else if (isForegroundActive) {
                    ServiceCompat.stopForeground(
                        this@WifiOtaFlashingService,
                        ServiceCompat.STOP_FOREGROUND_REMOVE,
                    )
                    isForegroundActive = false
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_OTA_PUSH -> {
                val path = intent.getStringExtra(EXTRA_FIRMWARE_PATH)
                val title = intent.getStringExtra(EXTRA_FIRMWARE_TITLE) ?: File(path.orEmpty()).name
                val useHttps = intent.getBooleanExtra(EXTRA_USE_HTTPS, false)
                val host = intent.getStringExtra(EXTRA_HOST) ?: "192.168.4.1"
                val port = intent.getIntExtra(EXTRA_PORT, if (useHttps) 443 else 80)
                val endpointPath = intent.getStringExtra(EXTRA_ENDPOINT_PATH) ?: "/update"
                val protocolName = intent.getStringExtra(EXTRA_PROTOCOL) ?: OtaUploadProtocol.ESP_MULTIPART_UPDATE.name
                val password = intent.getStringExtra(EXTRA_OTA_PASSWORD).orEmpty()
                val allowSelfSigned = intent.getBooleanExtra(EXTRA_ALLOW_SELF_SIGNED, true)
                val expectedSha = intent.getStringExtra(EXTRA_EXPECTED_SHA256)

                if (!path.isNullOrBlank()) {
                    val protocol = runCatching { OtaUploadProtocol.valueOf(protocolName) }
                        .getOrDefault(OtaUploadProtocol.ESP_MULTIPART_UPDATE)
                    startOtaPush(
                        firmwareFile = File(path),
                        firmwareTitle = title,
                        config = WifiOtaTargetConfig(
                            useHttps = useHttps,
                            host = host,
                            port = port,
                            endpointPath = endpointPath,
                            protocol = protocol,
                            otaPassword = password,
                            allowSelfSignedTls = allowSelfSigned,
                            expectedSha256 = expectedSha,
                        ),
                    )
                }
            }
            ACTION_CANCEL_OTA -> {
                cancelOtaTransfer()
            }
            ACTION_STOP_SERVICE -> {
                stopOtaService()
            }
        }
        return START_NOT_STICKY
    }

    fun probeDeviceEndpoint(config: WifiOtaTargetConfig, onResult: (Result<String>) -> Unit = {}) {
        serviceScope.launch(Dispatchers.IO) {
            val res = otaUploader.probeEndpoint(config)
            res.fold(
                onSuccess = { msg ->
                    transportManager.appendLog(LogDirection.SYS, msg)
                    transportManager.updateWifiOtaState(
                        WifiOtaProgressState(
                            isFlashing = false,
                            progress = 0f,
                            statusText = msg,
                            targetUrl = config.buildTargetUrl(),
                            resultMessage = msg,
                            isError = false,
                        )
                    )
                },
                onFailure = { err ->
                    val errMsg = err.message ?: "Endpoint unreachable"
                    transportManager.appendLog(LogDirection.ERR, errMsg)
                    transportManager.updateWifiOtaState(
                        WifiOtaProgressState(
                            isFlashing = false,
                            progress = 0f,
                            statusText = "Probe failed",
                            targetUrl = config.buildTargetUrl(),
                            resultMessage = errMsg,
                            isError = true,
                        )
                    )
                },
            )
            onResult(res)
        }
    }

    fun startOtaPush(
        firmwareFile: File,
        firmwareTitle: String,
        config: WifiOtaTargetConfig,
    ) {
        if (otaJob?.isActive == true) {
            transportManager.appendLog(LogDirection.ERR, "A Wi-Fi OTA upload is already active.")
            return
        }
        val targetUrl = config.buildTargetUrl()
        val total = firmwareFile.length().coerceAtLeast(1L)

        promoteForeground(
            title = "Wi-Fi OTA: $firmwareTitle",
            text = "Connecting -> $targetUrl",
            progressPercent = 2,
        )

        transportManager.updateWifiOtaState(
            WifiOtaProgressState(
                isFlashing = true,
                progress = 0.02f,
                bytesSent = 0L,
                totalBytes = total,
                statusText = "Preparing HTTP/HTTPS binary transfer…",
                firmwareTitle = firmwareTitle,
                targetUrl = targetUrl,
            )
        )
        transportManager.appendLog(
            LogDirection.TX,
            "Wi-Fi OTA (${config.protocol.label}) -> $targetUrl [${firmwareFile.name}, ${total / 1024} KB]",
        )

        otaJob = serviceScope.launch(Dispatchers.IO) {
            val result = otaUploader.pushFirmwareBinary(firmwareFile, config) { sent, size, status ->
                val frac = (sent.toFloat() / size.coerceAtLeast(1L)).coerceIn(0.02f, 1f)
                transportManager.updateWifiOtaState(
                    WifiOtaProgressState(
                        isFlashing = true,
                        progress = frac,
                        bytesSent = sent,
                        totalBytes = size,
                        statusText = status,
                        firmwareTitle = firmwareTitle,
                        targetUrl = targetUrl,
                    )
                )
            }

            val terminalState = result.fold(
                onSuccess = { outcome ->
                    val msg = "OTA HTTP ${outcome.httpCode}: Sent ${outcome.bytesSent / 1024} KB (SHA-256 ${outcome.sha256Hex.take(10)}…) • ${outcome.serverResponse}"
                    transportManager.appendLog(LogDirection.SYS, msg)
                    WifiOtaProgressState(
                        isFlashing = false,
                        progress = 1f,
                        bytesSent = outcome.bytesSent,
                        totalBytes = outcome.bytesSent,
                        statusText = "OTA Complete",
                        firmwareTitle = firmwareTitle,
                        targetUrl = targetUrl,
                        resultMessage = msg,
                        isError = false,
                    )
                },
                onFailure = { err ->
                    val errMsg = err.message ?: "Wi-Fi OTA failed"
                    transportManager.appendLog(LogDirection.ERR, errMsg)
                    WifiOtaProgressState(
                        isFlashing = false,
                        progress = 0f,
                        bytesSent = 0L,
                        totalBytes = total,
                        statusText = "OTA Error",
                        firmwareTitle = firmwareTitle,
                        targetUrl = targetUrl,
                        resultMessage = errMsg,
                        isError = true,
                    )
                },
            )
            transportManager.updateWifiOtaState(terminalState)
        }
    }

    fun cancelOtaTransfer() {
        otaUploader.cancelActiveTransfer()
        otaJob?.cancel()
        otaJob = null
        transportManager.appendLog(LogDirection.SYS, "Wi-Fi OTA transfer cancelled.")
        transportManager.updateWifiOtaState(
            WifiOtaProgressState(
                isFlashing = false,
                progress = 0f,
                statusText = "Cancelled",
                resultMessage = "Wi-Fi OTA transfer cancelled by user.",
                isError = false,
            )
        )
    }

    fun stopOtaService() {
        cancelOtaTransfer()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        isForegroundActive = false
        transportManager.setWifiOtaServiceRunning(false)
        stopSelf()
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        otaUploader.cancelActiveTransfer()
        otaJob?.cancel()
        transportManager.setWifiOtaServiceRunning(false)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java) ?: return
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Wi-Fi OTA Firmware Flashing Service",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Streams HTTP/HTTPS Over-the-Air firmware binaries to ESP and Arduino boards."
                }
                mgr.createNotificationChannel(channel)
            }
        }
    }

    private fun promoteForeground(title: String, text: String, progressPercent: Int) {
        val notification = buildNotification(title, text, progressPercent)
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
        runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceType)
            isForegroundActive = true
        }.onFailure {
            isForegroundActive = false
        }
    }

    private fun buildNotification(title: String, text: String, progressPercent: Int): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val cancelPending = PendingIntent.getService(
            this,
            1,
            Intent(this, WifiOtaFlashingService::class.java).setAction(ACTION_CANCEL_OTA),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, progressPercent.coerceIn(0, 100), false)
            .setContentIntent(contentIntent)
            .addAction(0, "Cancel OTA", cancelPending)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "pocketburner_wifi_ota"
        const val NOTIFICATION_ID = 4104

        const val ACTION_START_OTA_PUSH = "com.example.engine.ota.ACTION_START_OTA_PUSH"
        const val ACTION_CANCEL_OTA = "com.example.engine.ota.ACTION_CANCEL_OTA"
        const val ACTION_STOP_SERVICE = "com.example.engine.ota.ACTION_STOP_SERVICE"

        const val EXTRA_FIRMWARE_PATH = "extra_ota_firmware_path"
        const val EXTRA_FIRMWARE_TITLE = "extra_ota_firmware_title"
        const val EXTRA_USE_HTTPS = "extra_ota_use_https"
        const val EXTRA_HOST = "extra_ota_host"
        const val EXTRA_PORT = "extra_ota_port"
        const val EXTRA_ENDPOINT_PATH = "extra_ota_endpoint_path"
        const val EXTRA_PROTOCOL = "extra_ota_protocol"
        const val EXTRA_OTA_PASSWORD = "extra_ota_password"
        const val EXTRA_ALLOW_SELF_SIGNED = "extra_ota_allow_self_signed"
        const val EXTRA_EXPECTED_SHA256 = "extra_ota_expected_sha256"

        fun createPushIntent(
            context: Context,
            firmwarePath: String,
            firmwareTitle: String,
            config: WifiOtaTargetConfig,
        ): Intent = Intent(context, WifiOtaFlashingService::class.java).apply {
            action = ACTION_START_OTA_PUSH
            putExtra(EXTRA_FIRMWARE_PATH, firmwarePath)
            putExtra(EXTRA_FIRMWARE_TITLE, firmwareTitle)
            putExtra(EXTRA_USE_HTTPS, config.useHttps)
            putExtra(EXTRA_HOST, config.host)
            putExtra(EXTRA_PORT, config.port)
            putExtra(EXTRA_ENDPOINT_PATH, config.endpointPath)
            putExtra(EXTRA_PROTOCOL, config.protocol.name)
            putExtra(EXTRA_OTA_PASSWORD, config.otaPassword)
            putExtra(EXTRA_ALLOW_SELF_SIGNED, config.allowSelfSignedTls)
            if (!config.expectedSha256.isNullOrBlank()) {
                putExtra(EXTRA_EXPECTED_SHA256, config.expectedSha256)
            }
        }

        fun startServiceAction(context: Context, intent: Intent) {
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }.onFailure {
                runCatching { context.startService(intent) }
            }
        }
    }
}
