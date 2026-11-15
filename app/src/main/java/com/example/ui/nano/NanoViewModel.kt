package com.example.ui.nano

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.local.ScriptEntity
import com.example.data.local.SecurityAndTransportSettings
import com.example.engine.ActiveConnection
import com.example.engine.ScriptLanguage
import com.example.engine.ScriptTransferMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NanoEditorUiState(
    val currentScriptId: String? = null,
    val fileName: String = "main.py",
    val language: ScriptLanguage = ScriptLanguage.MICROPYTHON,
    val code: String = ScriptLanguage.MICROPYTHON.starterTemplate,
    val targetDeviceId: String = "CoreS3",
    val isModified: Boolean = false,
    val cutBuffer: String = "",
    val statusBarMessage: String = "[ New Buffer ]",
    val isTransferring: Boolean = false,
    val transferProgress: Float = 0f,
)

class NanoViewModel(private val container: AppContainer) : ViewModel() {

    private val scriptRepo = container.scriptRepository
    private val transport = container.transportManager

    private val _uiState = MutableStateFlow(NanoEditorUiState())
    val uiState: StateFlow<NanoEditorUiState> = _uiState.asStateFlow()

    val savedScripts: StateFlow<List<ScriptEntity>> = scriptRepo.allScripts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val connectionState: StateFlow<ActiveConnection> = transport.connectionState

