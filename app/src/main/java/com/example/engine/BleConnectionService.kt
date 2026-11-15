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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Foreground-capable Android Service managing BLE peripheral discovery, bonding/pairing,
 * GATT UART session lifecycle, and wireless command transmission across screen navigation.
 */
class BleConnectionService : Service() {

    inner class BleConnectionBinder : Binder() {
        fun getService(): BleConnectionService = this@BleConnectionService
    }

    private val binder = BleConnectionBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var transportManager: TransportManager
    private var monitorJob: Job? = null
    private var isForegroundActive = false

    val connectionState: StateFlow<ActiveConnection>
        get() = transportManager.connectionState

    val bleDevices: StateFlow<List<DiscoveredBleDevice>>
        get() = transportManager.bleDevices

    val isScanning: StateFlow<Boolean>
        get() = transportManager.isBleScanning

    override fun onCreate() {
        super.onCreate()
        transportManager = AppContainer.getInstance(applicationContext).transportManager
        ensureNotificationChannel()
        transportManager.setBleServiceRunning(true)

        monitorJob = serviceScope.launch {
            combine(
                transportManager.connectionState,
                transportManager.isBleScanning,
            ) { conn, scanning ->
                conn to scanning
            }.collect { (conn, scanning) ->
                when {
                    conn is ActiveConnection.BleConnected -> {
                        promoteForeground(
                            title = "BLE Connected: ${conn.deviceName}",
                            text = "${conn.address} • GATT MTU ${conn.mtu} • Ready for commands",
                            showDisconnect = true,
                        )
                    }
                    scanning -> {
                        promoteForeground(
                            title = "PocketBurner BLE Scanner Active",
                            text = "Discovering wireless ESP, Arduino, M5Stack & RPi GATT peripherals…",
                            showDisconnect = false,
                        )
                    }
                    isForegroundActive -> {
                        // Drop foreground notification cleanly once idle and no longer BLE connected
                        ServiceCompat.stopForeground(this@BleConnectionService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        isForegroundActive = false
                    }
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SCAN -> {
                promoteForeground(
                    title = "PocketBurner BLE Scanner Active",
                    text = "Scanning for BLE UART & IoT devices…",
                    showDisconnect = false,
                )
                transportManager.startBleScan()
            }
            ACTION_STOP_SCAN -> {
                transportManager.stopBleScan()
            }
            ACTION_PAIR_DEVICE -> {
                val address = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
                val autoConnect = intent.getBooleanExtra(EXTRA_AUTO_CONNECT, false)
                if (!address.isNullOrBlank()) {
                    transportManager.pairBleDevice(address, autoConnect)
                }
            }
            ACTION_CONNECT_DEVICE -> {
                val address = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
                val name = intent.getStringExtra(EXTRA_DEVICE_NAME) ?: address.orEmpty()
                if (!address.isNullOrBlank()) {
                    promoteForeground(
                        title = "Connecting BLE GATT: $name",
                        text = "Establishing wireless UART link ($address)…",
                        showDisconnect = true,
                    )
                    transportManager.connectBle(address, name)
                }
            }
            ACTION_SEND_COMMAND -> {
                val command = intent.getStringExtra(EXTRA_COMMAND_TEXT)
                if (!command.isNullOrBlank()) {
                    serviceScope.launch(Dispatchers.IO) {
                        transportManager.sendCommand(command)
                    }
                }
            }
            ACTION_DISCONNECT -> {
                transportManager.stopBleScan()
                if (transportManager.connectionState.value is ActiveConnection.BleConnected) {
                    transportManager.disconnect()
                }
            }
            ACTION_STOP_SERVICE -> {
                stopManagerService()
            }
            else -> {
                transportManager.refreshBondedBleDevices()
            }
        }
        return START_NOT_STICKY
    }

    fun startScan() {
        promoteForeground(
            title = "PocketBurner BLE Scanner Active",
            text = "Scanning for wireless BLE UART peripherals…",
            showDisconnect = false,
        )
        transportManager.startBleScan()
    }

    fun stopScan() {
        transportManager.stopBleScan()
    }

    fun refreshBondedDevices() {
        transportManager.refreshBondedBleDevices()
    }

    fun pairDevice(address: String, autoConnectAfterPair: Boolean = false): Result<Unit> {
        return transportManager.pairBleDevice(address, autoConnectAfterPair)
    }

    fun connectDevice(address: String, displayName: String) {
        promoteForeground(
            title = "Connecting BLE GATT: $displayName",
            text = "Opening GATT UART services on $address…",
            showDisconnect = true,
        )
        transportManager.connectBle(address, displayName)
    }

    fun disconnectBle() {
        transportManager.stopBleScan()
        if (transportManager.connectionState.value is ActiveConnection.BleConnected) {
            transportManager.disconnect()
        }
    }

    fun sendCommandAsync(command: String, lineEnding: String = "\n", onResult: (Result<Unit>) -> Unit = {}) {
        serviceScope.launch(Dispatchers.IO) {
            val res = transportManager.sendCommand(command, lineEnding)
            onResult(res)
        }
    }

    fun stopManagerService() {
        transportManager.stopBleScan()
        if (transportManager.connectionState.value is ActiveConnection.BleConnected) {
            transportManager.disconnect()
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        isForegroundActive = false
        transportManager.setBleServiceRunning(false)
        stopSelf()
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        transportManager.setBleServiceRunning(false)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java) ?: return
            val existing = mgr.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "BLE Device Connection Manager",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Maintains active BLE UART connections and wireless command links to IoT boards."
                }
                mgr.createNotificationChannel(channel)
            }
        }
    }

