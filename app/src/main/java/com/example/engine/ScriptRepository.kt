package com.example.engine

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.example.data.local.ScriptDao
import com.example.data.local.ScriptEntity
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

enum class ScriptLanguage(
    val displayName: String,
    val extension: String,
    val commentPrefix: String,
    val keywords: List<String>,
    val starterTemplate: String,
    val helperCheatsheet: List<Pair<String, String>>,
) {
    MICROPYTHON(
        displayName = "MicroPython",
        extension = "py",
        commentPrefix = "#",
        keywords = listOf("import", "from", "def", "class", "while", "for", "in", "if", "elif", "else", "try", "except", "return", "True", "False", "None", "await", "async"),
        starterTemplate = """# MicroPython script for ESP32 / RP2040 / M5Stack
from machine import Pin, I2C
import time

led = Pin(2, Pin.OUT)
while True:
    led.value(1 - led.value())
    print("Heartbeat tick:", time.ticks_ms())
    time.sleep(1)
""",
        helperCheatsheet = listOf(
            "GPIO Output" to "from machine import Pin\npin = Pin(2, Pin.OUT)\npin.on()",
            "I2C Bus Scan" to "from machine import Pin, I2C\ni2c = I2C(0, scl=Pin(22), sda=Pin(21))\nprint([hex(a) for a in i2c.scan()])",
            "Wi-Fi Connect" to "import network\nwlan = network.WLAN(network.STA_IF)\nwlan.active(True)\nwlan.connect('SSID', 'PASS')",
            "ADC Read" to "from machine import ADC, Pin\nadc = ADC(Pin(34))\nprint(adc.read())",
        )
    ),
    PYTHON(
        displayName = "Python (RPi)",
        extension = "py",
        commentPrefix = "#",
        keywords = listOf("import", "from", "def", "class", "while", "for", "in", "if", "elif", "else", "with", "as", "try", "except", "return", "True", "False", "None"),
        starterTemplate = """#!/usr/bin/env python3
import time
import platform

print(f"Running on {platform.node()} ({platform.machine()})")
while True:
    print("Telemetry ping:", time.strftime("%H:%M:%S"))
    time.sleep(2)
""",
        helperCheatsheet = listOf(
            "gpiozero LED" to "from gpiozero import LED\nled = LED(17)\nled.blink(on_time=0.5, off_time=0.5)",
            "Socket Server" to "import socket\ns = socket.socket()\ns.bind(('0.0.0.0', 9000))\ns.listen(1)",
            "Subprocess Cmd" to "import subprocess\nout = subprocess.check_output(['vcgencmd', 'measure_temp'])\nprint(out.decode())",
        )
    ),
    ARDUINO_CPP(
        displayName = "Arduino C++",
        extension = "ino",
        commentPrefix = "//",
        keywords = listOf("void", "int", "float", "bool", "char", "byte", "const", "if", "else", "for", "while", "return", "setup", "loop", "pinMode", "digitalWrite", "digitalRead", "Serial", "delay", "HIGH", "LOW", "OUTPUT", "INPUT"),
        starterTemplate = """// Arduino Sketch (.ino)
#include <Arduino.h>

void setup() {
  Serial.begin(115200);
  pinMode(LED_BUILTIN, OUTPUT);
  Serial.println("PocketBurner Node Ready");
}

void loop() {
  digitalWrite(LED_BUILTIN, HIGH);
  delay(500);
  digitalWrite(LED_BUILTIN, LOW);
  delay(500);
}
""",
        helperCheatsheet = listOf(
            "Serial Echo" to "if (Serial.available()) {\n  String cmd = Serial.readStringUntil('\\n');\n  Serial.println(\"ACK: \" + cmd);\n}",
            "I2C Wire Init" to "#include <Wire.h>\nWire.begin();\nWire.beginTransmission(0x3C);",
            "ESP Wi-Fi STA" to "#include <WiFi.h>\nWiFi.begin(\"SSID\", \"PASS\");\nwhile (WiFi.status() != WL_CONNECTED) delay(250);",
        )
    ),
    BASH(
        displayName = "Bash Shell",
        extension = "sh",
        commentPrefix = "#",
        keywords = listOf("if", "then", "else", "fi", "for", "do", "done", "while", "echo", "export", "sudo", "systemctl", "grep", "curl", "cat", "chmod"),
        starterTemplate = """#!/usr/bin/env bash
set -euo pipefail

echo "=== Host Telemetry ==="
uname -a
uptime
vcgencmd measure_temp 2>/dev/null || true
""",
        helperCheatsheet = listOf(
            "Systemd Service" to "sudo systemctl restart iot-agent.service\nsudo systemctl status iot-agent --no-pager",
            "I2C Detect" to "sudo i2cdetect -y 1",
            "Tail Syslog" to "journalctl -f -n 50",
        )
    ),
    JAVASCRIPT(
        displayName = "JavaScript",
        extension = "js",
        commentPrefix = "//",
        keywords = listOf("const", "let", "var", "function", "async", "await", "return", "if", "else", "for", "while", "import", "export", "true", "false", "null", "console"),
        starterTemplate = """// Node.js / Espruino IoT Script
const intervalMs = 1000;
let tick = 0;

setInterval(() => {
  tick += 1;
  console.log("Sensor tick #" + tick);
}, intervalMs);
""",
        helperCheatsheet = listOf(
            "HTTP Fetch" to "const res = await fetch('http://192.168.1.10/status');\nconsole.log(await res.json());",
            "Espruino Pin" to "digitalWrite(LED1, 1);\nsetTimeout(() => digitalWrite(LED1, 0), 500);",
        )
    ),
    LUA(
        displayName = "Lua (NodeMCU)",
        extension = "lua",
        commentPrefix = "--",
        keywords = listOf("local", "function", "end", "if", "then", "else", "elseif", "for", "while", "do", "return", "true", "false", "nil"),
        starterTemplate = """-- NodeMCU ESP8266 Lua init.lua
local pin = 4
gpio.mode(pin, gpio.OUTPUT)
tmr.create():alarm(1000, tmr.ALARM_AUTO, function()
  local state = gpio.read(pin)
  gpio.write(pin, 1 - state)
  print("GPIO toggle -> " .. tostring(1 - state))
end)
""",
        helperCheatsheet = listOf(
            "Wi-Fi STA" to "wifi.setmode(wifi.STATION)\nwifi.sta.config({ssid=\"SSID\", pwd=\"PASS\"})",
            "Timer Alarm" to "tmr.create():alarm(2000, tmr.ALARM_AUTO, function() print(\"ping\") end)",
        )
    ),
    YAML_JSON(
        displayName = "ESPHome / JSON",
        extension = "yaml",
        commentPrefix = "#",
        keywords = listOf("esphome", "esp32", "esp8266", "wifi", "ssid", "password", "ota", "api", "logger", "sensor", "switch", "platform", "gpio", "true", "false"),
        starterTemplate = """esphome:
  name: pocket-node
  platform: ESP32
  board: esp32dev

logger:
api:
ota:
wifi:
  ssid: "YourWiFi"
  password: "YourPassword"
""",
        helperCheatsheet = listOf(
            "GPIO Switch" to "switch:\n  - platform: gpio\n    pin: GPIO2\n    name: \"Board LED\"",
            "DHT Sensor" to "sensor:\n  - platform: dht\n    pin: GPIO4\n    temperature:\n      name: \"Temp\"",
        )
    );

    companion object {
        fun detectFromFileName(fileName: String): ScriptLanguage {
            val ext = fileName.substringAfterLast(".", "").lowercase()
            return when (ext) {
                "py" -> if (fileName.contains("rpi", true)) PYTHON else MICROPYTHON
                "ino", "cpp", "c", "h" -> ARDUINO_CPP
                "sh", "bash" -> BASH
                "js", "mjs" -> JAVASCRIPT
                "lua" -> LUA
                "yaml", "yml", "json" -> YAML_JSON
                else -> MICROPYTHON
            }
        }
    }
}