    val settings: StateFlow<SecurityAndTransportSettings> = container.settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SecurityAndTransportSettings())

    fun openFileFromCommand(fileName: String) {
        viewModelScope.launch {
            val (existing, content) = scriptRepo.loadScriptContent(fileName)
            val lang = existing?.let { runCatching { ScriptLanguage.valueOf(it.language) }.getOrNull() }
                ?: ScriptLanguage.detectFromFileName(fileName)
            _uiState.value = _uiState.value.copy(
                currentScriptId = existing?.id,
                fileName = existing?.fileName ?: fileName,
                language = lang,
                code = content,
                targetDeviceId = existing?.targetDeviceId ?: settings.value.selectedBoardId,
                isModified = existing == null,
                statusBarMessage = if (existing != null) "[ Loaded ${existing.fileName} ]" else "[ New File $fileName ]",
            )
        }
    }

    fun loadExistingScript(entity: ScriptEntity) {
        openFileFromCommand(entity.fileName)
    }

    fun loadGeneratedScript(fileName: String, code: String, targetDeviceId: String) {
        val detected = ScriptLanguage.detectFromFileName(fileName)
        _uiState.value = _uiState.value.copy(
            currentScriptId = null,
            fileName = fileName,
            language = detected,
            code = code,
            targetDeviceId = targetDeviceId,
            isModified = true,
            statusBarMessage = "[ Loaded AI-synthesized $fileName for $targetDeviceId ]",
        )
    }

    fun updateCode(newCode: String) {
        _uiState.value = _uiState.value.copy(
            code = newCode,
            isModified = true,
            statusBarMessage = "[ Modified ]",
        )
    }

    fun updateFileName(name: String) {
        val detected = ScriptLanguage.detectFromFileName(name)
        _uiState.value = _uiState.value.copy(
            fileName = name,
            language = detected,
            isModified = true,
        )
    }

    fun selectLanguage(lang: ScriptLanguage) {
        val current = _uiState.value
        val stem = current.fileName.substringBeforeLast(".").ifBlank { "script" }
        _uiState.value = current.copy(
            language = lang,
            fileName = "$stem.${lang.extension}",
            isModified = true,
            statusBarMessage = "[ Language: ${lang.displayName} ]",
        )
    }

    fun loadStarterTemplate(lang: ScriptLanguage) {
        _uiState.value = _uiState.value.copy(
            currentScriptId = null,
            fileName = "main.${lang.extension}",
            language = lang,
            code = lang.starterTemplate,
            isModified = true,
            statusBarMessage = "[ Loaded ${lang.displayName} template ]",
        )
    }

    fun selectTargetDevice(deviceId: String) {
        _uiState.value = _uiState.value.copy(targetDeviceId = deviceId)
    }

    fun insertSnippet(snippet: String) {
        val cur = _uiState.value.code
        val next = if (cur.endsWith("\n") || cur.isEmpty()) "$cur$snippet\n" else "$cur\n$snippet\n"
        _uiState.value = _uiState.value.copy(
            code = next,
            isModified = true,
            statusBarMessage = "[ Inserted helper snippet ]",
        )
    }

    fun cutLineAtIndex(lineIndex: Int) {
        val lines = _uiState.value.code.lines().toMutableList()
        if (lineIndex in lines.indices) {
            val removed = lines.removeAt(lineIndex)
            _uiState.value = _uiState.value.copy(
                code = lines.joinToString("\n"),
                cutBuffer = removed,
                isModified = true,
                statusBarMessage = "[ ^K Cut line ${lineIndex + 1} ]",
            )
        }
    }

    fun pasteCutBuffer(afterLineIndex: Int) {
        val cut = _uiState.value.cutBuffer
        if (cut.isEmpty()) {
            _uiState.value = _uiState.value.copy(statusBarMessage = "[ Cut buffer empty ]")
            return
        }
        val lines = _uiState.value.code.lines().toMutableList()
        val insertIdx = (afterLineIndex + 1).coerceIn(0, lines.size)
        lines.add(insertIdx, cut)
        _uiState.value = _uiState.value.copy(
            code = lines.joinToString("\n"),
            isModified = true,
            statusBarMessage = "[ ^U Pasted line ]",
        )
    }

    fun saveToLocalStorage() {
        viewModelScope.launch {
            val st = _uiState.value
            val saved = scriptRepo.saveScript(
                existingId = st.currentScriptId,
                fileName = st.fileName,
                language = st.language,
                content = st.code,
                targetDeviceId = st.targetDeviceId,
            )
            _uiState.value = st.copy(
                currentScriptId = saved.id,
                fileName = saved.fileName,
                isModified = false,
                statusBarMessage = "[ ^O Wrote ${saved.sizeBytes} bytes to local storage ]",
            )
        }
    }

    fun deleteScript(entity: ScriptEntity) {
        viewModelScope.launch {
            scriptRepo.deleteScript(entity)
            if (_uiState.value.currentScriptId == entity.id) {
                _uiState.value = _uiState.value.copy(
                    currentScriptId = null,
                    statusBarMessage = "[ Deleted ${entity.fileName} ]",
                )
            }
        }
    }

    fun exportToUri(uri: Uri) {
        viewModelScope.launch {
            val res = scriptRepo.exportScriptToUri(_uiState.value.code, uri)
            _uiState.value = _uiState.value.copy(
                statusBarMessage = res.fold(
                    onSuccess = { "[ Exported ${_uiState.value.fileName} ]" },
                    onFailure = { "[ Export failed: ${it.message} ]" }
                )
            )
        }
    }

    fun transferToDevice(mode: ScriptTransferMode) {
        viewModelScope.launch {
            val st = _uiState.value
            // Auto-save locally before transferring
            val saved = scriptRepo.saveScript(
                existingId = st.currentScriptId,
                fileName = st.fileName,
                language = st.language,
                content = st.code,
                targetDeviceId = st.targetDeviceId,
            )
            _uiState.value = st.copy(
                currentScriptId = saved.id,
                isModified = false,
                isTransferring = true,
                transferProgress = 0.05f,
                statusBarMessage = "[ Transferring ${saved.fileName}... ]",
            )
            val res = scriptRepo.transferScriptToDevice(saved.fileName, st.code, mode) { prog, msg ->
                _uiState.value = _uiState.value.copy(
                    transferProgress = prog,
                    statusBarMessage = "[ $msg ]",
                )
            }
            _uiState.value = _uiState.value.copy(
                isTransferring = false,
                transferProgress = if (res.isSuccess) 1f else 0f,
                statusBarMessage = res.fold(
                    onSuccess = { "[ ^T $it ]" },
                    onFailure = { "[ Transfer error: ${it.message} ]" }
                )
            )
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = NanoViewModel(container) as T
    }
}
