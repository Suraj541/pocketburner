package com.example.ui.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.local.ScriptEntity
import com.example.data.local.SecurityAndTransportSettings
import com.example.engine.ActiveConnection
import com.example.engine.HardwareBatteryTelemetry
import com.example.engine.LogDirection
import com.example.engine.ScriptTransferMode
import com.example.engine.TerminalLogEntry
import com.example.hardware.HardwareRegistry
import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class QuickTaskMacro(
    val label: String,
    val command: String,
)

class TerminalViewModel(private val container: AppContainer) : ViewModel() {

    private val transport = container.transportManager
    private val settingsStore = container.settingsStore
    private val scriptRepo = container.scriptRepository

    val logs: StateFlow<List<TerminalLogEntry>> = transport.logs
    val connectionState: StateFlow<ActiveConnection> = transport.connectionState
    val batteryTelemetry: StateFlow<HardwareBatteryTelemetry> = transport.batteryTelemetry

    val settings: StateFlow<SecurityAndTransportSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SecurityAndTransportSettings())

    val savedScripts: StateFlow<List<ScriptEntity>> = scriptRepo.allScripts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _commandHistory = MutableStateFlow<List<String>>(emptyList())
    val commandHistory: StateFlow<List<String>> = _commandHistory.asStateFlow()

    private val _lineEnding = MutableStateFlow("\n")
    val lineEnding: StateFlow<String> = _lineEnding.asStateFlow()

    private val _openNanoEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val openNanoEvents: SharedFlow<String> = _openNanoEvents.asSharedFlow()

    val quickMacros: List<QuickTaskMacro> = listOf(
        QuickTaskMacro("Battery", "AT+CBC"),
        QuickTaskMacro("SysInfo", "uname -a; free -h; cat /proc/device-tree/model 2>/dev/null || import os; print(os.uname())"),
        QuickTaskMacro("I2C Scan", "import machine; print([hex(x) for x in machine.I2C(0).scan()])"),
        QuickTaskMacro("GPIO Blink", "from machine import Pin; p=Pin(2,Pin.OUT); p.value(1-p.value())"),
        QuickTaskMacro("Wi-Fi IP", "import network; print(network.WLAN(network.STA_IF).ifconfig())"),
        QuickTaskMacro("Flash Info", "import esp; print('Flash:', esp.flash_size())"),
        QuickTaskMacro("Reboot", "import machine; machine.reset()"),
    )

    fun setLineEnding(ending: String) {
        _lineEnding.value = ending
    }

    fun toggleEncryption() {
        viewModelScope.launch {
            val next = !settings.value.encryptionEnabled
            settingsStore.setEncryptionEnabled(next)
            transport.appendLog(
                LogDirection.SYS,
                if (next) "Command Encryption ENABLED (AES-256-GCM payload + TLS transport)"
                else "Command Encryption DISABLED (Plaintext mode)"
            )
        }
    }

    fun probeHardwareBattery() {
        viewModelScope.launch {
            val res = transport.probeHardwareBattery()
            res.exceptionOrNull()?.let { err ->
                transport.appendLog(LogDirection.ERR, err.message ?: "Battery probe failed")
            }
        }
    }

    fun clearTerminal() {
        transport.clearLogs()
    }

    fun submitInput(rawInput: String) {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return
        _commandHistory.value = (_commandHistory.value + trimmed).distinct().takeLast(30)

        viewModelScope.launch {
            // Local Terminal Built-in CLI Commands
            when {
                trimmed.equals("help", ignoreCase = true) -> printHelp()
                trimmed.equals("clear", ignoreCase = true) -> transport.clearLogs()
                trimmed.equals("ls", ignoreCase = true) -> listLocalScripts()
                trimmed.equals("boardinfo", ignoreCase = true) -> printActiveBoardInfo()
                trimmed.startsWith("nano ", ignoreCase = true) || trimmed.equals("nano", ignoreCase = true) -> {
                    val fileName = trimmed.substringAfter("nano", "").trim().ifBlank { "script.py" }
                    transport.appendLog(LogDirection.SYS, "Opening Nano Studio -> $fileName")
                    _openNanoEvents.emit(fileName)
                }
                trimmed.startsWith("cat ", ignoreCase = true) -> {
                    val name = trimmed.substringAfter("cat ").trim()
                    catLocalScript(name)
                }
                trimmed.startsWith("rm ", ignoreCase = true) -> {
                    val name = trimmed.substringAfter("rm ").trim()
                    deleteLocalScript(name)
                }
                trimmed.startsWith("send ", ignoreCase = true) || trimmed.startsWith("upload ", ignoreCase = true) -> {
                    val fileName = trimmed.substringAfter(" ").trim()
                    transferLocalScript(fileName)
                }
                trimmed.equals("encrypt on", ignoreCase = true) -> {
                    settingsStore.setEncryptionEnabled(true)
                    transport.appendLog(LogDirection.SYS, "AES-256-GCM + TLS encryption turned ON.")
                }
                trimmed.equals("encrypt off", ignoreCase = true) -> {
                    settingsStore.setEncryptionEnabled(false)
                    transport.appendLog(LogDirection.SYS, "Encryption turned OFF (Plaintext).")
                }
                trimmed.startsWith("baud ", ignoreCase = true) -> {
                    val baud = trimmed.substringAfter("baud ").trim().toIntOrNull()
                    if (baud != null && baud > 0) {
                        settingsStore.setDefaultBaudRate(baud)
                        transport.setUsbBaudRate(baud)
                    } else {
                        transport.appendLog(LogDirection.ERR, "Usage: baud 115200 | 921600")
                    }
                }
                else -> {
                    val res = transport.sendCommand(rawInput, _lineEnding.value)
                    res.exceptionOrNull()?.let { err ->
                        transport.appendLog(LogDirection.ERR, err.message ?: "Send failed")
                    }
                }
            }
        }
    }

    fun sendMacro(macro: QuickTaskMacro) {
        viewModelScope.launch {
            val res = transport.sendCommand(macro.command, _lineEnding.value)
            res.exceptionOrNull()?.let { err ->
                transport.appendLog(LogDirection.ERR, err.message ?: "Macro failed")
            }
        }
    }

    private suspend fun listLocalScripts() {
        val list = savedScripts.value
        if (list.isEmpty()) {
            transport.appendLog(LogDirection.SYS, "Local script vault is empty. Run 'nano hello.py' to create one.")
        } else {
            val formatted = list.joinToString("\n") { "  ${it.fileName}  (${it.language}, ${it.sizeBytes} B, target=${it.targetDeviceId})" }
            transport.appendLog(LogDirection.SYS, "Saved local scripts (${list.size}):\n$formatted")
        }
    }

    private suspend fun catLocalScript(name: String) {
        val script = savedScripts.value.firstOrNull { it.fileName.equals(name, ignoreCase = true) }
        if (script == null) {
            transport.appendLog(LogDirection.ERR, "Script '$name' not found in local storage.")
            return
        }
        val text = runCatching { File(script.filePath).readText() }.getOrElse { "" }
        transport.appendLog(LogDirection.SYS, "--- ${script.fileName} ---\n$text")
    }

    private suspend fun deleteLocalScript(name: String) {
        val script = savedScripts.value.firstOrNull { it.fileName.equals(name, ignoreCase = true) }
        if (script == null) {
            transport.appendLog(LogDirection.ERR, "Script '$name' not found.")
            return
        }
        scriptRepo.deleteScript(script)
        transport.appendLog(LogDirection.SYS, "Removed local script '${script.fileName}'.")
    }

    private suspend fun transferLocalScript(fileName: String) {
        val script = savedScripts.value.firstOrNull { it.fileName.equals(fileName, ignoreCase = true) }
        if (script == null) {
            transport.appendLog(LogDirection.ERR, "Local script '$fileName' not found. Create it with 'nano $fileName'.")
            return
        }
        val content = runCatching { File(script.filePath).readText() }.getOrElse { "" }
        val mode = when {
            fileName.endsWith(".py", true) -> ScriptTransferMode.MICROPYTHON_RAW_REPL
            fileName.endsWith(".sh", true) -> ScriptTransferMode.LINUX_BASE64_SHELL
            else -> ScriptTransferMode.DIRECT_STREAM
        }
        val res = scriptRepo.transferScriptToDevice(script.fileName, content, mode) { _, _ -> }
        res.fold(
            onSuccess = { msg -> transport.appendLog(LogDirection.SYS, msg) },
            onFailure = { err -> transport.appendLog(LogDirection.ERR, err.message ?: "Transfer failed") }
        )
    }

    private fun printActiveBoardInfo() {
        val spec = HardwareRegistry.findById(settings.value.selectedBoardId) ?: HardwareRegistry.boards.first()
        transport.appendLog(
            LogDirection.SYS,
            """
            |Board: ${spec.displayName} (${spec.chip})
            |Arch: ${spec.architecture} | Flash: ${spec.flashSizeLabel} | RAM: ${spec.ramLabel}
            |Protocol: ${spec.protocol.label} | BootOffset: ${spec.bootloaderOffset} | AppOffset: ${spec.appOffset}
            |Baud: ${spec.defaultBaud} | GPIO: ${spec.gpioPinoutSummary}
            """.trimMargin()
        )
    }

    private fun printHelp() {
        transport.appendLog(
            LogDirection.SYS,
            """
            |PocketBurner Terminal CLI Commands:
            |  nano <filename>    Open Nano editor for any language (.py/.ino/.sh/.js/.lua/.yaml)
            |  ls                 List scripts saved in local storage
            |  cat <filename>     Print saved script contents
            |  send <filename>    Transfer saved script to connected IoT device
            |  rm <filename>      Delete local script
            |  encrypt on | off   Toggle AES-256-GCM + TLS encryption
            |  baud <rate>        Change USB OTG serial baud rate
            |  boardinfo          Show accurate pinout & flash spec of active board
            |  clear              Clear terminal output
            |  <any other text>   Send directly to connected BLE / Wi-Fi / USB OTG device
            """.trimMargin()
        )
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TerminalViewModel(container) as T
    }
}
