package com.example.ui.forge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.local.SecurityAndTransportSettings
import com.example.engine.AgentExecutionState
import com.example.engine.AgentRole
import com.example.engine.AgentStepOutput
import com.example.engine.AiProviderPreset
import com.example.engine.FirmwareTargetFormat
import com.example.engine.MultiAgentFirmwareEngine
import com.example.engine.MultiAgentSynthesisResult
import com.example.engine.ScannedAiModel
import com.example.engine.ScriptLanguage
import com.example.engine.agent.AgentRegistry
import com.example.engine.agent.IotFramework
import com.example.hardware.HardwareRegistry
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AiForgeViewModel(private val container: AppContainer) : ViewModel() {

    private val engine = container.multiAgentFirmwareEngine
    private val settingsStore = container.settingsStore
    private val scriptRepository = container.scriptRepository
    private val catalogEngine = container.firmwareCatalogEngine

    val settings: StateFlow<SecurityAndTransportSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SecurityAndTransportSettings())

    private val _scannedModels = MutableStateFlow<List<ScannedAiModel>>(emptyList())
    val scannedModels: StateFlow<List<ScannedAiModel>> = _scannedModels.asStateFlow()

    private val _modelSearchQuery = MutableStateFlow("")
    val modelSearchQuery: StateFlow<String> = _modelSearchQuery.asStateFlow()

    val filteredModels: StateFlow<List<ScannedAiModel>> = combine(
        _scannedModels,
        _modelSearchQuery,
        settings,
    ) { models, query, cfg ->
        MultiAgentFirmwareEngine.filterScannedModels(
            models = models,
            query = query,
            freeOnly = cfg.aiFreeOnlyFilter,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isScanningModels = MutableStateFlow(false)
    val isScanningModels: StateFlow<Boolean> = _isScanningModels.asStateFlow()

    private val _scanError = MutableStateFlow<String?>(null)
    val scanError: StateFlow<String?> = _scanError.asStateFlow()

    private val _targetBoardId = MutableStateFlow("CoreS3")
    val targetBoardId: StateFlow<String> = _targetBoardId.asStateFlow()

    private val _targetFramework = MutableStateFlow(IotFramework.ARDUINO)
    val targetFramework: StateFlow<IotFramework> = _targetFramework.asStateFlow()

    private val _targetFormat = MutableStateFlow(FirmwareTargetFormat.ARDUINO_INO)
    val targetFormat: StateFlow<FirmwareTargetFormat> = _targetFormat.asStateFlow()

    private val _taskPrompt = MutableStateFlow("")
    val taskPrompt: StateFlow<String> = _taskPrompt.asStateFlow()

    private val _roleModelOverrides = MutableStateFlow<Map<AgentRole, String>>(emptyMap())
    val roleModelOverrides: StateFlow<Map<AgentRole, String>> = _roleModelOverrides.asStateFlow()

    private val _agentSteps = MutableStateFlow(buildPreviewPipeline(IotFramework.ARDUINO, "CoreS3"))
    val agentSteps: StateFlow<List<AgentStepOutput>> = _agentSteps.asStateFlow()

    private val _isRunningAgents = MutableStateFlow(false)
    val isRunningAgents: StateFlow<Boolean> = _isRunningAgents.asStateFlow()

    private val _synthesisResult = MutableStateFlow<MultiAgentSynthesisResult?>(null)
    val synthesisResult: StateFlow<MultiAgentSynthesisResult?> = _synthesisResult.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private var activeBuildJob: Job? = null

    private fun buildPreviewPipeline(framework: IotFramework, boardId: String): List<AgentStepOutput> {
        val board = HardwareRegistry.findById(boardId) ?: HardwareRegistry.boards.first()
        val overrides = _roleModelOverrides.value
        val selectedModel = settings.value.aiSelectedModelId
        return AgentRegistry.selectPipeline(framework, board).map { agent ->
            AgentStepOutput(
                role = agent.role,
                agentId = agent.agentId,
                agentDisplayName = agent.displayName,
                frameworkBadge = agent.frameworkSpecializationTag(framework),
                phaseDetail = "Ready for ${board.id} (${framework.badge})",
                modelId = overrides[agent.role]?.takeIf { it.isNotBlank() } ?: selectedModel,
                state = AgentExecutionState.PENDING,
            )
        }
    }

    private fun refreshPreviewPipelineIfIdle() {
        if (!_isRunningAgents.value && _synthesisResult.value == null) {
            _agentSteps.value = buildPreviewPipeline(_targetFramework.value, _targetBoardId.value)
        }
    }

    fun selectProvider(preset: AiProviderPreset) {
        viewModelScope.launch {
            settingsStore.setAiProvider(preset.id, preset.defaultBaseUrl)
            _scannedModels.value = emptyList()
            _scanError.value = null
        }
    }

    fun updateApiKey(key: String) {
        viewModelScope.launch { settingsStore.setAiApiKey(key) }
    }

    fun updateBaseUrl(url: String) {
        viewModelScope.launch { settingsStore.setAiCustomBaseUrl(url) }
    }

    fun selectModel(modelId: String) {
        viewModelScope.launch {
            settingsStore.setAiSelectedModelId(modelId)
            refreshPreviewPipelineIfIdle()
        }
    }

    fun setFreeOnlyFilter(freeOnly: Boolean) {
        viewModelScope.launch { settingsStore.setAiFreeOnlyFilter(freeOnly) }
    }

    fun setModelSearchQuery(query: String) {
        _modelSearchQuery.value = query
    }

    fun selectTargetBoard(boardId: String) {
        _targetBoardId.value = boardId
        refreshPreviewPipelineIfIdle()
        viewModelScope.launch { settingsStore.setSelectedBoardId(boardId) }
    }

    fun selectTargetFramework(framework: IotFramework) {
        _targetFramework.value = framework
        _targetFormat.value = framework.defaultFormat
        refreshPreviewPipelineIfIdle()
    }

    fun selectTargetFormat(format: FirmwareTargetFormat) {
        _targetFormat.value = format
        _targetFramework.value = IotFramework.fromFormat(format)
        refreshPreviewPipelineIfIdle()
    }

    fun updateTaskPrompt(prompt: String) {
        _taskPrompt.value = prompt
    }

    fun setRoleModelOverride(role: AgentRole, modelId: String?) {
        val current = _roleModelOverrides.value.toMutableMap()
        if (modelId.isNullOrBlank()) {
            current.remove(role)
        } else {
            current[role] = modelId
        }
        _roleModelOverrides.value = current
        refreshPreviewPipelineIfIdle()
    }

    fun scanModelsForApi() {
        val cfg = settings.value
        val preset = AiProviderPreset.fromId(cfg.aiProvider)
        viewModelScope.launch {
            _isScanningModels.value = true
            _scanError.value = null
            val res = engine.scanAvailableModels(
                provider = preset,
                apiKey = cfg.aiApiKey,
                baseUrlOverride = cfg.aiCustomBaseUrl,
            )
            _isScanningModels.value = false
            res.onSuccess { models ->
                _scannedModels.value = models
                val freeCount = models.count { it.isFree }
                if (cfg.aiSelectedModelId.isBlank() || models.none { it.id == cfg.aiSelectedModelId }) {
                    val firstFree = models.firstOrNull { it.isFree } ?: models.firstOrNull()
                    if (firstFree != null) {
                        settingsStore.setAiSelectedModelId(firstFree.id)
                    }
                }
                refreshPreviewPipelineIfIdle()
                _statusMessage.value = "Scanned ${models.size} models ($freeCount :free available)."
            }.onFailure { err ->
                _scanError.value = err.message ?: "Unable to scan API models."
            }
        }
    }

    fun runMultiAgentBuild() {
        if (_isRunningAgents.value) return
        val cfg = settings.value
        val preset = AiProviderPreset.fromId(cfg.aiProvider)
        val board = HardwareRegistry.findById(_targetBoardId.value) ?: HardwareRegistry.boards.first()
        val framework = _targetFramework.value
        val format = _targetFormat.value
        val prompt = _taskPrompt.value

        activeBuildJob = viewModelScope.launch {
            _isRunningAgents.value = true
            _statusMessage.value = null
            _synthesisResult.value = null

            val res = engine.runMultiAgentSynthesis(
                provider = preset,
                apiKey = cfg.aiApiKey,
                baseUrlOverride = cfg.aiCustomBaseUrl,
                defaultModelId = cfg.aiSelectedModelId,
                roleModelOverrides = _roleModelOverrides.value,
                board = board,
                framework = framework,
                format = format,
                taskPrompt = prompt,
                onStepUpdate = { updatedSteps ->
                    _agentSteps.value = updatedSteps
                },
            )

            _isRunningAgents.value = false
            res.onSuccess { result ->
                _synthesisResult.value = result
                _statusMessage.value = "${framework.badge} 4-agent firmware build complete for ${board.id} (${result.finalCode.lines().size} lines)."
            }.onFailure { err ->
                _statusMessage.value = err.message ?: "Multi-agent build failed."
            }
        }
    }

    fun cancelBuild() {
        activeBuildJob?.cancel()
        activeBuildJob = null
        _isRunningAgents.value = false
        _statusMessage.value = "Multi-agent build cancelled."
    }

    fun saveResultToScriptVault() {
        val result = _synthesisResult.value ?: return
        viewModelScope.launch {
            val lang = ScriptLanguage.detectFromFileName(result.suggestedFilename)
            scriptRepository.saveScript(
                existingId = null,
                fileName = result.suggestedFilename,
                language = lang,
                content = result.finalCode,
                targetDeviceId = result.board.id,
            )
            _statusMessage.value = "Saved '${result.suggestedFilename}' to Nano Script Vault."
        }
    }

    fun saveResultToFirmwareCatalog() {
        val result = _synthesisResult.value ?: return
        viewModelScope.launch {
            catalogEngine.addGeneratedFirmwarePackage(
                title = "${result.board.id} (${result.framework.badge}): ${result.taskPrompt.take(30)}",
                targetDeviceId = result.board.id,
                framework = result.framework.displayName,
                fileName = result.suggestedFilename,
                codeContent = result.finalCode,
                buildGuide = result.buildAndFlashInstructions,
            )
            _statusMessage.value = "Added '${result.suggestedFilename}' to Burner Custom Firmware Catalog."
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
        _scanError.value = null
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AiForgeViewModel(container) as T
    }
}
