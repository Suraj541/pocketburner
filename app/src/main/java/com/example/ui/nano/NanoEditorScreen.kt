package com.example.ui.nano

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import com.example.data.local.ScriptEntity
import com.example.engine.ActiveConnection
import com.example.engine.CodeDiagnostic
import com.example.engine.DiagnosticSeverity
import com.example.engine.ScriptLanguage
import com.example.engine.ScriptSyntaxEngine
import com.example.engine.ScriptTransferMode
import com.example.engine.SyntaxAnalysisResult
import com.example.hardware.HardwareRegistry
import com.example.ui.components.ConnectionStatusPill
import com.example.ui.components.EncryptionTogglePill
import com.example.ui.theme.LocalHardwareStatusColors
import com.example.ui.theme.MonoTextStyles
import com.example.ui.theme.TerminalTextDark

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NanoEditorScreen(
    viewModel: NanoViewModel,
    onNavigateConnect: () -> Unit,
    onToggleEncryption: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val savedScripts by viewModel.savedScripts.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var showHelperPanel by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
    var showProblemsPanel by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showVaultDialog by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var activeLineIndex by remember { mutableIntStateOf(0) }

    var textFieldValue by remember(uiState.code, uiState.fileName) {
        mutableStateOf(TextFieldValue(text = uiState.code, selection = TextRange(uiState.code.length)))
    }

    val safExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
        onResult = { uri -> uri?.let { viewModel.exportToUri(it) } }
    )

    val statusColors = LocalHardwareStatusColors.current
    val analysis = uiState.syntaxAnalysis
    val lineCount = remember(uiState.code) { uiState.code.lines().size.coerceAtLeast(1) }
    val gutterLineHeightDp = with(LocalDensity.current) { MonoTextStyles.CodeEditor.lineHeight.toDp() }

    Column(modifier = modifier.fillMaxSize()) {
        // GNU Nano-Inspired Top Header Bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Surface(
                        onClick = { showRenameDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = "nano • ${uiState.fileName}${if (uiState.isModified) " *" else ""}",
                            style = MonoTextStyles.HexBadge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        ConnectionStatusPill(connectionState = connectionState, onClick = onNavigateConnect)
                        EncryptionTogglePill(enabled = settings.encryptionEnabled, onToggle = onToggleEncryption)
                    }
                }

                Spacer(Modifier.height(6.dp))
                // Language Picker Chips (write script in ANY language!)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ScriptLanguage.entries) { lang ->
                        FilterChip(
                            selected = uiState.language == lang,
                            onClick = { viewModel.selectLanguage(lang) },
                            label = { Text(lang.displayName, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        }

        // ^W Where Is (Search Bar)
        AnimatedVisibility(visible = showSearchBar) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search in ${uiState.fileName}...") },
                    singleLine = true,
                    textStyle = MonoTextStyles.TerminalLine,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                val matches = remember(searchQuery, uiState.code) {
                    if (searchQuery.isBlank()) 0
                    else Regex(Regex.escape(searchQuery), RegexOption.IGNORE_CASE).findAll(uiState.code).count()
                }
                Text("$matches hits", style = MonoTextStyles.HexBadge)
                IconButton(onClick = { showSearchBar = false }) {
                    Icon(Icons.Filled.Close, contentDescription = "Close search")
                }
            }
        }

        // ^G Nano Language Helper Panel (Snippets + Syntax Guide)
        AnimatedVisibility(visible = showHelperPanel) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "Nano Helper — ${uiState.language.displayName} Snippets",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Row {
                            TextButton(onClick = { viewModel.loadStarterTemplate(uiState.language) }) {
                                Text("Reset Template")
                            }
                            IconButton(onClick = { showHelperPanel = false }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "Close helper")
                            }
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uiState.language.helperCheatsheet) { (title, snippet) ->
                            Surface(
                                onClick = { viewModel.insertSnippet(snippet) },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                            ) {
                                Text(
                                    text = "+ $title",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Code Editor Viewport with Line Numbers, Error Gutter Markers & Multi-Language Highlighting
        val scrollState = rememberScrollState()
        val horizontalScroll = rememberScrollState()
        val syntaxTransformation = remember(uiState.language, searchQuery, analysis) {
            SyntaxHighlightTransformation(uiState.language, searchQuery, analysis)
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(statusColors.terminalBackground),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(vertical = 10.dp),
            ) {
                // Line number + diagnostic marker gutter
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier
                        .background(Color(0xFF060A0E))
                        .padding(start = 6.dp, end = 8.dp)
                        .testTag("nano_line_gutter"),
                ) {
                    for (line in 1..lineCount) {
                        val lineIdx = line - 1
                        val lineDiags = analysis.diagnosticsByLine[lineIdx].orEmpty()
                        val hasError = lineDiags.any { it.severity == DiagnosticSeverity.ERROR }
                        val hasWarning = !hasError && lineDiags.any { it.severity == DiagnosticSeverity.WARNING }
                        val isActive = lineIdx == activeLineIndex
                        val rowBg = when {
                            hasError -> Color(0x33EF4444)
                            hasWarning -> Color(0x28F59E0B)
                            isActive -> Color(0x1E38BDF8)
                            else -> Color.Transparent
                        }
                        val numColor = when {
                            hasError -> Color(0xFFF87171)
                            hasWarning -> Color(0xFFFBBF24)
                            isActive -> Color(0xFF38BDF8)
                            else -> Color(0xFF64748B)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .height(gutterLineHeightDp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(rowBg)
                                .clickable(enabled = lineDiags.isNotEmpty()) {
                                    val target = lineDiags.first()
                                    val safeOffset = target.startOffset.coerceIn(0, textFieldValue.text.length)
                                    textFieldValue = textFieldValue.copy(selection = TextRange(safeOffset))
                                    activeLineIndex = target.lineIndex
                                    showProblemsPanel = true
                                }
                                .padding(horizontal = 3.dp)
                                .testTag("gutter_line_$line"),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(10.dp),
                            ) {
                                when {
                                    hasError -> Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFF87171))
                                            .testTag("gutter_error_marker_$line"),
                                    )
                                    hasWarning -> Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFFBBF24))
                                            .testTag("gutter_warning_marker_$line"),
                                    )
                                }
                            }
                            Text(
                                text = line.toString(),
                                style = MonoTextStyles.CodeEditor,
                                color = numColor,
                            )
                        }
                    }
                }

                // Vertical gutter divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF1E293B)),
                )

                // Monospace Editable Buffer
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { updated ->
                        textFieldValue = updated
                        val cursor = updated.selection.start.coerceIn(0, updated.text.length)
                        activeLineIndex = updated.text.substring(0, cursor).count { it == '\n' }
                        if (updated.text != uiState.code) {
                            viewModel.updateCode(updated.text)
                        }
                    },
                    textStyle = MonoTextStyles.CodeEditor.copy(color = TerminalTextDark),
                    cursorBrush = SolidColor(Color(0xFF38BDF8)),
                    visualTransformation = syntaxTransformation,
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(horizontalScroll)
                        .padding(start = 8.dp, end = 12.dp)
                        .testTag("nano_code_editor"),
                )
            }
        }

        // Collapsible Diagnostics / Problems Drawer
        AnimatedVisibility(visible = showProblemsPanel && analysis.diagnostics.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("nano_problems_drawer"),
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Diagnostics (${analysis.errorCount} errors, ${analysis.warningCount} warnings)",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        IconButton(onClick = { showProblemsPanel = false }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Close diagnostics", modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.height((analysis.diagnostics.size.coerceAtMost(4) * 38).dp),
                    ) {
                        items(analysis.diagnostics, key = { "${it.lineIndex}:${it.column}:${it.ruleCode}" }) { diag ->
                            DiagnosticRow(
                                diagnostic = diag,
                                onJump = {
                                    val safeOffset = diag.startOffset.coerceIn(0, textFieldValue.text.length)
                                    textFieldValue = textFieldValue.copy(selection = TextRange(safeOffset))
                                    activeLineIndex = diag.lineIndex
                                },
                            )
                        }
                    }
                }
            }
        }

        if (uiState.isTransferring) {
            LinearProgressIndicator(progress = { uiState.transferProgress }, modifier = Modifier.fillMaxWidth())
        }

        // Nano Status Line
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(
                    text = uiState.statusBarMessage,
                    style = MonoTextStyles.HexBadge,
                    modifier = Modifier.weight(1f),
                )
                DiagnosticsBadgePill(
                    errorCount = analysis.errorCount,
                    warningCount = analysis.warningCount,
                    onClick = {
                        if (analysis.diagnostics.isNotEmpty()) {
                            showProblemsPanel = !showProblemsPanel
                        }
                    },
                )
                Text(
                    text = "Ln ${activeLineIndex + 1}/$lineCount • ${uiState.targetDeviceId}",
                    style = MonoTextStyles.HexBadge,
                )
            }
        }

        // Authentic Two-Row Nano Shortcut Keycaps (^G, ^O, ^W, ^K, ^U, ^T, ^L, ^X)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                NanoKeycap("^G", "Help", testTag = "nano_key_help") { showHelperPanel = !showHelperPanel }
                NanoKeycap("^O", "Write", testTag = "nano_key_save") { viewModel.saveToLocalStorage() }
                NanoKeycap("^W", "Where", testTag = "nano_key_search") { showSearchBar = !showSearchBar }
                NanoKeycap("^K", "Cut", testTag = "nano_key_cut") { viewModel.cutLineAtIndex(activeLineIndex) }
                NanoKeycap("^U", "Paste", testTag = "nano_key_paste") { viewModel.pasteCutBuffer(activeLineIndex) }
                NanoKeycap("^T", "Transfer", highlighted = true, testTag = "nano_key_transfer") {
                    showTransferDialog = true
                }
                NanoKeycap("^L", "Vault (${savedScripts.size})", testTag = "nano_key_vault") {
                    showVaultDialog = true
                }
                NanoKeycap("^X", "Export", testTag = "nano_key_export") {
                    safExportLauncher.launch(uiState.fileName)
                }
            }
        }
    }

    if (showRenameDialog) {
        var newName by remember { mutableStateOf(uiState.fileName) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Script Filename & Target") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Filename (.py, .ino, .sh, .js, .lua, .yaml)") },
                        singleLine = true,
                    )
                    Text("Target Board", style = MaterialTheme.typography.labelLarge)
                    var boardSearchQuery by remember { mutableStateOf("") }
                    val filteredBoards = remember(boardSearchQuery) {
                        HardwareRegistry.searchBoards(boardSearchQuery)
                    }
                    OutlinedTextField(
                        value = boardSearchQuery,
                        onValueChange = { boardSearchQuery = it },
                        placeholder = { Text("Search board or chip…") },
                        singleLine = true,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(filteredBoards) { b ->
                            FilterChip(
                                selected = uiState.targetDeviceId == b.id,
                                onClick = { viewModel.selectTargetDevice(b.id) },
                                label = { Text(b.id) },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateFileName(newName)
                    showRenameDialog = false
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") } },
        )
    }

    if (showVaultDialog) {
        ScriptVaultDialog(
            scripts = savedScripts,
            onDismiss = { showVaultDialog = false },
            onOpen = {
                viewModel.loadExistingScript(it)
                showVaultDialog = false
            },
            onDelete = viewModel::deleteScript,
        )
    }

    if (showTransferDialog) {
        TransferScriptDialog(
            fileName = uiState.fileName,
            connectionState = connectionState,
            encryptionEnabled = settings.encryptionEnabled,
            onDismiss = { showTransferDialog = false },
            onNavigateConnect = {
                showTransferDialog = false
                onNavigateConnect()
            },
            onTransfer = { mode ->
                showTransferDialog = false
                viewModel.transferToDevice(mode)
            },
        )
    }
}

