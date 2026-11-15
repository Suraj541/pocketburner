package com.example.engine.agent

import com.example.engine.AgentRole
import com.example.engine.MultiAgentFirmwareEngine
import com.example.hardware.DeviceFamily
import com.example.hardware.HardwareBoardSpec

/**
 * Abstract base implementing common prompt formatting, LLM execution, and diagnostic extraction
 * shared across specialized [FirmwareAgent] subclasses.
 */
abstract class BaseFirmwareAgent : FirmwareAgent {

    protected open fun parseDiagnostics(rawOutput: String, context: AgentExecutionContext): List<String> {
        val bullets = rawOutput.lines()
            .map { it.trim() }
            .filter { it.startsWith("- ") || it.startsWith("* ") }
            .map { it.removePrefix("- ").removePrefix("* ").trim() }
            .filter { it.length in 6..120 }
            .take(4)
        return if (bullets.isNotEmpty()) {
            bullets
        } else {
            listOf("${context.framework.badge} target: ${context.board.id} (${context.board.chip})")
        }
    }

    override suspend fun execute(
        context: AgentExecutionContext,
        llmClient: LlmCompletionClient,
        onStatusDetail: (String) -> Unit,
    ): AgentArtifact {
        onStatusDetail("Preparing ${frameworkSpecializationTag(context.framework)} prompts for ${context.board.id}…")
        val sysPrompt = buildSystemPrompt(context)
        val usrPrompt = buildUserPrompt(context)

        onStatusDetail("Invoking ${context.modelId} (${frameworkSpecializationTag(context.framework)})…")
        val responseText = llmClient.complete(
            provider = context.provider,
            apiKey = context.apiKey,
            baseUrl = context.baseUrl,
            modelId = context.modelId,
            systemPrompt = sysPrompt,
            userPrompt = usrPrompt,
        )

        onStatusDetail("Validating ${role.badge} output artifacts…")
        val codeBlock = if (role == AgentRole.ENGINEER || role == AgentRole.AUDITOR) {
            MultiAgentFirmwareEngine.extractPrimaryCodeBlock(responseText).takeIf { it.isNotBlank() }
        } else {
            null
        }
        val diagnostics = parseDiagnostics(responseText, context)
        val firstLineSummary = responseText.lines()
            .firstOrNull { it.isNotBlank() && !it.trim().startsWith("```") }
            ?.trim()
            ?.take(100)
            ?: "$displayName completed"

        return AgentArtifact(
            agentId = agentId,
            agentDisplayName = displayName,
            role = role,
            frameworkBadge = frameworkSpecializationTag(context.framework),
            summary = firstLineSummary,
            rawOutput = responseText,
            extractedCode = codeBlock,
            diagnostics = diagnostics,
        )
    }
}

/**
 * Agent 1: Hardware & Bus Architect across all supported IoT frameworks.
 */
class HardwareArchitectAgent : BaseFirmwareAgent() {
    override val agentId: String = "agent_hardware_architect"
    override val displayName: String = "Hardware Architect Agent"
    override val role: AgentRole = AgentRole.ARCHITECT
    override val supportedFrameworks: Set<IotFramework> = IotFramework.entries.toSet()

    override fun frameworkSpecializationTag(framework: IotFramework): String =
        "Arch • ${framework.badge}"

    override fun buildSystemPrompt(context: AgentExecutionContext): String = """
        You are Agent 1 (Embedded Hardware Architect) specialized for ${context.framework.displayName}.
        Toolchain: ${context.framework.toolchainSummary}
        Analyze the exact target board specification and design a verified hardware architecture plan:
        1. Pin & Bus Allocation: exact GPIO pins for I2C, SPI, UART, PWM, ADC, avoiding strapping/flash/USB pins.
        2. Framework SDK / Library Dependencies tailored to ${context.framework.displayName} (${context.format.label}).
        3. Power, logic level (${context.board.logicVoltage}), flash (${context.board.flashSizeLabel}), and RAM (${context.board.ramLabel}) budget.
        Output concise Markdown bullet lists. Do not write full source code yet.
    """.trimIndent()

    override fun buildUserPrompt(context: AgentExecutionContext): String = """
        TARGET BOARD SPECIFICATION:
        ${context.boardGroundingText}

        IOT WORK OBJECTIVE:
        ${context.userObjective}
    """.trimIndent()
}

/**
 * Agent 2 (Specialization A): Espressif ESP-IDF FreeRTOS C/C++ Firmware Agent.
 */
