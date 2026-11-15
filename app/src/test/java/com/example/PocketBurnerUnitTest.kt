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
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
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

    @Test
    fun usbSerialBridgeDetection_identifiesAllChipsetsAndBaudRates() {
        assertEquals(
            com.example.engine.UsbSerialBridgeType.CP210X,
            com.example.engine.TransportManager.identifyUsbSerialBridge(0x10C4, 0xEA60),
        )
        assertEquals(
            com.example.engine.UsbSerialBridgeType.CH340_CH9102,
            com.example.engine.TransportManager.identifyUsbSerialBridge(0x1A86, 0x7523),
        )
        assertEquals(
            com.example.engine.UsbSerialBridgeType.FTDI,
            com.example.engine.TransportManager.identifyUsbSerialBridge(0x0403, 0x6001),
        )
        assertEquals(
            com.example.engine.UsbSerialBridgeType.PL2303,
            com.example.engine.TransportManager.identifyUsbSerialBridge(0x067B, 0x2303),
        )
        assertEquals(
            com.example.engine.UsbSerialBridgeType.ESPRESSIF_NATIVE_USB,
            com.example.engine.TransportManager.identifyUsbSerialBridge(0x303A, 0x1001),
        )
        assertEquals(
            com.example.engine.UsbSerialBridgeType.ARDUINO_CDC,
            com.example.engine.TransportManager.identifyUsbSerialBridge(0x2341, 0x0043),
        )
        assertEquals(
            com.example.engine.UsbSerialBridgeType.RP2040_RP2350,
            com.example.engine.TransportManager.identifyUsbSerialBridge(0x2E8A, 0x0003),
        )
        assertTrue(com.example.engine.TransportManager.SUPPORTED_USB_BAUD_RATES.containsAll(listOf(9600, 115200, 921600)))
    }

    @Test
    fun launcherBridgeTrustAndBinaryDetection_validatesOriginsAndFirmwareUrls() {
        assertTrue(com.example.ui.launcher.LauncherViewModel.isTrustedBridgeOrigin("https://web.esphome.io/"))
        assertTrue(com.example.ui.launcher.LauncherViewModel.isTrustedBridgeOrigin("http://192.168.4.1/"))
        assertTrue(com.example.ui.launcher.LauncherViewModel.isTrustedBridgeOrigin("https://bmorcelli.github.io/Launcher/"))
        assertFalse(com.example.ui.launcher.LauncherViewModel.isTrustedBridgeOrigin("file:///android_asset/launcher_webui.html"))
        assertFalse(com.example.ui.launcher.LauncherViewModel.isTrustedBridgeOrigin("http://launcher.local/update"))
        assertFalse(com.example.ui.launcher.LauncherViewModel.isTrustedBridgeOrigin("https://untrusted-site.example.com/"))

        assertTrue(
            com.example.ui.launcher.LauncherViewModel.isBinaryFirmwareUrl(
                "https://github.com/bmorcelli/Launcher/releases/download/2.5.0/Launcher-m5stack-cardputer.bin",
                null,
                null,
            )
        )
        assertTrue(
            com.example.ui.launcher.LauncherViewModel.isBinaryFirmwareUrl(
                "https://example.org/pico.uf2",
                null,
                "application/octet-stream",
            )
        )
        assertFalse(
            com.example.ui.launcher.LauncherViewModel.isBinaryFirmwareUrl(
                "https://github.com/bmorcelli/Launcher",
                null,
                "text/html",
            )
        )
    }

    @Test
    fun wifiOtaConfig_buildsCleanHttpHttpsUrlsAndParsesLauncherTargets() {
        val httpDefault = com.example.engine.WifiOtaTargetConfig(
            useHttps = false,
            host = "http://192.168.4.1/",
            port = 80,
            endpointPath = "update",
        )
        assertEquals("http://192.168.4.1/update", httpDefault.buildTargetUrl())

        val httpsCustom = com.example.engine.WifiOtaTargetConfig(
            useHttps = true,
            host = "esp32-ota.local",
            port = 8443,
            endpointPath = "/ota/upload",
        )
        assertEquals("https://esp32-ota.local:8443/ota/upload", httpsCustom.buildTargetUrl())

        val fallbackParsed = com.example.ui.launcher.LauncherViewModel.parseTargetConfig(
            rawUrl = "file:///android_asset/launcher_webui.html",
            protocol = com.example.engine.OtaUploadProtocol.ESP_MULTIPART_UPDATE,
            fallbackHost = "192.168.1.55",
        )
        assertEquals("192.168.1.55", fallbackParsed.host)
        assertEquals("/update", fallbackParsed.endpointPath)
        assertFalse(fallbackParsed.useHttps)

        val directHttpsParsed = com.example.ui.launcher.LauncherViewModel.parseTargetConfig(
            rawUrl = "https://192.168.1.88:8443/upload",
            protocol = com.example.engine.OtaUploadProtocol.LAUNCHER_SD_UPLOAD,
        )
        assertTrue(directHttpsParsed.useHttps)
        assertEquals("192.168.1.88", directHttpsParsed.host)
        assertEquals(8443, directHttpsParsed.port)
        assertEquals("/upload", directHttpsParsed.endpointPath)
    }

    @Test
    fun wifiOtaUploader_probesAndPushesFirmwareOverLoopbackHttpServer() = kotlinx.coroutines.test.runTest {
        var receivedMd5Header: String? = null
        var receivedAuthHeader: String? = null
        var receivedOtaPassword: String? = null
        var receivedBodyBytes = 0

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/update") { exchange ->
            if (exchange.requestMethod == "GET") {
                val ok = "OTA Endpoint Ready".toByteArray()
                exchange.sendResponseHeaders(200, ok.size.toLong())
                exchange.responseBody.use { it.write(ok) }
            } else {
                receivedMd5Header = exchange.requestHeaders.getFirst("X-Firmware-MD5")
                receivedAuthHeader = exchange.requestHeaders.getFirst("Authorization")
                receivedOtaPassword = exchange.requestHeaders.getFirst("X-OTA-Password")
                receivedBodyBytes = exchange.requestBody.readBytes().size
                val reply = "Flash OK".toByteArray()
                exchange.sendResponseHeaders(200, reply.size.toLong())
                exchange.responseBody.use { it.write(reply) }
            }
        }
        server.createContext("/reject") { exchange ->
            exchange.requestBody.readBytes()
            val err = "Unauthorized".toByteArray()
            exchange.sendResponseHeaders(401, err.size.toLong())
            exchange.responseBody.use { it.write(err) }
        }
        server.start()

        val tempBin = File.createTempFile("pocketburner_ota_test", ".bin").apply {
            writeBytes(ByteArray(24 * 1024) { idx -> (idx and 0xFF).toByte() })
            deleteOnExit()
        }

        try {
            val port = server.address.port
            val uploader = com.example.engine.WifiOtaUploader()
            val (expectedMd5, expectedSha) = com.example.engine.WifiOtaUploader.computeDigests(tempBin)

            val config = com.example.engine.WifiOtaTargetConfig(
                useHttps = false,
                host = "127.0.0.1",
                port = port,
                endpointPath = "/update",
                protocol = com.example.engine.OtaUploadProtocol.ESP_MULTIPART_UPDATE,
                otaPassword = "esp-ota-secret",
            )

            val probe = uploader.probeEndpoint(config)
            assertTrue(probe.isSuccess)

            val progressUpdates = mutableListOf<Long>()
            val push = uploader.pushFirmwareBinary(tempBin, config) { sent, _, _ ->
                progressUpdates.add(sent)
            }
            assertTrue("Expected OTA push success, got: ${push.exceptionOrNull()}", push.isSuccess)
            val outcome = push.getOrThrow()
            assertEquals(200, outcome.httpCode)
            assertEquals(tempBin.length(), outcome.bytesSent)
            assertEquals(expectedMd5, outcome.md5Hex)
            assertEquals(expectedSha, outcome.sha256Hex)
            assertEquals("Flash OK", outcome.serverResponse)
            assertEquals(expectedMd5, receivedMd5Header)
            assertTrue(receivedAuthHeader?.startsWith("Basic ") == true)
            assertEquals("esp-ota-secret", receivedOtaPassword)
            assertTrue(receivedBodyBytes > tempBin.length())
            assertTrue(progressUpdates.any { it == tempBin.length() })

            val failConfig = config.copy(
                endpointPath = "/reject",
                protocol = com.example.engine.OtaUploadProtocol.ARDUINO_RAW_BINARY,
            )
            val failPush = uploader.pushFirmwareBinary(tempBin, failConfig) { _, _, _ -> }
            assertTrue(failPush.isFailure)
            assertTrue(failPush.exceptionOrNull()?.message?.contains("401") == true)
        } finally {
            server.stop(0)
            tempBin.delete()
        }
    }

    @Test
    fun bmorcelliFullCatalog_parsesAllAssetsAndJsonFeedsWithoutTruncation() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val container = AppContainer.getInstance(context)
        val engine = container.firmwareCatalogEngine

        assertTrue(engine.defaultRepositories.any { it.id == "repo_bmorcelli_catalog" })

        val assetsArray = (1..48).joinToString(",") { idx ->
            val boardSuffix = if (idx == 48) "m5stack-cardputer-adv" else "board-$idx"
            """{"name":"Launcher-$boardSuffix.bin","size":655360,"browser_download_url":"https://example.org/Launcher-$boardSuffix.bin"}"""
        }
        val releaseJson = """
            {
              "tag_name": "2.6.0",
              "body": "M5Launcher Release",
              "author": {"login": "bmorcelli"},
              "assets": [$assetsArray]
            }
        """.trimIndent()
        val parsedRelease = engine.parseGitHubReleaseFeed(releaseJson, "repo_bmorcelli_launcher", "M5Launcher (bmorcelli/Launcher)")
        assertEquals(48, parsedRelease.size)
        assertTrue(parsedRelease.last().deviceTagsCsv.contains("Cardputer ADV"))

        val m5JsonFwFeed = """
            [
              {
                "name": "Bruce",
                "author": "pr3y",
                "board": "M5Stack Cardputer",
                "category": "Security Research",
                "version": "1.10",
                "cover": "bruce_cover.png",
                "files": [
                  {"address": "0x1000", "path": "bootloader.bin"},
                  {"address": "0x10000", "path": "bruce_cardputer.bin"}
                ]
              }
            ]
        """.trimIndent()
        val parsedCatalog = engine.parseM5BurnerFeed(m5JsonFwFeed, "repo_bmorcelli_catalog")
        assertEquals(1, parsedCatalog.size)
        assertEquals("Bruce", parsedCatalog.first().title)
        assertTrue(parsedCatalog.first().deviceTagsCsv.contains("Cardputer"))
        assertEquals("0x10000", parsedCatalog.first().flashOffset)
        assertEquals("https://m5burner-cdn.m5stack.com/firmware/bruce_cardputer.bin", parsedCatalog.first().downloadUrl)
        assertEquals("https://m5burner-cdn.m5stack.com/cover/bruce_cover.png", parsedCatalog.first().coverImageUrl)
    }

    @Test
    fun m5BurnerHkCdnApi_configuredAndParsesCdnFirmwareEntries() = kotlinx.coroutines.test.runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val container = AppContainer.getInstance(context)
        val engine = container.firmwareCatalogEngine

        val m5Repo = engine.defaultRepositories.first { it.id == "repo_m5burner" }
        assertEquals("http://m5burner-api-fc-hk-cdn.m5stack.com/api/firmware", m5Repo.url)

        val oldRepo = m5Repo.copy(url = "https://burner.m5stack.com/api/v1/firmware")
        container.database.repoSourceDao().upsert(oldRepo)
        engine.ensureDefaultReposSeeded()
        val migrated = container.database.repoSourceDao().getAll().first { it.id == "repo_m5burner" }
        assertEquals("http://m5burner-api-fc-hk-cdn.m5stack.com/api/firmware", migrated.url)

        val cdnPayload = """
            {
              "rows": [
                {
                  "fid": "fw_9001",
                  "name": "M5StickC Plus2 UIFlow2",
                  "author": "M5Stack",
                  "category": ["IoT & Automation"],
                  "device": "StickC-Plus2",
                  "version": "2.1.4",
                  "download": "firmware/uiflow2_stickc_plus2.bin",
                  "cover": "cover/uiflow2.png",
                  "keywords": ["UIFlow2", "MicroPython"]
                }
              ]
            }
        """.trimIndent()
        val items = engine.parseM5BurnerFeed(cdnPayload, "repo_m5burner")
        assertEquals(1, items.size)
        assertEquals("repo_m5burner_fw_9001", items.first().id)
        assertEquals("https://m5burner-cdn.m5stack.com/firmware/uiflow2_stickc_plus2.bin", items.first().downloadUrl)
        assertEquals("https://m5burner-cdn.m5stack.com/cover/uiflow2.png", items.first().coverImageUrl)
        assertTrue(items.first().description.contains("Tags: UIFlow2, MicroPython"))
    }
}
