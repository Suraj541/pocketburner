package com.example.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.usb.UsbDevice
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.AppContainer
import com.example.MainActivity
import com.example.data.local.SettingsStore
import com.example.hardware.HardwareBoardSpec
import com.example.hardware.HardwareRegistry
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Foreground-capable Android Service managing USB OTG host serial devices
 * (CP210x, CH340/CH9102, FTDI, PL2303, Espressif JTAG/CDC, Arduino, RP2040/RP2350),
 * dynamic baud rate configuration, DTR/RTS bootloader pulsing, and binary firmware flashing.
 */
class UsbOtgManagerService : Service() {

    inner class UsbOtgBinder : Binder() {
        fun getService(): UsbOtgManagerService = this@UsbOtgManagerService
    }

    private val binder = UsbOtgBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var transportManager: TransportManager
    private lateinit var flashProtocolEngine: FlashProtocolEngine
    private lateinit var integrityService: FirmwareIntegrityService
    private lateinit var settingsStore: SettingsStore

    private var monitorJob: Job? = null
    private var flashJob: Job? = null
    private var isForegroundActive = false

    val connectionState: StateFlow<ActiveConnection>
        get() = transportManager.connectionState

    val usbDevices: StateFlow<List<DiscoveredUsbDevice>>
        get() = transportManager.usbDevices

    val flashProgressState: StateFlow<UsbFlashProgressState>
        get() = transportManager.usbFlashState

    override fun onCreate() {
        super.onCreate()
        val container = AppContainer.getInstance(applicationContext)
        transportManager = container.transportManager
        flashProtocolEngine = container.flashProtocolEngine
        integrityService = container.firmwareIntegrityService
        settingsStore = container.settingsStore
        ensureNotificationChannel()
        transportManager.setUsbServiceRunning(true)

        monitorJob = serviceScope.launch {
            combine(
                transportManager.connectionState,
                transportManager.usbFlashState,
            ) { conn, flash -> conn to flash }
                .collect { (conn, flash) ->
                    when {
                        flash.isFlashing -> {
                            promoteForeground(
                                title = "Flashing ${flash.firmwareTitle.ifBlank { "Firmware Binary" }}",
                                text = "${flash.boardId} @ ${flash.baudRate} bps • ${flash.statusText}",
                                progressPercent = (flash.progress * 100f).toInt().coerceIn(0, 100),
                                showDisconnect = false,
                            )
                        }
                        conn is ActiveConnection.UsbConnected -> {
                            promoteForeground(
                                title = "USB OTG Serial: ${conn.deviceLabel}",
                                text = "${conn.board.chip} @ ${conn.baudRate} bps • ${conn.board.protocol.label}",
                                progressPercent = -1,
                                showDisconnect = true,
                            )
                        }
                        isForegroundActive -> {
                            ServiceCompat.stopForeground(
                                this@UsbOtgManagerService,
                                ServiceCompat.STOP_FOREGROUND_REMOVE,
                            )
                            isForegroundActive = false
                        }
                    }
                }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SCAN_SERIAL_DEVICES -> {
                transportManager.refreshUsbDevices()
            }
            ACTION_CONNECT_SERIAL -> {
                val targetName = intent.getStringExtra(EXTRA_DEVICE_NAME)
                val baud = intent.getIntExtra(EXTRA_BAUD_RATE, 115200)
                serviceScope.launch(Dispatchers.IO) {
                    transportManager.refreshUsbDevices()
                    val found = transportManager.usbDevices.value.firstOrNull {
                        targetName.isNullOrBlank() || it.deviceName == targetName
                    }
                    if (found != null) {
                        connectSerial(found.usbDevice, baud)
                    } else {
                        transportManager.appendLog(LogDirection.ERR, "USB OTG device not found during connect request.")
                    }
                }
            }
            ACTION_SET_BAUD_RATE -> {
                val baud = intent.getIntExtra(EXTRA_BAUD_RATE, 115200)
                updateBaudRate(baud)
            }
            ACTION_PULSE_BOOTLOADER -> {
                pulseBootloaderReset()
            }
            ACTION_FLASH_BINARY -> {
                val path = intent.getStringExtra(EXTRA_FIRMWARE_PATH)
                val boardId = intent.getStringExtra(EXTRA_BOARD_ID)
                val baud = intent.getIntExtra(EXTRA_BAUD_RATE, 921600)
                val offset = intent.getStringExtra(EXTRA_OFFSET_HEX) ?: "0x10000"
                val flashMode = intent.getStringExtra(EXTRA_FLASH_MODE) ?: "DIO"
                val eraseBefore = intent.getBooleanExtra(EXTRA_ERASE_BEFORE, false)
                val expectedSha = intent.getStringExtra(EXTRA_EXPECTED_SHA256)
                val title = intent.getStringExtra(EXTRA_FIRMWARE_TITLE) ?: File(path.orEmpty()).name

                if (!path.isNullOrBlank()) {
                    val board = boardId?.let { HardwareRegistry.findById(it) }
                        ?: (transportManager.connectionState.value as? ActiveConnection.UsbConnected)?.board
                        ?: HardwareRegistry.boards.first()
                    startBinaryFlash(
                        file = File(path),
                        firmwareTitle = title,
                        board = board,
                        options = FlashOptions(
                            baudRate = baud,
                            flashOffsetHex = offset,
                            flashMode = flashMode,
                            eraseBeforeFlash = eraseBefore,
                            expectedSha256 = expectedSha,
                        ),
                    )
                }
            }
            ACTION_DISCONNECT_USB -> {
                disconnectUsb()
            }
            ACTION_STOP_SERVICE -> {
                stopUsbService()
            }
            else -> {
                transportManager.refreshUsbDevices()
            }
        }
        return START_NOT_STICKY
    }

