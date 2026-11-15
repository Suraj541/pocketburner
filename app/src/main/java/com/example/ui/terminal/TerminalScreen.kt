package com.example.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.engine.LogDirection
import com.example.ui.components.ConnectionStatusPill
import com.example.ui.components.EncryptionTogglePill
import com.example.ui.theme.LocalHardwareStatusColors
import com.example.ui.theme.MonoTextStyles
import com.example.ui.theme.TerminalEncColor
import com.example.ui.theme.TerminalErrColor
import com.example.ui.theme.TerminalRxColor
import com.example.ui.theme.TerminalSysColor
import com.example.ui.theme.TerminalTextDark
import com.example.ui.theme.TerminalTxColor

@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel,
    onNavigateConnect: () -> Unit,
    onOpenNano: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val history by viewModel.commandHistory.collectAsStateWithLifecycle()
    val lineEnding by viewModel.lineEnding.collectAsStateWithLifecycle()

    var input by remember { mutableStateOf("") }
    var historyCursor by remember { mutableIntStateOf(-1) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.openNanoEvents.collect { fileName ->
            onOpenNano(fileName)
        }
    }

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.lastIndex)
        }
    }

    val statusColors = LocalHardwareStatusColors.current

    Column(modifier = modifier.fillMaxSize()) {
        // Header + Encryption ON/OFF Toggle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            ConnectionStatusPill(connectionState = connectionState, onClick = onNavigateConnect)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                EncryptionTogglePill(
                    enabled = settings.encryptionEnabled,
                    onToggle = viewModel::toggleEncryption,
                )
                IconButton(onClick = { viewModel.submitInput("help") }) {
                    Icon(Icons.Filled.HelpOutline, contentDescription = "Terminal Help")
                }
                IconButton(onClick = viewModel::clearTerminal) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear Terminal")
                }
            }
        }

        // Quick Task Macros + Nano CLI shortcut
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            item {
                AssistChip(
                    onClick = { viewModel.submitInput("nano main.py") },
                    leadingIcon = { Icon(Icons.Filled.Code, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("nano main.py") },
                    modifier = Modifier.testTag("terminal_nano_chip"),
                )
            }
            items(viewModel.quickMacros) { macro ->
                AssistChip(
                    onClick = { viewModel.sendMacro(macro) },
                    label = { Text(macro.label) },
                )
            }
        }

        // Console Log Viewport
        Surface(
            color = statusColors.terminalBackground,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("terminal_log_list"),
            ) {
                items(logs, key = { it.id }) { entry ->
                    val prefixColor = when (entry.direction) {
                        LogDirection.RX -> TerminalRxColor
                        LogDirection.TX -> TerminalTxColor
                        LogDirection.ENC -> TerminalEncColor
                        LogDirection.SYS -> TerminalSysColor
                        LogDirection.ERR -> TerminalErrColor
                    }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "[${entry.direction.name}] ${entry.message}",
                            style = MonoTextStyles.TerminalLine,
                            color = prefixColor,
                        )
                        entry.detail?.let { detail ->
                            Text(
                                text = "   └─ $detail",
                                style = MonoTextStyles.HexBadge,
                                color = TerminalTextDark.copy(alpha = 0.68f),
                            )
                        }
                    }
                }
            }
        }

        // Command Input Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            IconButton(
                onClick = {
                    if (history.isNotEmpty()) {
                        val nextIdx = if (historyCursor < 0) history.lastIndex else (historyCursor - 1).coerceAtLeast(0)
                        historyCursor = nextIdx
                        input = history[nextIdx]
                    }
                },
            ) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Previous command")
            }

            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it
                    historyCursor = -1
                },
                placeholder = { Text("Command or 'nano script.py'...") },
                textStyle = MonoTextStyles.TerminalLine,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (input.isNotBlank()) {
                        viewModel.submitInput(input)
                        input = ""
                    }
                }),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("terminal_command_input"),
            )

            Surface(
                onClick = {
                    val next = when (lineEnding) {
                        "\n" -> "\r\n"
                        "\r\n" -> ""
                        else -> "\n"
                    }
                    viewModel.setLineEnding(next)
                },
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                val label = when (lineEnding) {
                    "\n" -> "\\n"
                    "\r\n" -> "\\r\\n"
                    else -> "Raw"
                }
                Text(
                    text = label,
                    style = MonoTextStyles.HexBadge,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                )
            }

            IconButton(
                onClick = {
                    if (input.isNotBlank()) {
                        viewModel.submitInput(input)
                        input = ""
                    }
                },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                    .testTag("terminal_send_button"),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send command",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}
