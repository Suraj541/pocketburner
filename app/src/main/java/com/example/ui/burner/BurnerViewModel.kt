package com.example.ui.burner

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.local.CategoryEntity
import com.example.data.local.DeviceSeriesWithDevices
import com.example.data.local.DownloadState
import com.example.data.local.FirmwareEntity
import com.example.data.local.RepoParserType
import com.example.data.local.RepoSourceEntity
import com.example.engine.ActiveConnection
import com.example.engine.FlashOptions
import com.example.hardware.FirmwareCategory
import com.example.hardware.HardwareBoardSpec
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FlashSessionState(
    val isFlashing: Boolean = false,
    val progress: Float = 0f,
    val statusText: String = "",
    val resultMessage: String? = null,
    val isError: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class BurnerViewModel(private val container: AppContainer) : ViewModel() {

    private val catalog = container.firmwareCatalogEngine
    private val flashEngine = container.flashProtocolEngine
    private val firmwareDao = container.database.firmwareDao()
    private val repoDao = container.database.repoSourceDao()
    private val taxonomyRepo = container.taxonomyRepository

    val categories: StateFlow<List<CategoryEntity>> = taxonomyRepo.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deviceHierarchy: StateFlow<List<DeviceSeriesWithDevices>> = taxonomyRepo.seriesWithDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedCategory = MutableStateFlow(FirmwareCategory.ALL)
    val selectedCategory: StateFlow<FirmwareCategory> = _selectedCategory.asStateFlow()

    private val _selectedSeriesId = MutableStateFlow<String?>(null)
    val selectedSeriesId: StateFlow<String?> = _selectedSeriesId.asStateFlow()

    private val _selectedDevice = MutableStateFlow("All devices")
    val selectedDevice: StateFlow<String> = _selectedDevice.asStateFlow()

    private val _isDevicesExpanded = MutableStateFlow(true)
    val isDevicesExpanded: StateFlow<Boolean> = _isDevicesExpanded.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private val _flashSession = MutableStateFlow(FlashSessionState())
    val flashSession: StateFlow<FlashSessionState> = _flashSession.asStateFlow()

    val connectionState: StateFlow<ActiveConnection> = container.transportManager.connectionState

    val repos: StateFlow<List<RepoSourceEntity>> = repoDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Offline-capable Room relational query + instant text search
    val filteredFirmwares: StateFlow<List<FirmwareEntity>> = combine(
        _selectedCategory,
        _selectedDevice,
        _selectedSeriesId,
    ) { category, device, seriesId ->
        val effectiveDeviceFilter = if (device != "All devices") device else (seriesId ?: "All devices")
        Triple(category, effectiveDeviceFilter, Unit)
    }.flatMapLatest { (category, deviceFilter, _) ->
        firmwareDao.observeFilteredOffline(
            categoryId = category.name,
            categoryLabel = category.label,
            deviceId = deviceFilter,
        )
    }.combine(_searchQuery) { items, query ->
        val q = query.trim().lowercase()
        if (q.isBlank()) items
        else items.filter { item ->
            item.title.lowercase().contains(q) ||
                item.author.lowercase().contains(q) ||
                item.description.lowercase().contains(q) ||
                item.deviceTagsCsv.lowercase().contains(q) ||
                item.framework.lowercase().contains(q)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            taxonomyRepo.ensureTaxonomySeeded()
            catalog.ensureDefaultReposSeeded()
            syncAllRepos()
        }
    }

    fun selectCategory(category: FirmwareCategory) {
        _selectedCategory.value = category
    }

    fun selectSeries(seriesId: String?) {
        _selectedSeriesId.value = seriesId
        _selectedDevice.value = "All devices"
    }

    fun selectDevice(deviceId: String) {
        _selectedDevice.value = deviceId
    }

    fun toggleDevicesExpanded() {
        _isDevicesExpanded.value = !_isDevicesExpanded.value
    }

    fun setSearchQuery(q: String) {
        _searchQuery.value = q
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    fun syncAllRepos() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            _syncMessage.value = null
            val errors = catalog.syncAllEnabledRepos()
            _isSyncing.value = false
            _syncMessage.value = if (errors.isEmpty()) {
                "Catalog synced & cached offline in Room."
            } else {
                "Offline cache active (${errors.size} feed notice: ${errors.first()})"
            }
        }
    }

    fun addRepoFeed(name: String, url: String, parserType: RepoParserType) {
        viewModelScope.launch {
            _isSyncing.value = true
            val res = catalog.addRepoSource(name, url, parserType)
            _isSyncing.value = false
            _syncMessage.value = res.fold(
                onSuccess = { count -> "Added feed '$name' ($count items cached offline)." },
                onFailure = { err -> "Feed added, sync issue: ${err.message}" }
            )
        }
    }

    fun toggleRepoEnabled(repo: RepoSourceEntity) {
        viewModelScope.launch {
            repoDao.upsert(repo.copy(enabled = !repo.enabled))
        }
    }

    fun deleteRepo(repo: RepoSourceEntity) {
        viewModelScope.launch {
            repoDao.deleteById(repo.id)
        }
    }

    fun downloadFirmware(item: FirmwareEntity) {
        if (item.downloadState == DownloadState.DOWNLOADING.name) return
        viewModelScope.launch {
            val res = catalog.downloadFirmwareOnDemand(item)
            res.exceptionOrNull()?.let { err ->
                _syncMessage.value = "Download failed: ${err.message}"
            }
        }
    }

    fun deleteLocalDownload(item: FirmwareEntity) {
        viewModelScope.launch {
            catalog.deleteLocalBinary(item)
        }
    }

    fun addCustomUrlFirmware(
        title: String,
        author: String,
        category: FirmwareCategory,
        targetDeviceId: String,
        framework: String,
        version: String,
        downloadUrl: String,
        flashOffset: String,
        baudRate: Int,
        sha256: String?,
        description: String,
    ) {
        viewModelScope.launch {
            catalog.addCustomUrlFirmware(
                title = title,
                author = author,
                category = category,
                targetDeviceId = targetDeviceId,
                framework = framework,
                version = version,
                downloadUrl = downloadUrl,
                flashOffset = flashOffset,
                baudRate = baudRate,
                sha256 = sha256,
                description = description,
            )
            _syncMessage.value = "Added custom firmware '$title'. Tap Download when ready to flash."
        }
    }

    fun addCustomLocalFirmware(
        uri: Uri,
        title: String,
        category: FirmwareCategory,
        targetDeviceId: String,
        framework: String,
        version: String,
        flashOffset: String,
        baudRate: Int,
        description: String,
    ) {
        viewModelScope.launch {
            val res = catalog.addCustomLocalFirmware(
                uri = uri,
                title = title,
                category = category,
                targetDeviceId = targetDeviceId,
                framework = framework,
                version = version,
                flashOffset = flashOffset,
                baudRate = baudRate,
                description = description,
            )
            _syncMessage.value = res.fold(
                onSuccess = { "Imported '${it.title}' (${it.sizeBytes / 1024} KB)." },
                onFailure = { "Import failed: ${it.message}" }
            )
        }
    }

    fun flashFirmware(item: FirmwareEntity, board: HardwareBoardSpec, options: FlashOptions) {
        val localPath = item.localFilePath
        if (localPath.isNullOrBlank()) {
            _flashSession.value = FlashSessionState(
                isFlashing = false,
                progress = 0f,
                statusText = "Binary not downloaded yet.",
                resultMessage = "Download the firmware image first.",
                isError = true,
            )
            return
        }
        viewModelScope.launch {
            _flashSession.value = FlashSessionState(isFlashing = true, progress = 0.02f, statusText = "Initializing flash...")
            if (options.eraseBeforeFlash && board.protocol == com.example.hardware.FlashProtocol.ESPTOOL_SLIP) {
                val eraseRes = flashEngine.eraseEspFlash(board) { p, msg ->
                    _flashSession.value = FlashSessionState(isFlashing = true, progress = p * 0.2f, statusText = msg)
                }
                if (eraseRes.isFailure) {
                    _flashSession.value = FlashSessionState(
                        isFlashing = false,
                        progress = 0f,
                        statusText = "Erase failed",
                        resultMessage = eraseRes.exceptionOrNull()?.message,
                        isError = true,
                    )
                    return@launch
                }
            }
            val res = flashEngine.flashFirmware(File(localPath), board, options) { p, msg ->
                _flashSession.value = FlashSessionState(isFlashing = true, progress = p, statusText = msg)
            }
            _flashSession.value = res.fold(
                onSuccess = { msg ->
                    FlashSessionState(isFlashing = false, progress = 1f, statusText = "Complete", resultMessage = msg, isError = false)
                },
                onFailure = { err ->
                    FlashSessionState(isFlashing = false, progress = 0f, statusText = "Error", resultMessage = err.message, isError = true)
                }
            )
        }
    }

    fun eraseBoardFlash(board: HardwareBoardSpec) {
        viewModelScope.launch {
            _flashSession.value = FlashSessionState(isFlashing = true, progress = 0.1f, statusText = "Erasing ${board.displayName}...")
            val res = flashEngine.eraseEspFlash(board) { p, msg ->
                _flashSession.value = FlashSessionState(isFlashing = true, progress = p, statusText = msg)
            }
            _flashSession.value = res.fold(
                onSuccess = { msg -> FlashSessionState(isFlashing = false, progress = 1f, statusText = "Erased", resultMessage = msg) },
                onFailure = { err -> FlashSessionState(isFlashing = false, progress = 0f, statusText = "Erase failed", resultMessage = err.message, isError = true) }
            )
        }
    }

    fun resetFlashSession() {
        _flashSession.value = FlashSessionState()
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = BurnerViewModel(container) as T
    }
}