    fun scanSerialDevices(): List<DiscoveredUsbDevice> {
        transportManager.refreshUsbDevices()
        return transportManager.usbDevices.value
    }

    fun connectSerial(device: UsbDevice, baudRate: Int) {
        serviceScope.launch(Dispatchers.IO) {
            settingsStore.setDefaultBaudRate(baudRate)
            if (!transportManager.usbDevices.value.any { it.usbDevice.deviceId == device.deviceId && it.hasPermission }) {
                transportManager.requestUsbPermission(device)
            } else {
                promoteForeground(
                    title = "Opening USB OTG Serial…",
                    text = "${device.productName ?: device.deviceName} @ $baudRate bps",
                    progressPercent = -1,
                    showDisconnect = true,
                )
                transportManager.connectUsb(device, baudRate)
            }
        }
    }

    fun updateBaudRate(baudRate: Int) {
        serviceScope.launch(Dispatchers.IO) {
            settingsStore.setDefaultBaudRate(baudRate)
            transportManager.setUsbBaudRate(baudRate)
        }
    }

    fun pulseBootloaderReset() {
        serviceScope.launch(Dispatchers.IO) {
            val ok = transportManager.pulseUsbBootloaderReset()
            if (!ok) {
                transportManager.appendLog(LogDirection.ERR, "Connect a USB OTG serial bridge first to pulse DTR/RTS.")
            }
        }
    }