enum class ScriptTransferMode(val label: String, val subtitle: String) {
    MICROPYTHON_RAW_REPL("MicroPython Raw REPL", "Ctrl-C + Ctrl-A writes file directly to ESP/Pico flash"),
    LINUX_BASE64_SHELL("Linux / RPi Base64 Push", "Writes via base64 decode over SSH/UART shell"),
    DIRECT_STREAM("Direct / AES-256 Stream", "Sends script lines directly (encrypted if toggle is ON)"),
}

class ScriptRepository(
    private val context: Context,
    private val scriptDao: ScriptDao,
    private val transport: TransportManager,
) {
    private val scriptsDir: File
        get() = File(context.filesDir, "scripts").apply { mkdirs() }

    val allScripts: Flow<List<ScriptEntity>> = scriptDao.observeAll()

    suspend fun saveScript(
        existingId: String?,
        fileName: String,
        language: ScriptLanguage,
        content: String,
        targetDeviceId: String,
    ): ScriptEntity = withContext(Dispatchers.IO) {
        val cleanName = fileName.trim().ifBlank { "untitled.${language.extension}" }
        val safeFileName = cleanName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val file = File(scriptsDir, safeFileName)
        val bytes = content.toByteArray(Charsets.UTF_8)
        file.writeBytes(bytes)
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val entity = ScriptEntity(
            id = existingId ?: "scr_${UUID.randomUUID().toString().take(8)}",
            fileName = cleanName,
            language = language.name,
            filePath = file.absolutePath,
            targetDeviceId = targetDeviceId,
            sizeBytes = bytes.size.toLong(),
            sha256 = sha,
            updatedAt = System.currentTimeMillis(),
        )
        scriptDao.upsert(entity)
        entity
    }

    suspend fun loadScriptContent(fileName: String): Pair<ScriptEntity?, String> = withContext(Dispatchers.IO) {
        val existing = scriptDao.findByName(fileName)
        if (existing != null) {
            val file = File(existing.filePath)
            if (file.exists()) return@withContext existing to file.readText()
        }
        val lang = ScriptLanguage.detectFromFileName(fileName)
        null to lang.starterTemplate
    }

    suspend fun deleteScript(entity: ScriptEntity) = withContext(Dispatchers.IO) {
        File(entity.filePath).delete()
        scriptDao.deleteById(entity.id)
    }

    suspend fun exportScriptToUri(content: String, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: return@withContext Result.failure(IllegalStateException("Cannot write to selected file"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun transferScriptToDevice(
        fileName: String,
        content: String,
        mode: ScriptTransferMode,
        onProgress: (Float, String) -> Unit,
    ): Result<String> = withContext(Dispatchers.IO) {
        if (transport.connectionState.value is ActiveConnection.Disconnected) {
            return@withContext Result.failure(
                IllegalStateException("Not connected to any device. Connect via USB OTG, BLE, or Wi-Fi first.")
            )
        }
        when (mode) {
            ScriptTransferMode.MICROPYTHON_RAW_REPL -> {
                onProgress(0.10f, "Entering MicroPython Raw REPL (Ctrl-C, Ctrl-A)...")
                transport.writeWireBytes(byteArrayOf(0x03, 0x03))
                delay(120)
                transport.writeWireBytes(byteArrayOf(0x01)) // Raw REPL
                delay(120)

                val b64 = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                val pythonLoader = """
import ubinascii
f = open('$fileName', 'wb')
f.write(ubinascii.a2b_base64('$b64'))
f.close()
print('POCKETBURNER_SAVED:$fileName')
""".trimIndent()
                onProgress(0.55f, "Streaming $fileName to device flash...")
                transport.sendCommand(pythonLoader, "\n")
                delay(100)
                transport.writeWireBytes(byteArrayOf(0x04)) // Ctrl-D execute
                delay(120)
                transport.writeWireBytes(byteArrayOf(0x02)) // Ctrl-B exit raw REPL
                onProgress(1f, "Transferred $fileName via MicroPython Raw REPL!")
                Result.success("Saved $fileName onto device filesystem")
            }
            ScriptTransferMode.LINUX_BASE64_SHELL -> {
                val b64 = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                onProgress(0.35f, "Pushing Base64 payload to shell...")
                val cmd = "echo '$b64' | base64 -d > '$fileName' && chmod +x '$fileName' && ls -lh '$fileName'"
                val res = transport.sendCommand(cmd, "\n")
                if (res.isFailure) return@withContext Result.failure(res.exceptionOrNull()!!)
                onProgress(1f, "Script written to remote shell as $fileName")
                Result.success("Transferred $fileName via Base64 shell push")
            }
            ScriptTransferMode.DIRECT_STREAM -> {
                val lines = content.lines()
                lines.forEachIndexed { idx, line ->
                    val res = transport.sendCommand(line, "\n")
                    if (res.isFailure) return@withContext Result.failure(res.exceptionOrNull()!!)
                    onProgress((idx + 1).toFloat() / lines.size.coerceAtLeast(1), "Sending line ${idx + 1}/${lines.size}")
                    delay(15)
                }
                Result.success("Streamed ${lines.size} lines of $fileName")
            }
        }
    }
}
