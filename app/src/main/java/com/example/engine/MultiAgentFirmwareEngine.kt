package com.example.engine

import com.example.engine.agent.AgentExecutionContext
import com.example.engine.agent.AgentRegistry
import com.example.engine.agent.IotFramework
import com.example.engine.agent.LlmCompletionClient
import com.example.hardware.HardwareBoardSpec
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

enum class AiProviderPreset(
    val id: String,
    val label: String,
    val defaultBaseUrl: String,
    val description: String,
    val requiresKeyForScan: Boolean,
) {
    OPENROUTER(
        id = "OPENROUTER",
        label = "OpenRouter (:free)",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        description = "Scans live OpenRouter catalog for :free community & frontier models using your free API key.",
        requiresKeyForScan = false,
    ),
    GROQ(
        id = "GROQ",
        label = "Groq Free API",
        defaultBaseUrl = "https://api.groq.com/openai/v1",
        description = "Ultra-fast LPU inference with free developer API keys via /models.",
        requiresKeyForScan = true,
    ),
    GOOGLE_AI_STUDIO(
        id = "GOOGLE_AI_STUDIO",
        label = "Gemini AI Studio (Free Key)",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
        description = "Direct Gemini API free-tier endpoints scanned with your AI Studio key.",
        requiresKeyForScan = true,
    ),
    CUSTOM_OPENAI(
        id = "CUSTOM_OPENAI",
        label = "Custom OpenAI Endpoint",
        defaultBaseUrl = "http://192.168.1.10:11434/v1",
        description = "Any OpenAI-compatible gateway (Together, Mistral, NVIDIA NIM, Ollama, LM Studio).",
        requiresKeyForScan = false,
    );

    companion object {
        fun fromId(id: String?): AiProviderPreset =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: OPENROUTER
    }
}

data class ScannedAiModel(
    val id: String,
    val displayName: String,
    val providerId: String,
    val isFree: Boolean,
    val contextLength: Int = 0,
    val pricingNote: String = if (isFree) ":free" else "paid",
) {
    val prefixedLabel: String
        get() = if (isFree) ":free $displayName" else displayName
}

enum class FirmwareTargetFormat(
    val label: String,
    val defaultFileName: String,
    val languageHint: String,
) {
    ARDUINO_INO("Arduino C++ (.ino)", "firmware_app.ino", "cpp"),
    MICROPYTHON("MicroPython (main.py)", "main.py", "python"),
    ESP_IDF_C("ESP-IDF C (main.c)", "main.c", "c"),
    RASPBERRY_PI_PY("Raspberry Pi Python (.py)", "iot_worker.py", "python"),
    RASPBERRY_PI_SH("Raspberry Pi Setup (.sh)", "setup_iot.sh", "bash"),
    ESPHOME_YAML("ESPHome Config (.yaml)", "iot_node.yaml", "yaml"),
}

enum class AgentRole(
    val title: String,
    val badge: String,
    val mission: String,
) {
    ARCHITECT(
        title = "Agent 1 • Hardware Architect",
        badge = "ARCH",
        mission = "Maps GPIOs, buses (I2C/SPI/UART/PWM), voltage safety & libraries for the exact board.",
    ),
    ENGINEER(
        title = "Agent 2 • Framework Specialist",
        badge = "CODE",
        mission = "Synthesizes complete non-blocking firmware/code honoring board pins and framework APIs.",
    ),
    AUDITOR(
        title = "Agent 3 • Framework Auditor",
        badge = "AUDIT",
        mission = "Inspects strapping pins, flash offsets, memory limits & emits hardened final code.",
    ),
    PACKAGER(
        title = "Agent 4 • Flash & Deploy Packager",
        badge = "BUILD",
        mission = "Generates esptool / idf.py / arduino-cli / mpremote / RPi manifest for Nano & Burner.",
    ),
}

enum class AgentExecutionState {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
}

data class AgentStepOutput(
    val role: AgentRole,
    val agentId: String = "",
    val agentDisplayName: String = role.title,
    val frameworkBadge: String = "",
    val phaseDetail: String = "",
    val modelId: String,
    val state: AgentExecutionState = AgentExecutionState.PENDING,
    val outputText: String = "",
    val diagnostics: List<String> = emptyList(),
    val durationMs: Long = 0L,
    val errorMessage: String? = null,
)