class EspIdfFirmwareAgent : BaseFirmwareAgent() {
    override val agentId: String = "agent_engineer_espidf"
    override val displayName: String = "ESP-IDF FreeRTOS Agent"
    override val role: AgentRole = AgentRole.ENGINEER
    override val supportedFrameworks: Set<IotFramework> = setOf(IotFramework.ESP_IDF)

    override fun supports(framework: IotFramework, board: HardwareBoardSpec): Boolean =
        framework == IotFramework.ESP_IDF &&
            (board.family == DeviceFamily.ESP_IOT || board.family == DeviceFamily.M5STACK || board.chip.contains("ESP", ignoreCase = true))

    override fun frameworkSpecializationTag(framework: IotFramework): String = "ESP-IDF v5"

    override fun buildSystemPrompt(context: AgentExecutionContext): String = """
        You are Agent 2 (Senior ESP-IDF Embedded Firmware Agent) targeting ${context.board.displayName} (${context.board.chip}).
        Follow idiomatic ESP-IDF v5.x practices:
        - Include proper ESP-IDF headers (`freertos/FreeRTOS.h`, `freertos/task.h`, `esp_log.h`, `esp_err.h`, `driver/gpio.h`, `nvs_flash.h`).
        - Wrap hardware driver calls with `ESP_ERROR_CHECK(...)` and define static `TAG`.
        - Structure execution cleanly inside `void app_main(void)` spawning modular FreeRTOS tasks (`xTaskCreate`).
        - Strictly obey Agent 1's GPIO pin map. Output ONE complete fenced code block (```c ... ```).
    """.trimIndent()

    override fun buildUserPrompt(context: AgentExecutionContext): String = """
        BOARD: ${context.boardGroundingText}
        TASK: ${context.userObjective}
        AGENT 1 ARCHITECTURE PLAN:
        ${context.priorOutputs[AgentRole.ARCHITECT].orEmpty()}
    """.trimIndent()
}

/**
 * Agent 2 (Specialization B): Arduino C++ Framework Agent (ESP32, M5Stack, AVR, SAMD, RP2040).
 */
class ArduinoFirmwareAgent : BaseFirmwareAgent() {
    override val agentId: String = "agent_engineer_arduino"
    override val displayName: String = "Arduino C++ Firmware Agent"
    override val role: AgentRole = AgentRole.ENGINEER
    override val supportedFrameworks: Set<IotFramework> = setOf(IotFramework.ARDUINO)

    override fun frameworkSpecializationTag(framework: IotFramework): String = "Arduino C++"

    override fun buildSystemPrompt(context: AgentExecutionContext): String = """
        You are Agent 2 (Senior Arduino C++ Firmware Agent) targeting ${context.board.displayName} (${context.board.chip}).
        Follow idiomatic Arduino framework rules for ${context.board.architecture}:
        - Start with `<Arduino.h>`, explicit `constexpr` GPIO constants matching Agent 1, and Serial initialization at ${context.board.defaultBaud} baud.
        - Use non-blocking `millis()` scheduling instead of long `delay()` loops so Wi-Fi/BLE watchdogs remain responsive.
        - Avoid dynamic `String` heap fragmentation on RAM-constrained AVR/MCU targets; handle peripheral init failures gracefully.
        - Output ONE complete compilable sketch inside a fenced block (```cpp ... ```).
    """.trimIndent()

    override fun buildUserPrompt(context: AgentExecutionContext): String = """
        BOARD: ${context.boardGroundingText}
        TASK: ${context.userObjective}
        AGENT 1 ARCHITECTURE PLAN:
        ${context.priorOutputs[AgentRole.ARCHITECT].orEmpty()}
    """.trimIndent()
}

/**
 * Agent 2 (Specialization C): MicroPython Framework Agent (ESP32, M5Stack, RP2040/RP2350 Pico).
 */
class MicroPythonFirmwareAgent : BaseFirmwareAgent() {
    override val agentId: String = "agent_engineer_micropython"
    override val displayName: String = "MicroPython Firmware Agent"
    override val role: AgentRole = AgentRole.ENGINEER
    override val supportedFrameworks: Set<IotFramework> = setOf(IotFramework.MICROPYTHON)

    override fun frameworkSpecializationTag(framework: IotFramework): String = "MicroPython"

    override fun buildSystemPrompt(context: AgentExecutionContext): String = """
        You are Agent 2 (Senior MicroPython Embedded Agent) targeting ${context.board.displayName} (${context.board.chip}).
        Follow idiomatic MicroPython embedded practices:
        - Use `machine` (`Pin`, `I2C`, `SPI`, `ADC`, `PWM`, `WDT`), `time.ticks_ms()`, `gc`, and `uasyncio` / `ubluetooth` where applicable.
        - Pre-allocate buffers where practical and avoid `machine.Pin` allocation inside hard IRQ callbacks.
        - Include robust `try/except` recovery so REPL/Raw-REPL deployments never brick on transient sensor timeouts.
        - Output ONE complete `main.py` inside a fenced block (```python ... ```).
    """.trimIndent()