    private fun promoteForeground(title: String, text: String, showDisconnect: Boolean) {
        val notification = buildNotification(title, text, showDisconnect)
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
        runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceType)
            isForegroundActive = true
        }.onFailure {
            // If OS restricts foreground promotion (e.g. background restriction), remain alive while bound
            isForegroundActive = false
        }
    }

    private fun buildNotification(title: String, text: String, showDisconnect: Boolean): Notification {
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
            Intent(this, BleConnectionService::class.java).setAction(ACTION_STOP_SERVICE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)

        if (showDisconnect) {
            val disconnectPending = PendingIntent.getService(
                this,
                2,
                Intent(this, BleConnectionService::class.java).setAction(ACTION_DISCONNECT),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(0, "Disconnect", disconnectPending)
        }
        builder.addAction(0, "Stop", stopPending)
        return builder.build()
    }

    companion object {
        const val CHANNEL_ID = "pocketburner_ble_manager"
        const val NOTIFICATION_ID = 4102

        const val ACTION_START_SCAN = "com.example.engine.ble.ACTION_START_SCAN"
        const val ACTION_STOP_SCAN = "com.example.engine.ble.ACTION_STOP_SCAN"
        const val ACTION_PAIR_DEVICE = "com.example.engine.ble.ACTION_PAIR_DEVICE"
        const val ACTION_CONNECT_DEVICE = "com.example.engine.ble.ACTION_CONNECT_DEVICE"
        const val ACTION_SEND_COMMAND = "com.example.engine.ble.ACTION_SEND_COMMAND"
        const val ACTION_DISCONNECT = "com.example.engine.ble.ACTION_DISCONNECT"
        const val ACTION_STOP_SERVICE = "com.example.engine.ble.ACTION_STOP_SERVICE"

        const val EXTRA_DEVICE_ADDRESS = "extra_device_address"
        const val EXTRA_DEVICE_NAME = "extra_device_name"
        const val EXTRA_AUTO_CONNECT = "extra_auto_connect"
        const val EXTRA_COMMAND_TEXT = "extra_command_text"

        fun createScanIntent(context: Context): Intent =
            Intent(context, BleConnectionService::class.java).setAction(ACTION_START_SCAN)

        fun createConnectIntent(context: Context, address: String, name: String): Intent =
            Intent(context, BleConnectionService::class.java).apply {
                action = ACTION_CONNECT_DEVICE
                putExtra(EXTRA_DEVICE_ADDRESS, address)
                putExtra(EXTRA_DEVICE_NAME, name)
            }

        fun createPairIntent(context: Context, address: String, autoConnect: Boolean = false): Intent =
            Intent(context, BleConnectionService::class.java).apply {
                action = ACTION_PAIR_DEVICE
                putExtra(EXTRA_DEVICE_ADDRESS, address)
                putExtra(EXTRA_AUTO_CONNECT, autoConnect)
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