    fun startBinaryFlash(
        file: File,
        firmwareTitle: String,
        board: HardwareBoardSpec,
        options: FlashOptions,
    ) {
        if (flashJob?.isActive == true) {
            transportManager.appendLog(LogDirection.ERR, "Binary flash already in progress.")
            return
        }
        flashJob = serviceScope.launch(Dispatchers.IO) {
            promoteForeground(
                title = "Verifying $firmwareTitle",
                text = "Checking SHA-256 binary integrity…",
                progressPercent = 1,
                showDisconnect = false,
            )

            transportManager.updateUsbFlashState(
                UsbFlashProgressState(
                    isFlashing = true,
                    progress = 0.01f,
                    statusText = "Verifying SHA-256 binary integrity…",
                    firmwareTitle = firmwareTitle,
                    boardId = board.id,
                    baudRate = options.baudRate,
                )
            )

            val verifyRes = integrityService.verifyBeforeFlash(
                file = file,
                expectedSha256 = options.expectedSha256,
                requireExpectedHash = options.requireExpectedSha256,
            ) { frac, msg ->
                transportManager.updateUsbFlashState(
                    UsbFlashProgressState(
                        isFlashing = true,
                        progress = 0.01f + frac * 0.04f,
                        statusText = msg,
                        firmwareTitle = firmwareTitle,
                        boardId = board.id,
                        baudRate = options.baudRate,
                    )
                )
            }
            if (verifyRes.isFailure) {
                val errMsg = verifyRes.exceptionOrNull()?.message ?: "SHA-256 integrity check failed"
                transportManager.appendLog(LogDirection.ERR, errMsg)
                transportManager.updateUsbFlashState(
                    UsbFlashProgressState(
                        isFlashing = false,
                        progress = 0f,
                        statusText = "SHA-256 Integrity Failed",
                        firmwareTitle = firmwareTitle,
                        boardId = board.id,
                        baudRate = options.baudRate,
                        resultMessage = errMsg,
                        isError = true,
                    )
                )
                return@launch
            }

            // Auto-connect first permitted serial device if not currently connected via USB OTG
            if (transportManager.connectionState.value !is ActiveConnection.UsbConnected) {
                transportManager.refreshUsbDevices()
                val firstUsb = transportManager.usbDevices.value.firstOrNull { it.hasPermission }
                if (firstUsb != null) {
                    val initBaud = if (options.baudRate > 115200 && board.protocol == com.example.hardware.FlashProtocol.ESPTOOL_SLIP) {
                        115200
                    } else {
                        options.baudRate
                    }
                    transportManager.connectUsb(firstUsb.usbDevice, initBaud)
                }
            }

            promoteForeground(
                title = "Flashing $firmwareTitle",
                text = "Preparing ${board.displayName} @ ${options.baudRate} bps",
                progressPercent = 5,
                showDisconnect = false,
            )

            transportManager.updateUsbFlashState(
                UsbFlashProgressState(
                    isFlashing = true,
                    progress = 0.05f,
                    statusText = "Initializing USB OTG flash pipeline…",
                    firmwareTitle = firmwareTitle,
                    boardId = board.id,
                    baudRate = options.baudRate,
                )
            )

            if (options.eraseBeforeFlash && board.protocol == com.example.hardware.FlashProtocol.ESPTOOL_SLIP) {
                val eraseRes = flashProtocolEngine.eraseEspFlash(board) { p, msg ->
                    transportManager.updateUsbFlashState(
                        UsbFlashProgressState(
                            isFlashing = true,
                            progress = p * 0.20f,
                            statusText = msg,
                            firmwareTitle = firmwareTitle,
                            boardId = board.id,
                            baudRate = options.baudRate,
                        )
                    )
                }
                if (eraseRes.isFailure) {
                    val errMsg = eraseRes.exceptionOrNull()?.message ?: "SPI flash erase failed"
                    transportManager.updateUsbFlashState(
                        UsbFlashProgressState(
                            isFlashing = false,
                            progress = 0f,
                            statusText = "Erase failed",
                            firmwareTitle = firmwareTitle,
                            boardId = board.id,
                            baudRate = options.baudRate,
                            resultMessage = errMsg,
                            isError = true,
                        )
                    )
                    return@launch
                }
            }

            val res = flashProtocolEngine.flashFirmware(file, board, options) { p, msg ->
                val adjusted = if (options.eraseBeforeFlash) 0.20f + p * 0.80f else p
                transportManager.updateUsbFlashState(
                    UsbFlashProgressState(
                        isFlashing = true,
                        progress = adjusted,
                        statusText = msg,
                        firmwareTitle = firmwareTitle,
                        boardId = board.id,
                        baudRate = options.baudRate,
                    )
                )
            }

            val finalState = res.fold(
                onSuccess = { msg ->
                    UsbFlashProgressState(
                        isFlashing = false,
                        progress = 1f,
                        statusText = "Flash complete",
                        firmwareTitle = firmwareTitle,
                        boardId = board.id,
                        baudRate = options.baudRate,
                        resultMessage = msg,
                        isError = false,
                    )
                },
                onFailure = { err ->
                    UsbFlashProgressState(
                        isFlashing = false,
                        progress = 0f,
                        statusText = "Flash error",
                        firmwareTitle = firmwareTitle,
                        boardId = board.id,
                        baudRate = options.baudRate,
                        resultMessage = err.message ?: "Binary flash failed",
                        isError = true,
                    )
                },
            )
            transportManager.updateUsbFlashState(finalState)
        }
    }

    fun disconnectUsb() {
        flashJob?.cancel()
        if (transportManager.connectionState.value is ActiveConnection.UsbConnected) {
            transportManager.disconnect()
        }
    }

