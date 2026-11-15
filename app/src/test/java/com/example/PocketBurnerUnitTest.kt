package com.example

import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.example.engine.BleBondStatus
import com.example.engine.BleConnectionService
import com.example.engine.CryptoEnvelopeEngine
import com.example.engine.DiscoveredBleDevice
import com.example.engine.ScriptLanguage
import com.example.engine.TransportManager
import com.example.hardware.DeviceFamily
import com.example.hardware.FirmwareCategory
import com.example.hardware.HardwareRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PocketBurnerUnitTest {

    @Test
    fun aes256GcmRoundTrip_succeedsWithMatchingKeyAndFailsWithWrongKey() {
        val secret = "super-secret-iot-psk"
        val command = "gpio.write(2, 1)".toByteArray(Charsets.UTF_8)

        val envelope = CryptoEnvelopeEngine.encryptCommand(command, secret)
        assertTrue(envelope.wirePayload.startsWith("PBENC1:"))

        val decrypted = CryptoEnvelopeEngine.decryptEnvelope(envelope.wirePayload, secret)
        assertNotNull(decrypted)
        assertEquals("gpio.write(2, 1)", decrypted?.toString(Charsets.UTF_8))

        val wrongDecrypt = CryptoEnvelopeEngine.decryptEnvelope(envelope.wirePayload, "wrong-key")
        assertNull(wrongDecrypt)
    }

    @Test
    fun hardwareRegistry_includesAllRequestedM5EspArduinoRpiDevices() {
        val requiredIds = listOf(
            "Core2", "CoreS3", "Cardputer", "Cardputer ADV", "AtomS3R", "StickC-Plus2",
            "NanoC6", "NanoH2", "Tab5", "ToughC5", "Arduino Nesso N1", "VAMeter",
            "ESP8266", "ESP32 DevKit", "ESP32-S3", "ESP32-C5", "ESP32-C6", "ESP32-H2", "ESP32-P4",
            "Arduino Uno R3", "Arduino Uno R4 WiFi", "Arduino Mega 2560", "Arduino Nano ESP32",
            "Raspberry Pi 5", "Raspberry Pi 4B", "Raspberry Pi Zero 2 W", "Raspberry Pi Pico", "Raspberry Pi Pico 2",
        )
        requiredIds.forEach { id ->
            assertNotNull("Missing HardwareBoardSpec for $id", HardwareRegistry.findById(id))
        }
        assertEquals(18, FirmwareCategory.entries.size)
        assertTrue(HardwareRegistry.boards.any { it.family == DeviceFamily.ARDUINO })
        assertTrue(HardwareRegistry.boards.any { it.family == DeviceFamily.RASPBERRY_PI })
        assertTrue(HardwareRegistry.boards.any { it.family == DeviceFamily.ESP_IOT })
        assertTrue(HardwareRegistry.boards.any { it.family == DeviceFamily.M5STACK })
    }

    @Test
    fun hardwareRegistry_searchBoards_matchesByChipProtocolVidPidAndFamily() {
        val c6Matches = HardwareRegistry.searchBoards("ESP32-C6")
        assertTrue(c6Matches.any { it.id == "ESP32-C6" })
        assertTrue(c6Matches.any { it.id == "NanoC6" })

        val pico2Matches = HardwareRegistry.searchBoards("rp2350", DeviceFamily.RASPBERRY_PI)
        assertTrue(pico2Matches.any { it.id == "Raspberry Pi Pico 2" })

        val pico2Exact = HardwareRegistry.searchBoards("2e8a:000f", DeviceFamily.RASPBERRY_PI)
        assertEquals(listOf("Raspberry Pi Pico 2"), pico2Exact.map { it.id })

        val usbJtagMatches = HardwareRegistry.searchBoards("303A:1001")
        assertTrue(usbJtagMatches.isNotEmpty())
        assertTrue(usbJtagMatches.any { it.id == "CoreS3" })

        val avrMatches = HardwareRegistry.searchBoards("arduino stk500", DeviceFamily.ARDUINO)
        assertTrue(avrMatches.any { it.id == "Arduino Uno R3" })
        assertTrue(avrMatches.any { it.id == "Arduino Mega 2560" })

        val emptyMatches = HardwareRegistry.searchBoards("nonexistent_board_xyz_999")
        assertTrue(emptyMatches.isEmpty())
    }

    @Test
    fun bleFilterAndBoardHint_workAcrossNameMacAndBondState() {
        val sampleDevices = listOf(
            DiscoveredBleDevice(
                name = "M5-Cardputer-UART",
                address = "AA:BB:CC:11:22:33",
                rssi = -48,
                bondStatus = BleBondStatus.PAIRED,
                serviceUuids = listOf("6e400001-b5a3-f393-e0a9-e50e24dcca9e"),
                matchedBoardHint = TransportManager.inferBleBoardHint("M5-Cardputer-UART", emptyList()),
            ),
            DiscoveredBleDevice(
                name = "PicoW-Sensor",
                address = "DD:EE:FF:44:55:66",
                rssi = -67,
                bondStatus = BleBondStatus.UNPAIRED,
                serviceUuids = emptyList(),
                matchedBoardHint = TransportManager.inferBleBoardHint("PicoW-Sensor", emptyList()),
            ),
        )

        val pairedOnly = TransportManager.filterBleDevices(sampleDevices, query = "", bondedOnly = true)
        assertEquals(1, pairedOnly.size)
        assertEquals("AA:BB:CC:11:22:33", pairedOnly.first().address)

        val macQuery = TransportManager.filterBleDevices(sampleDevices, query = "44:55:66", bondedOnly = false)
        assertEquals(1, macQuery.size)
        assertEquals("PicoW-Sensor", macQuery.first().name)
        assertTrue(macQuery.first().matchedBoardHint?.contains("Pico") == true)
    }

    @Test
    fun bleConnectionService_registeredInManifestAndBindsCleanly() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val serviceInfo = context.packageManager.getServiceInfo(
            ComponentName(context, BleConnectionService::class.java),
            PackageManager.GET_META_DATA,
        )
        assertNotNull(serviceInfo)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE, serviceInfo.foregroundServiceType)
        }

        val controller = Robolectric.buildService(BleConnectionService::class.java).create()
        val service = controller.get()
        val binder = service.onBind(null) as BleConnectionService.BleConnectionBinder
        assertEquals(service, binder.getService())

        val container = AppContainer.getInstance(context)
        assertTrue(container.transportManager.bleServiceRunning.value)

        service.stopManagerService()
        controller.destroy()
        assertFalse(container.transportManager.bleServiceRunning.value)
    }

    @Test
    fun multiAgentEngine_parsesAndFiltersFreeApiModelsAndExtractsCode() {
        val sampleOpenRouterJson = """
            {
              "data": [
                {
                  "id": "openai/gpt-4o",
                  "name": "GPT-4o",
                  "context_length": 128000,
                  "pricing": {"prompt": "0.000005", "completion": "0.000015"}
                },
                {
                  "id": "qwen/qwen3-coder:free",
                  "name": "Qwen 3 Coder",
                  "context_length": 65536,
                  "pricing": {"prompt": "0", "completion": "0"}
                },
                {
                  "id": "meta-llama/llama-3.3-70b-instruct:free",
                  "name": "Llama 3.3 70B Instruct",
                  "context_length": 131072,
                  "pricing": {"prompt": "0", "completion": "0"}
                }
              ]
            }
        """.trimIndent()

        val parsed = com.example.engine.MultiAgentFirmwareEngine.parseOpenAiModelsJson(
            sampleOpenRouterJson,
            com.example.engine.AiProviderPreset.OPENROUTER,
        )
        assertEquals(3, parsed.size)
        val freeModels = com.example.engine.MultiAgentFirmwareEngine.filterScannedModels(parsed, "", freeOnly = true)
        assertEquals(2, freeModels.size)
        assertTrue(freeModels.all { it.isFree && it.prefixedLabel.startsWith(":free ") })

        val qwenSearch = com.example.engine.MultiAgentFirmwareEngine.filterScannedModels(parsed, "qwen", freeOnly = true)
        assertEquals(listOf("qwen/qwen3-coder:free"), qwenSearch.map { it.id })

        val sampleGoogleJson = """
            {
              "models": [
                {
                  "name": "models/gemini-2.5-flash",
                  "displayName": "Gemini 2.5 Flash",
                  "inputTokenLimit": 1048576,
                  "supportedGenerationMethods": ["generateContent"]
                }
              ]
            }
        """.trimIndent()
        val googleModels = com.example.engine.MultiAgentFirmwareEngine.parseGoogleAiModelsJson(sampleGoogleJson)
        assertEquals(1, googleModels.size)
        assertTrue(googleModels.first().isFree)
        assertTrue(googleModels.first().prefixedLabel.startsWith(":free "))

        val board = HardwareRegistry.findById("ESP32-C6")!!
        val ctx = com.example.engine.MultiAgentFirmwareEngine.buildBoardGroundingContext(
            board,
            com.example.engine.FirmwareTargetFormat.ARDUINO_INO,
        )
        assertTrue(ctx.contains("ESP32-C6"))
        assertTrue(ctx.contains(board.appOffset))

        val markdown = """
            ### Audit Checklist
            - Pins verified
            ```cpp
            #include <Arduino.h>
            void setup() { Serial.begin(115200); }
            void loop() {}
            ```
        """.trimIndent()
        val extracted = com.example.engine.MultiAgentFirmwareEngine.extractPrimaryCodeBlock(markdown)
        assertTrue(extracted.startsWith("#include <Arduino.h>"))
        assertFalse(extracted.contains("```"))
    }

    @Test
    fun multiAgentArchitecture_specializesAgentsPerFrameworkAndOrchestratesPipeline() = kotlinx.coroutines.test.runTest {
        val esp32Board = HardwareRegistry.findById("ESP32-S3")!!
        val unoBoard = HardwareRegistry.findById("Arduino Uno R3")!!

        val espIdfPipeline = com.example.engine.agent.AgentRegistry.selectPipeline(
            com.example.engine.agent.IotFramework.ESP_IDF,
            esp32Board,
        )
        assertEquals(4, espIdfPipeline.size)
        assertTrue(espIdfPipeline[1] is com.example.engine.agent.EspIdfFirmwareAgent)

        val arduinoPipeline = com.example.engine.agent.AgentRegistry.selectPipeline(
            com.example.engine.agent.IotFramework.ARDUINO,
            unoBoard,
        )
        assertTrue(arduinoPipeline[1] is com.example.engine.agent.ArduinoFirmwareAgent)

        val mpyPipeline = com.example.engine.agent.AgentRegistry.selectPipeline(
            com.example.engine.agent.IotFramework.MICROPYTHON,
            esp32Board,
        )
        assertTrue(mpyPipeline[1] is com.example.engine.agent.MicroPythonFirmwareAgent)

        val invokedRoles = mutableListOf<String>()
        val fakeClient = com.example.engine.agent.LlmCompletionClient { _, _, _, _, sysPrompt, _ ->
            when {
                sysPrompt.startsWith("You are Agent 1") -> {
                    invokedRoles.add("ARCH")
                    "- GPIO21 SDA, GPIO22 SCL\n- I2C OLED initialized"
                }
                sysPrompt.startsWith("You are Agent 2") -> {
                    assertTrue(sysPrompt.contains("ESP-IDF"))
                    invokedRoles.add("ESP_IDF")
                    "```c\n#include \"esp_log.h\"\nvoid app_main(void) {}\n```"
                }
                sysPrompt.startsWith("You are Agent 3") -> {
                    invokedRoles.add("AUDIT")
                    "- Verified FreeRTOS watchdog\n```c\n#include \"esp_log.h\"\nvoid app_main(void) { ESP_LOGI(\"APP\", \"OK\"); }\n```"
                }
                else -> {
                    invokedRoles.add("PACKAGER")
                    "- idf.py set-target esp32s3\n- esptool flash @ 0x10000"
                }
            }
        }

        val engine = com.example.engine.MultiAgentFirmwareEngine(customCompletionClient = fakeClient)
        val observedUpdates = mutableListOf<List<com.example.engine.AgentStepOutput>>()
        val result = engine.runMultiAgentSynthesis(
            provider = com.example.engine.AiProviderPreset.OPENROUTER,
            apiKey = "test-free-key",
            baseUrlOverride = "",
            defaultModelId = "qwen/qwen3-coder:free",
            board = esp32Board,
            framework = com.example.engine.agent.IotFramework.ESP_IDF,
            format = com.example.engine.FirmwareTargetFormat.ESP_IDF_C,
            taskPrompt = "Blink status LED cleanly in a FreeRTOS task",
            onStepUpdate = { observedUpdates.add(it) },
        )

        assertTrue(result.isSuccess)
        assertEquals(listOf("ARCH", "ESP_IDF", "AUDIT", "PACKAGER"), invokedRoles)
        val body = result.getOrThrow()
        assertEquals(com.example.engine.agent.IotFramework.ESP_IDF, body.framework)
        assertTrue(body.finalCode.contains("ESP_LOGI"))
        assertTrue(body.steps.all { it.state == com.example.engine.AgentExecutionState.COMPLETED })
        assertTrue(observedUpdates.isNotEmpty())
    }

    @Test
    fun scriptLanguageDetection_detectsAllExtensions() {
        assertEquals(ScriptLanguage.MICROPYTHON, ScriptLanguage.detectFromFileName("main.py"))
        assertEquals(ScriptLanguage.ARDUINO_CPP, ScriptLanguage.detectFromFileName("blink.ino"))
        assertEquals(ScriptLanguage.BASH, ScriptLanguage.detectFromFileName("setup.sh"))
        assertEquals(ScriptLanguage.JAVASCRIPT, ScriptLanguage.detectFromFileName("agent.js"))
        assertEquals(ScriptLanguage.LUA, ScriptLanguage.detectFromFileName("init.lua"))
        assertEquals(ScriptLanguage.YAML_JSON, ScriptLanguage.detectFromFileName("node.yaml"))
    }
}
