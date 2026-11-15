package com.example.engine.agent

import com.example.engine.AgentRole
import com.example.engine.AiProviderPreset
import com.example.engine.FirmwareTargetFormat
import com.example.hardware.HardwareBoardSpec

/**
 * Supported IoT development frameworks that specialized [FirmwareAgent] implementations target.
 */
enum class IotFramework(
    val id: String,
    val displayName: String,
    val badge: String,
    val defaultFormat: FirmwareTargetFormat,
    val toolchainSummary: String,
) {
    ARDUINO(
        id = "arduino",
        displayName = "Arduino C++",
        badge = "Arduino",
        defaultFormat = FirmwareTargetFormat.ARDUINO_INO,
        toolchainSummary = "arduino-cli / PlatformIO (framework = arduino), Wire.h, SPI.h, FreeRTOS/millis()",
    ),
    ESP_IDF(
        id = "esp_idf",
        displayName = "ESP-IDF (FreeRTOS)",
        badge = "ESP-IDF",
        defaultFormat = FirmwareTargetFormat.ESP_IDF_C,
        toolchainSummary = "Espressif IoT Development Framework v5.x, idf.py, CMakeLists.txt, FreeRTOS, esp_log",
    ),
    MICROPYTHON(
        id = "micropython",
        displayName = "MicroPython",
        badge = "MicroPython",
        defaultFormat = FirmwareTargetFormat.MICROPYTHON,
        toolchainSummary = "MicroPython machine/network/ubluetooth/uasyncio, mpremote & PocketBurner Raw REPL",
    ),
    RASPBERRY_PI(
        id = "raspberry_pi",
        displayName = "Raspberry Pi OS",
        badge = "RPi Linux",
        defaultFormat = FirmwareTargetFormat.RASPBERRY_PI_PY,
        toolchainSummary = "Python 3 (gpiozero, lgpio, smbus2, bleak), POSIX Bash & systemd services",
    ),
    ESPHOME(
        id = "esphome",
        displayName = "ESPHome YAML",
        badge = "ESPHome",
        defaultFormat = FirmwareTargetFormat.ESPHOME_YAML,
        toolchainSummary = "Declarative ESPHome sensor/switch/wifi/ota/api YAML node configuration",
    );

    companion object {
        fun fromFormat(format: FirmwareTargetFormat): IotFramework = when (format) {
            FirmwareTargetFormat.ARDUINO_INO -> ARDUINO
            FirmwareTargetFormat.ESP_IDF_C -> ESP_IDF
            FirmwareTargetFormat.MICROPYTHON -> MICROPYTHON
            FirmwareTargetFormat.RASPBERRY_PI_PY, FirmwareTargetFormat.RASPBERRY_PI_SH -> RASPBERRY_PI
            FirmwareTargetFormat.ESPHOME_YAML -> ESPHOME
        }
    }
}

/**
 * Shared immutable blackboard context passed between specialized [FirmwareAgent] stages.
 */
data class AgentExecutionContext(
    val taskId: String,
    val board: HardwareBoardSpec,
    val framework: IotFramework,
    val format: FirmwareTargetFormat,
    val userObjective: String,
    val provider: AiProviderPreset,
    val apiKey: String,
    val baseUrl: String,
    val modelId: String,
    val boardGroundingText: String,
    val priorOutputs: Map<AgentRole, String> = emptyMap(),
    val latestDraftCode: String = "",
)

/**
 * Output artifact produced by a single [FirmwareAgent] execution stage.
 */
data class AgentArtifact(
    val agentId: String,
    val agentDisplayName: String,
    val role: AgentRole,
    val frameworkBadge: String,
    val summary: String,
    val rawOutput: String,
    val extractedCode: String? = null,
    val diagnostics: List<String> = emptyList(),
)

/**
 * Pluggable LLM completion transport so [FirmwareAgent] orchestration is testable
 * offline with fake clients and backed by OkHttp in production.
 */
fun interface LlmCompletionClient {
    suspend fun complete(
        provider: AiProviderPreset,
        apiKey: String,
        baseUrl: String,
        modelId: String,
        systemPrompt: String,
        userPrompt: String,
    ): String
}

/**
 * Core Multi-Agent contract implemented by all role- and framework-specialized agents
 * in the PocketBurner firmware synthesis pipeline.
 */
interface FirmwareAgent {
    val agentId: String
    val displayName: String
    val role: AgentRole
    val supportedFrameworks: Set<IotFramework>

    fun supports(framework: IotFramework, board: HardwareBoardSpec): Boolean =
        framework in supportedFrameworks

    fun frameworkSpecializationTag(framework: IotFramework): String

    fun buildSystemPrompt(context: AgentExecutionContext): String

    fun buildUserPrompt(context: AgentExecutionContext): String

    suspend fun execute(
        context: AgentExecutionContext,
        llmClient: LlmCompletionClient,
        onStatusDetail: (String) -> Unit = {},
    ): AgentArtifact
}