    fun stopUsbService() {
        flashJob?.cancel()
        if (transportManager.connectionState.value is ActiveConnection.UsbConnected) {
            transportManager.disconnect()
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        isForegroundActive = false
        transportManager.setUsbServiceRunning(false)
        stopSelf()
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        flashJob?.cancel()
        transportManager.setUsbServiceRunning(false)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java) ?: return
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "USB OTG Serial & Flasher Service",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Maintains active USB OTG serial bridges and binary firmware flashing sessions."
                }
                mgr.createNotificationChannel(channel)
            }
        }
    }

    private fun promoteForeground(
        title: String,
        text: String,
        progressPercent: Int,
        showDisconnect: Boolean,
    ) {
        val notification = buildNotification(title, text, progressPercent, showDisconnect)
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

    private fun buildNotification(
        title: String,
        text: String,
        progressPercent: Int,
        showDisconnect: Boolean,
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val stopPending = PendingIntent.getService(
            this,
            1,
            Intent(this, UsbOtgManagerService::class.java).setAction(ACTION_STOP_SERVICE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)

        if (progressPercent in 0..100) {
            builder.setProgress(100, progressPercent, false)
        }
        if (showDisconnect) {
            val disconnectPending = PendingIntent.getService(
                this,
                2,
                Intent(this, UsbOtgManagerService::class.java).setAction(ACTION_DISCONNECT_USB),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(0, "Disconnect", disconnectPending)
        }
        builder.addAction(0, "Stop", stopPending)
        return builder.build()
    }

    companion object {
        const val CHANNEL_ID = "pocketburner_usb_otg_manager"
        const val NOTIFICATION_ID = 4103

        const val ACTION_SCAN_SERIAL_DEVICES = "com.example.engine.usb.ACTION_SCAN_SERIAL_DEVICES"
        const val ACTION_CONNECT_SERIAL = "com.example.engine.usb.ACTION_CONNECT_SERIAL"
        const val ACTION_SET_BAUD_RATE = "com.example.engine.usb.ACTION_SET_BAUD_RATE"
        const val ACTION_PULSE_BOOTLOADER = "com.example.engine.usb.ACTION_PULSE_BOOTLOADER"
        const val ACTION_FLASH_BINARY = "com.example.engine.usb.ACTION_FLASH_BINARY"
        const val ACTION_DISCONNECT_USB = "com.example.engine.usb.ACTION_DISCONNECT_USB"
        const val ACTION_STOP_SERVICE = "com.example.engine.usb.ACTION_STOP_SERVICE"

        const val EXTRA_DEVICE_NAME = "extra_usb_device_name"
        const val EXTRA_BAUD_RATE = "extra_usb_baud_rate"
        const val EXTRA_FIRMWARE_PATH = "extra_usb_firmware_path"
        const val EXTRA_FIRMWARE_TITLE = "extra_usb_firmware_title"
        const val EXTRA_BOARD_ID = "extra_usb_board_id"
        const val EXTRA_OFFSET_HEX = "extra_usb_offset_hex"
        const val EXTRA_FLASH_MODE = "extra_usb_flash_mode"
        const val EXTRA_ERASE_BEFORE = "extra_usb_erase_before"
        const val EXTRA_EXPECTED_SHA256 = "extra_usb_expected_sha256"

        fun createScanIntent(context: Context): Intent =
            Intent(context, UsbOtgManagerService::class.java).setAction(ACTION_SCAN_SERIAL_DEVICES)

        fun createConnectIntent(context: Context, deviceName: String?, baudRate: Int): Intent =
            Intent(context, UsbOtgManagerService::class.java).apply {
                action = ACTION_CONNECT_SERIAL
                putExtra(EXTRA_DEVICE_NAME, deviceName)
                putExtra(EXTRA_BAUD_RATE, baudRate)
            }

        fun createFlashBinaryIntent(
            context: Context,
            firmwarePath: String,
            firmwareTitle: String,
            boardId: String,
            baudRate: Int,
            offsetHex: String,
            flashMode: String,
            eraseBefore: Boolean,
            expectedSha256: String? = null,
        ): Intent = Intent(context, UsbOtgManagerService::class.java).apply {
            action = ACTION_FLASH_BINARY
            putExtra(EXTRA_FIRMWARE_PATH, firmwarePath)
            putExtra(EXTRA_FIRMWARE_TITLE, firmwareTitle)
            putExtra(EXTRA_BOARD_ID, boardId)
            putExtra(EXTRA_BAUD_RATE, baudRate)
            putExtra(EXTRA_OFFSET_HEX, offsetHex)
            putExtra(EXTRA_FLASH_MODE, flashMode)
            putExtra(EXTRA_ERASE_BEFORE, eraseBefore)
            if (!expectedSha256.isNullOrBlank()) {
                putExtra(EXTRA_EXPECTED_SHA256, expectedSha256)
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