@Composable
private fun NanoKeycap(
    shortcut: String,
    label: String,
    highlighted: Boolean = false,
    testTag: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag(testTag),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Text(
                text = shortcut,
                style = MonoTextStyles.HexBadge,
                fontWeight = FontWeight.Bold,
                color = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ScriptVaultDialog(
    scripts: List<ScriptEntity>,
    onDismiss: () -> Unit,
    onOpen: (ScriptEntity) -> Unit,
    onDelete: (ScriptEntity) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Local Script Vault (${scripts.size})")
            }
        },
        text = {
            if (scripts.isEmpty()) {
                Text("No scripts saved locally yet. Tap ^O Write in the Nano editor to save your first script.")
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(280.dp),
                ) {
                    items(scripts, key = { it.id }) { item ->
                        Surface(
                            onClick = { onOpen(item) },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(10.dp)) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.fileName, style = MonoTextStyles.TerminalLine)
                                    Text(
                                        "${item.language} • ${item.sizeBytes} B • ${item.targetDeviceId}",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                                IconButton(onClick = { onDelete(item) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete script")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun TransferScriptDialog(
    fileName: String,
    connectionState: ActiveConnection,
    encryptionEnabled: Boolean,
    onDismiss: () -> Unit,
    onNavigateConnect: () -> Unit,
    onTransfer: (ScriptTransferMode) -> Unit,
) {
    var selectedMode by remember {
        mutableStateOf(
            when {
                fileName.endsWith(".sh", true) -> ScriptTransferMode.LINUX_BASE64_SHELL
                fileName.endsWith(".py", true) -> ScriptTransferMode.MICROPYTHON_RAW_REPL
                else -> ScriptTransferMode.DIRECT_STREAM
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transfer $fileName to Device") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ConnectionStatusPill(connectionState = connectionState, onClick = onNavigateConnect)
                Text(
                    text = if (encryptionEnabled) "Payload will be AES-256-GCM + TLS encrypted." else "Payload will stream in plaintext.",
                    style = MaterialTheme.typography.bodySmall,
                )
                HorizontalDivider()
                ScriptTransferMode.entries.forEach { mode ->
                    Surface(
                        onClick = { selectedMode = mode },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selectedMode == mode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(mode.label, style = MaterialTheme.typography.titleSmall)
                            Text(mode.subtitle, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onTransfer(selectedMode) },
                modifier = Modifier.testTag("confirm_transfer_script_button"),
            ) {
                Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Transfer Now")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DiagnosticsBadgePill(
    errorCount: Int,
    warningCount: Int,
    onClick: () -> Unit,
) {
    val containerColor = when {
        errorCount > 0 -> MaterialTheme.colorScheme.errorContainer
        warningCount > 0 -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = when {
        errorCount > 0 -> MaterialTheme.colorScheme.onErrorContainer
        warningCount > 0 -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    val label = when {
        errorCount > 0 && warningCount > 0 -> "$errorCount err • $warningCount warn"
        errorCount > 0 -> "$errorCount err"
        warningCount > 0 -> "$warningCount warn"
        else -> "Syntax OK"
    }
    Surface(
        onClick = onClick,
        color = containerColor,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.testTag("nano_diagnostics_pill"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        ) {
            Icon(
                imageVector = when {
                    errorCount > 0 -> Icons.Filled.Error
                    warningCount > 0 -> Icons.Filled.Warning
                    else -> Icons.Filled.CheckCircle
                },
                contentDescription = "Syntax diagnostics",
                tint = contentColor,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = label,
                style = MonoTextStyles.HexBadge,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun DiagnosticRow(
    diagnostic: CodeDiagnostic,
    onJump: () -> Unit,
) {
    val isErr = diagnostic.severity == DiagnosticSeverity.ERROR
    Surface(
        onClick = onJump,
        color = if (isErr) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
        else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Icon(
                imageVector = if (isErr) Icons.Filled.Error else Icons.Filled.Warning,
                contentDescription = null,
                tint = if (isErr) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = "Ln ${diagnostic.oneBasedLine}:${diagnostic.oneBasedColumn}",
                style = MonoTextStyles.HexBadge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = diagnostic.message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private class SyntaxHighlightTransformation(
    private val language: ScriptLanguage,
    private val searchQuery: String,
    private val analysis: SyntaxAnalysisResult,
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val highlighted = ScriptSyntaxEngine.buildHighlightedText(
            source = text.text,
            language = language,
            searchQuery = searchQuery,
            analysis = analysis,
        )
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}