    override fun buildUserPrompt(context: AgentExecutionContext): String = """
        BOARD: ${context.boardGroundingText}
        TASK: ${context.userObjective}
        AGENT 1 ARCHITECTURE PLAN:
        ${context.priorOutputs[AgentRole.ARCHITECT].orEmpty()}
    """.trimIndent()
}

/**
 * Agent 2 (Specialization D): Raspberry Pi OS Linux Python / Bash Automation Agent.
 */
class RaspberryPiAgent : BaseFirmwareAgent() {
    override val agentId: String = "agent_engineer_rpi"
    override val displayName: String = "Raspberry Pi OS Agent"
    override val role: AgentRole = AgentRole.ENGINEER
    override val supportedFrameworks: Set<IotFramework> = setOf(IotFramework.RASPBERRY_PI)

    override fun frameworkSpecializationTag(framework: IotFramework): String =
        if (framework.defaultFormat == com.example.engine.FirmwareTargetFormat.RASPBERRY_PI_SH) "RPi Bash" else "RPi Python"

    override fun buildSystemPrompt(context: AgentExecutionContext): String = """
        You are Agent 2 (Raspberry Pi Embedded Linux Systems Agent) targeting ${context.board.displayName} (${context.board.chip}).
        Write production-ready ${context.format.label} code:
        - Use standard Raspberry Pi OS APIs (`gpiozero`, `lgpio`, `smbus2`, `asyncio`, `signal`, or hardened Bash `set -euo pipefail`).
        - Handle SIGINT/SIGTERM gracefully for clean GPIO release & systemd compatibility.
        - Output ONE complete script inside ```${context.format.languageHint} ... ```.
    """.trimIndent()

    override fun buildUserPrompt(context: AgentExecutionContext): String = """
        BOARD: ${context.boardGroundingText}
        TASK: ${context.userObjective}
        AGENT 1 ARCHITECTURE PLAN:
        ${context.priorOutputs[AgentRole.ARCHITECT].orEmpty()}
    """.trimIndent()
}

/**
 * Agent 2 (Specialization E): Declarative ESPHome YAML Firmware Agent.
 */
class EspHomeFirmwareAgent : BaseFirmwareAgent() {
    override val agentId: String = "agent_engineer_esphome"
    override val displayName: String = "ESPHome YAML Agent"
    override val role: AgentRole = AgentRole.ENGINEER
    override val supportedFrameworks: Set<IotFramework> = setOf(IotFramework.ESPHOME)

    override fun frameworkSpecializationTag(framework: IotFramework): String = "ESPHome YAML"

    override fun buildSystemPrompt(context: AgentExecutionContext): String = """
        You are Agent 2 (ESPHome Firmware Architect) targeting ${context.board.displayName} (${context.board.chip}).
        Write a valid ESPHome YAML configuration using Agent 1's exact GPIO pins, `esp32`/`esp8266` board target,
        `logger`, `api`, `ota`, `wifi`, and requested hardware sensor/display/switch blocks inside ```yaml ... ```.
    """.trimIndent()

    override fun buildUserPrompt(context: AgentExecutionContext): String = """
        BOARD: ${context.boardGroundingText}
        TASK: ${context.userObjective}
        AGENT 1 ARCHITECTURE PLAN:
        ${context.priorOutputs[AgentRole.ARCHITECT].orEmpty()}
    """.trimIndent()
}

/**
 * Agent 3: Framework-Aware Code & Pinout Safety Auditor.
 */
class FrameworkAuditorAgent : BaseFirmwareAgent() {
    override val agentId: String = "agent_framework_auditor"
    override val displayName: String = "Framework Auditor Agent"
    override val role: AgentRole = AgentRole.AUDITOR
    override val supportedFrameworks: Set<IotFramework> = IotFramework.entries.toSet()

    override fun frameworkSpecializationTag(framework: IotFramework): String =
        "Audit • ${framework.badge}"

