package com.example.ui.vulnforge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.local.LabEntity
import com.example.data.local.VulnerabilityEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VulnForgeViewModel(private val repository: com.example.data.local.VulnForgeRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val severity = MutableStateFlow("All")
    private val _selected = MutableStateFlow<VulnerabilityEntity?>(null)
    val selected: StateFlow<VulnerabilityEntity?> = _selected.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val labs = repository.labs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val events = repository.recentEvents.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val sources = repository.dataSources.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val vulnerabilities: StateFlow<List<VulnerabilityEntity>> = combine(repository.vulnerabilities, query, severity) { items, q, selectedSeverity ->
        items.filter { item ->
            (selectedSeverity == "All" || item.severity == selectedSeverity) &&
                (q.isBlank() || "${item.cveId} ${item.title} ${item.product} ${item.vendor}".contains(q, ignoreCase = true))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { repository.seedIfEmpty() }
    }

    fun setQuery(value: String) { query.value = value }
    fun setSeverity(value: String) { severity.value = value }
    fun select(item: VulnerabilityEntity?) { _selected.value = item }

    fun createLab(item: VulnerabilityEntity) {
        viewModelScope.launch {
            repository.createLab(item)
            _selected.value = null
            _message.value = "Practice lab manifest created. Review and approve it before provisioning."
        }
    }

    fun updateLabStatus(lab: LabEntity, status: String) {
        viewModelScope.launch {
            repository.updateLabStatus(lab, status)
            _message.value = "${lab.name}: $status"
        }
    }

    fun snapshot(lab: LabEntity) {
        viewModelScope.launch {
            repository.snapshotLab(lab, "Manual baseline")
            _message.value = "Snapshot captured locally for ${lab.cveId}."
        }
    }

    fun clearMessage() { _message.value = null }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return VulnForgeViewModel(container.vulnForgeRepository) as T
        }
    }
}