data class MultiAgentSynthesisResult(
    val taskId: String,
    val board: HardwareBoardSpec,
    val framework: IotFramework,
    val format: FirmwareTargetFormat,
    val taskPrompt: String,
    val steps: List<AgentStepOutput>,
    val pinoutPlan: String,
    val finalCode: String,
    val auditSummary: String,
    val buildAndFlashInstructions: String,
    val suggestedFilename: String,
)

class MultiAgentFirmwareEngine(
    private val customCompletionClient: LlmCompletionClient? = null,
) {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(75, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val defaultCompletionClient = LlmCompletionClient { provider, apiKey, baseUrl, modelId, systemPrompt, userPrompt ->
        callChatCompletion(
            provider = provider,
            apiKey = apiKey,
            baseUrl = baseUrl,
            modelId = modelId,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
        )
    }

    suspend fun scanAvailableModels(
        provider: AiProviderPreset,
        apiKey: String,
        baseUrlOverride: String,
    ): Result<List<ScannedAiModel>> = withContext(Dispatchers.IO) {
        val cleanBase = (baseUrlOverride.ifBlank { provider.defaultBaseUrl }).trim().trimEnd('/')
        val cleanKey = apiKey.trim()
        if (provider.requiresKeyForScan && cleanKey.isEmpty()) {
            return@withContext Result.failure(
                IllegalArgumentException("Enter your ${provider.label} API key before scanning models.")
            )
        }

        try {
            val request = when (provider) {
                AiProviderPreset.GOOGLE_AI_STUDIO -> {
                    Request.Builder()
                        .url("$cleanBase/models?key=$cleanKey")
                        .get()
                        .build()
                }
                else -> {
                    val builder = Request.Builder()
                        .url("$cleanBase/models")
                        .get()
                        .header("Accept", "application/json")
                    if (cleanKey.isNotEmpty()) {
                        builder.header("Authorization", "Bearer $cleanKey")
                    }
                    if (provider == AiProviderPreset.OPENROUTER) {
                        builder.header("HTTP-Referer", "https://pocketburner.local")
                        builder.header("X-Title", "PocketBurner IoT Studio")
                    }
                    builder.build()
                }
            }

            httpClient.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val apiErr = extractApiError(bodyText)
                    return@withContext Result.failure(
                        IllegalStateException("Model scan failed (${response.code}): ${apiErr.ifBlank { response.message }}")
                    )
                }

                val parsed = when (provider) {
                    AiProviderPreset.GOOGLE_AI_STUDIO -> parseGoogleAiModelsJson(bodyText)
                    else -> parseOpenAiModelsJson(bodyText, provider)
                }
                if (parsed.isEmpty()) {
                    return@withContext Result.failure(
                        IllegalStateException("No compatible chat models returned from $cleanBase/models.")
                    )
                }
                Result.success(parsed)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun runMultiAgentSynthesis(
        provider: AiProviderPreset,
        apiKey: String,
        baseUrlOverride: String,
        defaultModelId: String,
        roleModelOverrides: Map<AgentRole, String> = emptyMap(),
        board: HardwareBoardSpec,
        framework: IotFramework? = null,
        format: FirmwareTargetFormat,
        taskPrompt: String,
        onStepUpdate: (List<AgentStepOutput>) -> Unit,
    ): Result<MultiAgentSynthesisResult> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        val cleanBase = (baseUrlOverride.ifBlank { provider.defaultBaseUrl }).trim().trimEnd('/')
        val resolvedFramework = framework ?: IotFramework.fromFormat(format)

        if (customCompletionClient == null && cleanKey.isEmpty() && provider != AiProviderPreset.CUSTOM_OPENAI) {
            return@withContext Result.failure(
                IllegalArgumentException("Provide a free API key for ${provider.label} before starting the multi-agent build.")
            )
        }
        val fallbackModel = defaultModelId.trim()
        if (fallbackModel.isEmpty() && AgentRole.entries.any { roleModelOverrides[it].isNullOrBlank() }) {
            return@withContext Result.failure(
                IllegalArgumentException("Tap 'Scan Models for API' and select a :free model first.")
            )
        }
        if (taskPrompt.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Describe the IoT hardware work/behavior you want the agents to build.")
            )
        }

        fun modelFor(role: AgentRole): String =
            roleModelOverrides[role]?.takeIf { it.isNotBlank() } ?: fallbackModel

        val pipeline = AgentRegistry.selectPipeline(resolvedFramework, board)
        val states = pipeline.map { agent ->
            AgentStepOutput(
                role = agent.role,
                agentId = agent.agentId,
                agentDisplayName = agent.displayName,
                frameworkBadge = agent.frameworkSpecializationTag(resolvedFramework),
                phaseDetail = "Queued for ${board.id} (${resolvedFramework.badge})",
                modelId = modelFor(agent.role),
                state = AgentExecutionState.PENDING,
            )
        }.toMutableList()
        onStepUpdate(states.toList())

        val taskId = UUID.randomUUID().toString().take(8)
        val boardContext = buildBoardGroundingContext(board, format)
        val completionClient = customCompletionClient ?: defaultCompletionClient
        val priorOutputs = mutableMapOf<AgentRole, String>()
        var latestDraftCode = ""

        try {
            for ((index, agent) in pipeline.withIndex()) {
                val startTime = System.currentTimeMillis()
                val agentModel = modelFor(agent.role)
                states[index] = states[index].copy(
                    state = AgentExecutionState.RUNNING,
                    phaseDetail = "Initializing ${agent.displayName}…",
                )
                onStepUpdate(states.toList())

                val execContext = AgentExecutionContext(
                    taskId = taskId,
                    board = board,
                    framework = resolvedFramework,
                    format = format,
                    userObjective = taskPrompt,
                    provider = provider,
                    apiKey = cleanKey,
                    baseUrl = cleanBase,
                    modelId = agentModel,
                    boardGroundingText = boardContext,
                    priorOutputs = priorOutputs.toMap(),
                    latestDraftCode = latestDraftCode,
                )

                val artifact = withTimeout(75_000L) {
                    agent.execute(
                        context = execContext,
                        llmClient = completionClient,
                        onStatusDetail = { detail ->
                            states[index] = states[index].copy(phaseDetail = detail)
                            onStepUpdate(states.toList())
                        },
                    )
                }

                priorOutputs[agent.role] = artifact.rawOutput
                artifact.extractedCode?.takeIf { it.isNotBlank() }?.let { code ->
                    latestDraftCode = code
                }

                states[index] = states[index].copy(
                    state = AgentExecutionState.COMPLETED,
                    phaseDetail = artifact.summary,
                    outputText = artifact.rawOutput,
                    diagnostics = artifact.diagnostics,
                    durationMs = System.currentTimeMillis() - startTime,
                )
                onStepUpdate(states.toList())
            }

            val archOutput = priorOutputs[AgentRole.ARCHITECT].orEmpty()
            val auditOutput = priorOutputs[AgentRole.AUDITOR].orEmpty()
            val packOutput = priorOutputs[AgentRole.PACKAGER].orEmpty()
            val finalCode = latestDraftCode.ifBlank {
                extractPrimaryCodeBlock(priorOutputs[AgentRole.ENGINEER].orEmpty())
            }

            val safeSlug = board.id.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
            val ext = format.defaultFileName.substringAfterLast('.', "txt")
            val suggestedFile = "${safeSlug}_${resolvedFramework.id}_fw.$ext"

            Result.success(
                MultiAgentSynthesisResult(
                    taskId = taskId,
                    board = board,
                    framework = resolvedFramework,
                    format = format,
                    taskPrompt = taskPrompt,
                    steps = states.toList(),
                    pinoutPlan = archOutput,
                    finalCode = finalCode,
                    auditSummary = stripCodeBlocks(auditOutput).ifBlank { "Verified by ${resolvedFramework.badge} Auditor for ${board.displayName}." },
                    buildAndFlashInstructions = packOutput,
                    suggestedFilename = suggestedFile,
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val activeIdx = states.indexOfFirst { it.state == AgentExecutionState.RUNNING }
            if (activeIdx >= 0) {
                states[activeIdx] = states[activeIdx].copy(
                    state = AgentExecutionState.FAILED,
                    phaseDetail = "Execution failed",
                    errorMessage = e.message ?: "Agent execution failed",
                )
                onStepUpdate(states.toList())
            }
            Result.failure(e)
        }
    }

    private fun callChatCompletion(
        provider: AiProviderPreset,
        apiKey: String,
        baseUrl: String,
        modelId: String,
        systemPrompt: String,
        userPrompt: String,
    ): String {
        val jsonMedia = "application/json; charset=utf-8".toMediaType()
        val request: Request = when (provider) {
            AiProviderPreset.GOOGLE_AI_STUDIO -> {
                val cleanModel = modelId.removePrefix("models/").removeSuffix(":free")
                val payload = JSONObject().apply {
                    put(
                        "systemInstruction",
                        JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))),
                    )
                    put(
                        "contents",
                        JSONArray().put(
                            JSONObject()
                                .put("role", "user")
                                .put("parts", JSONArray().put(JSONObject().put("text", userPrompt)))
                        ),
                    )
                    put(
                        "generationConfig",
                        JSONObject()
                            .put("temperature", 0.25)
                            .put("maxOutputTokens", 4096),
                    )
                }
                Request.Builder()
                    .url("$baseUrl/models/$cleanModel:generateContent?key=$apiKey")
                    .post(payload.toString().toRequestBody(jsonMedia))
                    .build()
            }
            else -> {
                val payload = JSONObject().apply {
                    put("model", modelId)
                    put("temperature", 0.25)
                    put("max_tokens", 4096)
                    put(
                        "messages",
                        JSONArray()
                            .put(JSONObject().put("role", "system").put("content", systemPrompt))
                            .put(JSONObject().put("role", "user").put("content", userPrompt)),
                    )
                }
                val builder = Request.Builder()
                    .url("$baseUrl/chat/completions")
                    .post(payload.toString().toRequestBody(jsonMedia))
                    .header("Content-Type", "application/json")
                if (apiKey.isNotEmpty()) {
                    builder.header("Authorization", "Bearer $apiKey")
                }
                if (provider == AiProviderPreset.OPENROUTER) {
                    builder.header("HTTP-Referer", "https://pocketburner.local")
                    builder.header("X-Title", "PocketBurner IoT Studio")
                }
                builder.build()
            }
        }

        httpClient.newCall(request).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val apiErr = extractApiError(bodyText)
                throw IllegalStateException("LLM API error (${response.code}) [$modelId]: ${apiErr.ifBlank { response.message }}")
            }

            return when (provider) {
                AiProviderPreset.GOOGLE_AI_STUDIO -> {
                    val root = JSONObject(bodyText)
                    val candidates = root.optJSONArray("candidates")
                        ?: throw IllegalStateException("No candidates returned by Gemini API.")
                    val parts = candidates.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts")
                    val text = buildString {
                        if (parts != null) {
                            for (i in 0 until parts.length()) {
                                append(parts.optJSONObject(i)?.optString("text").orEmpty())
                            }
                        }
                    }
                    text.ifBlank { throw IllegalStateException("Model returned empty response.") }
                }
                else -> {
                    val root = JSONObject(bodyText)
                    val choices = root.optJSONArray("choices")
                        ?: throw IllegalStateException("Missing 'choices' in chat completion response.")
                    val content = choices.optJSONObject(0)
                        ?.optJSONObject("message")
                        ?.optString("content")
                        .orEmpty()
                    content.ifBlank { throw IllegalStateException("Model returned empty message content.") }
                }
            }
        }
    }

    companion object {
        fun buildBoardGroundingContext(board: HardwareBoardSpec, format: FirmwareTargetFormat): String = buildString {
            appendLine("Board ID: ${board.id} (${board.displayName})")
            appendLine("Family / Series: ${board.family.label} / ${board.seriesGroup}")
            appendLine("SoC / MCU: ${board.chip} (${board.architecture}, ${board.cpuClockMhz} MHz)")
            appendLine("Memory: Flash ${board.flashSizeLabel}, RAM ${board.ramLabel}")
            appendLine("Logic Voltage: ${board.logicVoltage}")
            appendLine("Flash Protocol & Offsets: ${board.protocol.label} • Boot ${board.bootloaderOffset} -> App ${board.appOffset} • Mode ${board.flashMode} @ ${board.defaultBaud} bps")
            appendLine("Interfaces: ${board.keyInterfaces.joinToString(", ")}")
            appendLine("GPIO Pinout: ${board.gpioPinoutSummary}")
            appendLine("Supported Languages: ${board.supportedLanguages.joinToString(", ")}")
            appendLine("Requested Output Format: ${format.label}")
            appendLine("Board Notes: ${board.notes}")
        }

        fun parseOpenAiModelsJson(jsonBody: String, provider: AiProviderPreset): List<ScannedAiModel> {
            val root = JSONObject(jsonBody)
            val data = root.optJSONArray("data") ?: return emptyList()
            val list = ArrayList<ScannedAiModel>(data.length())
            for (i in 0 until data.length()) {
                val obj = data.optJSONObject(i) ?: continue
                val id = obj.optString("id").trim()
                if (id.isEmpty()) continue
                val rawName = obj.optString("name").ifBlank { id }
                val ctx = obj.optInt("context_length", obj.optInt("context_window", 0))
                val pricing = obj.optJSONObject("pricing")
                val promptPrice = pricing?.optString("prompt").orEmpty().trim()
                val completionPrice = pricing?.optString("completion").orEmpty().trim()
                val isZeroPrice = (promptPrice == "0" || promptPrice == "0.0" || promptPrice == "0.00") &&
                    (completionPrice == "0" || completionPrice == "0.0" || completionPrice == "0.00")

                val isFree = when (provider) {
                    AiProviderPreset.OPENROUTER -> id.endsWith(":free", ignoreCase = true) || isZeroPrice
                    AiProviderPreset.GROQ -> true
                    AiProviderPreset.CUSTOM_OPENAI -> id.endsWith(":free", ignoreCase = true) || isZeroPrice || pricing == null
                    AiProviderPreset.GOOGLE_AI_STUDIO -> true
                }

                list.add(
                    ScannedAiModel(
                        id = id,
                        displayName = rawName,
                        providerId = provider.id,
                        isFree = isFree,
                        contextLength = ctx,
                        pricingNote = if (isFree) ":free" else "paid",
                    )
                )
            }
            return list.sortedWith(
                compareByDescending<ScannedAiModel> { it.isFree }
                    .thenBy { it.displayName.lowercase() }
            )
        }

        fun parseGoogleAiModelsJson(jsonBody: String): List<ScannedAiModel> {
            val root = JSONObject(jsonBody)
            val models = root.optJSONArray("models") ?: return emptyList()
            val list = mutableListOf<ScannedAiModel>()
            for (i in 0 until models.length()) {
                val obj = models.optJSONObject(i) ?: continue
                val fullName = obj.optString("name").trim()
                if (fullName.isEmpty()) continue
                val methods = obj.optJSONArray("supportedGenerationMethods")
                val supportsGenerate = (0 until (methods?.length() ?: 0)).any { idx ->
                    methods?.optString(idx) == "generateContent"
                }
                if (!supportsGenerate) continue

                val shortId = fullName.removePrefix("models/")
                val displayName = obj.optString("displayName").ifBlank { shortId }
                val inputLimit = obj.optInt("inputTokenLimit", 0)
                list.add(
                    ScannedAiModel(
                        id = shortId,
                        displayName = displayName,
                        providerId = AiProviderPreset.GOOGLE_AI_STUDIO.id,
                        isFree = true,
                        contextLength = inputLimit,
                        pricingNote = ":free",
                    )
                )
            }
            return list.sortedBy { it.displayName.lowercase() }
        }

        fun filterScannedModels(
            models: List<ScannedAiModel>,
            query: String,
            freeOnly: Boolean,
        ): List<ScannedAiModel> {
            val tokens = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
            return models.filter { model ->
                if (freeOnly && !model.isFree) return@filter false
                if (tokens.isEmpty()) return@filter true
                val hay = "${model.id} ${model.displayName} ${model.pricingNote}".lowercase()
                tokens.all { hay.contains(it) }
            }
        }

        fun extractPrimaryCodeBlock(markdown: String): String {
            val regex = Regex("```[a-zA-Z0-9_+.-]*\\s*\\n([\\s\\S]*?)```")
            val match = regex.findAll(markdown).maxByOrNull { it.groupValues[1].length }
            return match?.groupValues?.get(1)?.trimEnd() ?: markdown.trim()
        }

        fun stripCodeBlocks(markdown: String): String {
            return markdown
                .replace(Regex("```[a-zA-Z0-9_+.-]*\\s*\\n[\\s\\S]*?```"), "")
                .trim()
        }

        private fun extractApiError(bodyText: String): String {
            return runCatching {
                val obj = JSONObject(bodyText)
                obj.optJSONObject("error")?.optString("message")
                    ?: obj.optString("message")
            }.getOrDefault("")
        }
    }
}