    override fun buildSystemPrompt(context: AgentExecutionContext): String {
        val frameworkAuditFocus = when (context.framework) {
            IotFramework.ESP_IDF -> "Check FreeRTOS task stack sizes, ESP_ERROR_CHECK guards, IRAM safety, and esp_task_wdt feeding."
            IotFramework.ARDUINO -> "Check blocking delay() calls, ISR IRAM_ATTR rules, Wire/SPI begin pins, and RAM/String safety."
            IotFramework.MICROPYTHON -> "Check IRQ memory allocation, ticks_diff usage, exception resilience, and GC pressure."
            IotFramework.RASPBERRY_PI -> "Check GPIO cleanup on SIGTERM/SIGINT, permissions, non-blocking I/O, and error exit codes."
            IotFramework.ESPHOME -> "Check YAML indentation, pin conflicts, PSRAM board variant, and OTA fallback safety."
        }
        return """
            You are Agent 3 (Hardware Verification & Framework Security Auditor) for ${context.framework.displayName}.
            Hardware constraints for ${context.board.displayName} (${context.board.chip}, ${context.board.logicVoltage}):
            - $frameworkAuditFocus
            - Confirm no boot/strapping/USB-JTAG pin conflicts with ${context.board.gpioPinoutSummary}.
            First output a concise bulleted Audit Checklist (starting each finding with `- `), then ALWAYS output the final complete hardened source code inside ONE single fenced block (```${context.format.languageHint} ... ```).
        """.trimIndent()
    }

    override fun buildUserPrompt(context: AgentExecutionContext): String = """
        BOARD: ${context.boardGroundingText}
        OBJECTIVE: ${context.userObjective}
        DRAFT CODE FROM ENGINEER AGENT:
        ```${context.format.languageHint}
        ${context.latestDraftCode}
        ```
    """.trimIndent()
}

/**
 * Agent 4: Build Manifest & PocketBurner Deployment Packager.
 */
class BuildPackagerAgent : BaseFirmwareAgent() {
    override val agentId: String = "agent_build_packager"
    override val displayName: String = "Build & Flash Packager"
    override val role: AgentRole = AgentRole.PACKAGER
    override val supportedFrameworks: Set<IotFramework> = IotFramework.entries.toSet()

    override fun frameworkSpecializationTag(framework: IotFramework): String =
        "Packager • ${framework.badge}"

    override fun buildSystemPrompt(context: AgentExecutionContext): String {
        val cliInstruction = when (context.framework) {
            IotFramework.ESP_IDF -> "Include `idf.py set-target`, `sdkconfig` notes, `CMakeLists.txt` component requirements, and `esptool` binary offsets."
            IotFramework.ARDUINO -> "Include exact `platformio.ini` / `arduino-cli compile` FQBN flags, `lib_deps`, and upload baud."
            IotFramework.MICROPYTHON -> "Include `mpremote cp`, MicroPython module installs (`mip`), and PocketBurner Raw-REPL push steps."
            IotFramework.RASPBERRY_PI -> "Include `pip` packages, `chmod +x`, and a ready-to-use `systemd` unit file for autostart."
            IotFramework.ESPHOME -> "Include `esphome run` compile & OTA flash commands."
        }
        return """
            You are Agent 4 (Build & PocketBurner Deployment Packager) for ${context.framework.displayName}.
            Board flash parameters: Protocol=${context.board.protocol.label}, AppOffset=${context.board.appOffset}, Mode=${context.board.flashMode}, Baud=${context.board.defaultBaud}.
            $cliInstruction
            Also provide 2-3 verification commands for PocketBurner Terminal. Keep it structured cleanly in Markdown.
        """.trimIndent()
    }

    override fun buildUserPrompt(context: AgentExecutionContext): String = """
        BOARD: ${context.board.displayName} (${context.board.chip})
        FRAMEWORK: ${context.framework.displayName} (${context.format.label})
        USER OBJECTIVE: ${context.userObjective}
        FINAL CODE SIZE: ${context.latestDraftCode.lines().size} lines
    """.trimIndent()
}

/**
 * Central registry assembling framework-specialized [FirmwareAgent] pipelines.
 */
object AgentRegistry {
    val allEngineerAgents: List<FirmwareAgent> = listOf(
        EspIdfFirmwareAgent(),
        ArduinoFirmwareAgent(),
        MicroPythonFirmwareAgent(),
        RaspberryPiAgent(),
        EspHomeFirmwareAgent(),
    )

    fun resolveEngineerAgent(framework: IotFramework, board: HardwareBoardSpec): FirmwareAgent {
        return allEngineerAgents.firstOrNull { it.supports(framework, board) }
            ?: allEngineerAgents.firstOrNull { framework in it.supportedFrameworks }
            ?: ArduinoFirmwareAgent()
    }

    fun selectPipeline(framework: IotFramework, board: HardwareBoardSpec): List<FirmwareAgent> = listOf(
        HardwareArchitectAgent(),
        resolveEngineerAgent(framework, board),
        FrameworkAuditorAgent(),
        BuildPackagerAgent(),
    )
}
