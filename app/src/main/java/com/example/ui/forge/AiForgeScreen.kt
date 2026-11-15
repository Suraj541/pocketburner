package com.example.ui.forge

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.engine.AgentExecutionState
import com.example.engine.AgentRole
import com.example.engine.AgentStepOutput
import com.example.engine.AiProviderPreset
import com.example.engine.FirmwareTargetFormat
import com.example.engine.MultiAgentSynthesisResult
import com.example.engine.agent.IotFramework
import com.example.hardware.HardwareRegistry
import com.example.ui.components.HardwareSpecInspectorDialog
import com.example.ui.theme.LocalHardwareStatusColors
import com.example.ui.theme.MonoTextStyles

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiForgeScreen(
    viewModel: AiForgeViewModel,
    onOpenInNano: (fileName: String, code: String, targetBoardId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val filteredModels by viewModel.filteredModels.collectAsStateWithLifecycle()
    val scannedModels by viewModel.scannedModels.collectAsStateWithLifecycle()
    val isScanningModels by viewModel.isScanningModels.collectAsStateWithLifecycle()
    val scanError by viewModel.scanError.collectAsStateWithLifecycle()
    val modelSearchQuery by viewModel.modelSearchQuery.collectAsStateWithLifecycle()
    val targetBoardId by viewModel.targetBoardId.collectAsStateWithLifecycle()
    val targetFramework by viewModel.targetFramework.collectAsStateWithLifecycle()
    val targetFormat by viewModel.targetFormat.collectAsStateWithLifecycle()
    val taskPrompt by viewModel.taskPrompt.collectAsStateWithLifecycle()
    val roleOverrides by viewModel.roleModelOverrides.collectAsStateWithLifecycle()
    val agentSteps by viewModel.agentSteps.collectAsStateWithLifecycle()
    val isRunningAgents by viewModel.isRunningAgents.collectAsStateWithLifecycle()
    val synthesisResult by viewModel.synthesisResult.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    val currentProvider = AiProviderPreset.fromId(settings.aiProvider)
    val selectedBoard = remember(targetBoardId) {
        HardwareRegistry.findById(targetBoardId) ?: HardwareRegistry.boards.first()
    }

    var revealApiKey by remember { mutableStateOf(false) }
    var showRegistryDialog by remember { mutableStateOf(false) }
    var showPerAgentMatrix by remember { mutableStateOf(false) }
    var boardSearchQuery by remember { mutableStateOf("") }
    val quickFilteredBoards = remember(boardSearchQuery) {
        HardwareRegistry.searchBoards(boardSearchQuery)
    }

    val taskPresets = remember {
        listOf(
            "BLE UART Sensor Gateway" to "Create a BLE Nordic UART (NUS) service transmitting periodic sensor readings as JSON and handling LED/GPIO relay commands from PocketBurner.",
            "Wi-Fi + TLS Telemetry" to "Connect to Wi-Fi, expose an encrypted telemetry command loop over TCP/TLS, and drive onboard status LED + I2C sensor readout.",
            "OLED Dashboard + Button Menu" to "Initialize onboard I2C/SPI display and physical buttons with a clean 3-page hardware diagnostic menu and live ADC/heap readout.",
            "RPi Systemd Telemetry Agent" to "Write a resilient Python script with GPIO pin control, CPU temperature monitoring, and TCP command listener ready to run as a systemd service.",
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        // Header
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Multi-Agent Firmware Forge", style = MaterialTheme.typography.headlineSmall)
                    }
                    Text(
                        "4-Agent Hardware Architect • Engineer • Auditor • Packager",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Status / Error Banner
        val bannerText = scanError ?: statusMessage
        if (!bannerText.isNullOrBlank()) {
            val isError = scanError != null
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(12.dp),
                    ) {
                        Text(bannerText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = viewModel::clearStatusMessage) { Text("OK") }
                    }
                }
            }
        }

        // 1. Free API Key & :free Model Scanner Card
        item {
            ElevatedCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("1. Free API Key & :free Model Scanner", style = MaterialTheme.typography.titleMedium)
                    Text(
                        currentProvider.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(AiProviderPreset.entries) { preset ->
                            FilterChip(
                                selected = currentProvider == preset,
                                onClick = { viewModel.selectProvider(preset) },
                                label = { Text(preset.label) },
                            )
                        }
                    }

                    OutlinedTextField(
                        value = settings.aiApiKey,
                        onValueChange = viewModel::updateApiKey,
                        label = { Text("Free API Key (${currentProvider.label})") },
                        placeholder = { Text("Paste OpenRouter / Groq / Gemini / Custom key…") },
                        leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { revealApiKey = !revealApiKey }) {
                                Icon(
                                    imageVector = if (revealApiKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (revealApiKey) "Hide API Key" else "Show API Key",
                                )
                            }
                        },
                        visualTransformation = if (revealApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("forge_api_key_input"),
                    )

                    OutlinedTextField(
                        value = settings.aiCustomBaseUrl,
                        onValueChange = viewModel::updateBaseUrl,
                        label = { Text("API Base URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Button(
                            onClick = viewModel::scanModelsForApi,
                            enabled = !isScanningModels,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("forge_scan_models_button"),
                        ) {
                            if (isScanningModels) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Scanning API…")
                            } else {
                                Icon(Icons.Filled.Radar, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Scan Models for API")
                            }
                        }

                        FilterChip(
                            selected = settings.aiFreeOnlyFilter,
                            onClick = { viewModel.setFreeOnlyFilter(!settings.aiFreeOnlyFilter) },
                            label = { Text(":free Only") },
                            modifier = Modifier.testTag("forge_free_only_chip"),
                        )
                    }

                    if (settings.aiSelectedModelId.isNotBlank()) {
                        val isFreeModel = scannedModels.firstOrNull { it.id == settings.aiSelectedModelId }?.isFree
                            ?: settings.aiSelectedModelId.endsWith(":free", ignoreCase = true)
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(10.dp),
                            ) {
                                if (isFreeModel) {
                                    FreeModelTag()
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(
                                    text = settings.aiSelectedModelId,
                                    style = MonoTextStyles.HexBadge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    if (scannedModels.isNotEmpty()) {
                        OutlinedTextField(
                            value = modelSearchQuery,
                            onValueChange = viewModel::setModelSearchQuery,
                            placeholder = { Text("Search scanned models (qwen, llama, gemma, deepseek, :free)…") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            trailingIcon = {
                                if (modelSearchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setModelSearchQuery("") }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Clear model search")
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("forge_model_search_input"),
                        )

                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            filteredModels.take(12).forEach { model ->
                                val selected = settings.aiSelectedModelId == model.id
                                Surface(
                                    onClick = { viewModel.selectModel(model.id) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    },
                                    border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    ) {
                                        if (model.isFree) {
                                            FreeModelTag()
                                            Spacer(Modifier.width(8.dp))
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = model.displayName,
                                                style = MaterialTheme.typography.labelLarge,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = if (model.contextLength > 0) {
                                                    "${model.id} • ${model.contextLength / 1024}k ctx"
                                                } else {
                                                    model.id
                                                },
                                                style = MonoTextStyles.HexBadge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        if (selected) {
                                            Icon(
                                                Icons.Filled.CheckCircle,
                                                contentDescription = "Selected model",
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                            }
                            if (filteredModels.size > 12) {
                                Text(
                                    "Showing top 12 of ${filteredModels.size} scanned models — narrow via search above.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Target Hardware & Work Specification Card
        item {
            ElevatedCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("2. Hardware & Firmware Task", style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(onClick = { showRegistryDialog = true }) {
                            Icon(Icons.Filled.Memory, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Registry")
                        }
                    }

                    OutlinedTextField(
                        value = boardSearchQuery,
                        onValueChange = { boardSearchQuery = it },
                        placeholder = { Text("Search board / chip / USB VID:PID…") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("forge_board_search_input"),
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(quickFilteredBoards.take(25)) { board ->
                            FilterChip(
                                selected = selectedBoard.id == board.id,
                                onClick = { viewModel.selectTargetBoard(board.id) },
                                label = { Text(board.id) },
                            )
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "${selectedBoard.displayName} (${selectedBoard.chip})",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                "${selectedBoard.protocol.label} • Offset ${selectedBoard.appOffset} • ${selectedBoard.logicVoltage}",
                                style = MonoTextStyles.HexBadge,
                            )
                            Text(
                                "GPIO: ${selectedBoard.gpioPinoutSummary}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Text("IoT Agent Framework Specialization", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(IotFramework.entries) { fw ->
                            FilterChip(
                                selected = targetFramework == fw,
                                onClick = { viewModel.selectTargetFramework(fw) },
                                label = { Text(fw.displayName) },
                                modifier = Modifier.testTag("forge_framework_${fw.id}"),
                            )
                        }
                    }
                    Text(
                        text = targetFramework.toolchainSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Text("Output File Format", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(FirmwareTargetFormat.entries) { fmt ->
                            FilterChip(
                                selected = targetFormat == fmt,
                                onClick = { viewModel.selectTargetFormat(fmt) },
                                label = { Text(fmt.label) },
                            )
                        }
                    }

                    Text("IoT Task Quick Presets", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(taskPresets) { (title, body) ->
                            FilterChip(
                                selected = taskPrompt == body,
                                onClick = { viewModel.updateTaskPrompt(body) },
                                label = { Text(title) },
                            )
                        }
                    }

                    OutlinedTextField(
                        value = taskPrompt,
                        onValueChange = viewModel::updateTaskPrompt,
                        label = { Text("Describe the exact IoT work for ${selectedBoard.id}") },
                        placeholder = { Text("e.g., Read DHT22 + I2C OLED status display, host BLE UART command control, and deep-sleep between polls") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("forge_task_prompt_input"),
                    )

                    // Optional per-agent model overrides
                    TextButton(
                        onClick = { showPerAgentMatrix = !showPerAgentMatrix },
                        modifier = Modifier.align(Alignment.Start),
                    ) {
                        Icon(
                            imageVector = if (showPerAgentMatrix) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Multi-Agent :free Model Assignment (${roleOverrides.size} custom)")
                    }

                    AnimatedVisibility(visible = showPerAgentMatrix) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val freeChoices = scannedModels.filter { !settings.aiFreeOnlyFilter || it.isFree }.take(8)
                            AgentRole.entries.forEach { role ->
                                val overrideId = roleOverrides[role]
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(role.title, style = MaterialTheme.typography.labelMedium)
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        item {
                                            FilterChip(
                                                selected = overrideId.isNullOrBlank(),
                                                onClick = { viewModel.setRoleModelOverride(role, null) },
                                                label = { Text("Default") },
                                            )
                                        }
                                        items(freeChoices) { m ->
                                            FilterChip(
                                                selected = overrideId == m.id,
                                                onClick = { viewModel.setRoleModelOverride(role, m.id) },
                                                label = { Text(if (m.isFree) ":free ${m.displayName.take(20)}" else m.displayName.take(20)) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (isRunningAgents) viewModel.cancelBuild() else viewModel.runMultiAgentBuild()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("forge_run_agents_button"),
                    ) {
                        if (isRunningAgents) {
                            Icon(Icons.Filled.Stop, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Stop Multi-Agent Pipeline")
                        } else {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Run 4-Agent Build for ${selectedBoard.id}")
                        }
                    }
                }
            }
        }

        // 3. Multi-Agent Progress & Framework Specialization Monitor Panel
        item {
            MultiAgentMonitorPanel(
                board = selectedBoard,
                framework = targetFramework,
                steps = agentSteps,
                isRunning = isRunningAgents,
            )
        }

        // 4. Final Synthesized Output Card
        synthesisResult?.let { result ->
            item {
                SynthesisOutputCard(
                    result = result,
                    onOpenInNano = {
                        onOpenInNano(result.suggestedFilename, result.finalCode, result.board.id)
                    },
                    onSaveToVault = viewModel::saveResultToScriptVault,
                    onSaveToCatalog = viewModel::saveResultToFirmwareCatalog,
                )
            }
        }
    }

    if (showRegistryDialog) {
        HardwareSpecInspectorDialog(
            initialBoardId = selectedBoard.id,
            onDismiss = { showRegistryDialog = false },
            onSelectBoard = { viewModel.selectTargetBoard(it.id) },
        )
    }
}

@Composable
private fun FreeModelTag() {
    val statusColors = LocalHardwareStatusColors.current
    Surface(
        color = statusColors.connected.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, statusColors.connected.copy(alpha = 0.65f)),
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = ":free",
            style = MonoTextStyles.HexBadge.copy(
                fontWeight = FontWeight.Bold,
                color = statusColors.connected,
            ),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SynthesisOutputCard(
    result: MultiAgentSynthesisResult,
    onOpenInNano: () -> Unit,
    onSaveToVault: () -> Unit,
    onSaveToCatalog: () -> Unit,
) {
    var activeTab by remember { mutableIntStateOf(0) }
    ElevatedCard(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Synthesized Firmware Output", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${result.suggestedFilename} • ${result.board.displayName} (${result.board.appOffset})",
                        style = MonoTextStyles.HexBadge,
                    )
                }
            }

            SecondaryTabRow(selectedTabIndex = activeTab) {
                listOf("Code", "Pin Map", "Audit", "Flash Guide").forEachIndexed { idx, label ->
                    Tab(
                        selected = activeTab == idx,
                        onClick = { activeTab = idx },
                        text = { Text(label, maxLines = 1) },
                    )
                }
            }

            val tabText = when (activeTab) {
                0 -> result.finalCode
                1 -> result.pinoutPlan
                2 -> result.auditSummary
                else -> result.buildAndFlashInstructions
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .heightIn(min = 140.dp, max = 320.dp)
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp),
                ) {
                    Text(
                        text = tabText,
                        style = if (activeTab == 0) MonoTextStyles.TerminalLine else MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = onOpenInNano,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("forge_open_nano_button"),
                ) {
                    Icon(Icons.Filled.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open in Nano")
                }
                FilledTonalButton(
                    onClick = onSaveToCatalog,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("forge_save_catalog_button"),
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Firmware")
                }
            }
            OutlinedButton(
                onClick = onSaveToVault,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save Copy to Script Vault")
            }
        }
    }
}
