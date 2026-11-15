package com.example.ui.forge

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.engine.AgentExecutionState
import com.example.engine.AgentStepOutput
import com.example.engine.agent.IotFramework
import com.example.hardware.HardwareBoardSpec
import com.example.ui.theme.LocalHardwareStatusColors
import com.example.ui.theme.MonoTextStyles

/**
 * Dedicated UI panel monitoring multi-agent execution progress, active framework specializations
 * (ESP-IDF, Arduino, MicroPython, Raspberry Pi, ESPHome), hardware diagnostics, and timing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MultiAgentMonitorPanel(
    board: HardwareBoardSpec,
    framework: IotFramework,
    steps: List<AgentStepOutput>,
    isRunning: Boolean,
    modifier: Modifier = Modifier,
) {
    val statusColors = LocalHardwareStatusColors.current
    val totalSteps = steps.size.coerceAtLeast(1)
    val completedSteps = steps.count { it.state == AgentExecutionState.COMPLETED }
    val activeStep = steps.firstOrNull { it.state == AgentExecutionState.RUNNING }
    val failedStep = steps.firstOrNull { it.state == AgentExecutionState.FAILED }
    val totalElapsedMs = steps.sumOf { it.durationMs }

    val progressFraction = when {
        completedSteps == totalSteps -> 1f
        activeStep != null -> (completedSteps + 0.45f) / totalSteps
        else -> completedSteps.toFloat() / totalSteps
    }

    ElevatedCard(
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("multi_agent_monitor_panel"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Monitor Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.Filled.Hub,
                        contentDescription = "Multi-Agent Monitor",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Multi-Agent Monitor Panel", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = when {
                                failedStep != null -> "Halted at ${failedStep.agentDisplayName}"
                                activeStep != null -> activeStep.phaseDetail.ifBlank { "Executing ${activeStep.agentDisplayName}…" }
                                completedSteps == totalSteps -> "All $totalSteps agents verified (${"%.1f".format(totalElapsedMs / 1000f)}s)"
                                else -> "Pipeline staged for ${board.id} • ${framework.displayName}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (failedStep != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = framework.badge,
                        style = MonoTextStyles.HexBadge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            // Overall Progress Telemetry
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Pipeline Progress ($completedSteps / $totalSteps agents)",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = "${(progressFraction * 100).toInt()}%",
                        style = MonoTextStyles.HexBadge.copy(
                            color = if (completedSteps == totalSteps) statusColors.connected else MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                )
            }

            // Individual Specialized Agent Monitor Rows
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                steps.forEach { step ->
                    AgentMonitorCard(step = step, isPipelineActive = isRunning)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AgentMonitorCard(
    step: AgentStepOutput,
    isPipelineActive: Boolean,
) {
    var expanded by remember(step.state) {
        mutableStateOf(step.state == AgentExecutionState.FAILED)
    }
    val statusColors = LocalHardwareStatusColors.current

    val borderColor = when (step.state) {
        AgentExecutionState.RUNNING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
        AgentExecutionState.COMPLETED -> statusColors.connected.copy(alpha = 0.55f)
        AgentExecutionState.FAILED -> MaterialTheme.colorScheme.error.copy(alpha = 0.75f)
        AgentExecutionState.PENDING -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(
            alpha = if (step.state == AgentExecutionState.RUNNING) 0.65f else 0.38f
        ),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("agent_monitor_${step.role.name.lowercase()}"),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    when (step.state) {
                        AgentExecutionState.RUNNING -> CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                        AgentExecutionState.COMPLETED -> Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "Completed",
                            tint = statusColors.connected,
                            modifier = Modifier.size(18.dp),
                        )
                        AgentExecutionState.FAILED -> Icon(
                            Icons.Filled.ErrorOutline,
                            contentDescription = "Failed",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                        AgentExecutionState.PENDING -> Icon(
                            Icons.Filled.Build,
                            contentDescription = if (isPipelineActive) "Queued" else "Ready",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = step.agentDisplayName.ifBlank { step.role.title },
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = step.phaseDetail.ifBlank { step.role.mission },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (step.durationMs > 0L) {
                    Text(
                        text = "${"%.1f".format(step.durationMs / 1000f)}s",
                        style = MonoTextStyles.HexBadge,
                    )
                    Spacer(Modifier.width(4.dp))
                }

                if (step.outputText.isNotBlank() || !step.errorMessage.isNullOrBlank()) {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = "Toggle agent output",
                        )
                    }
                }
            }

            // Specialization + Model metadata badges
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (step.frameworkBadge.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = step.frameworkBadge,
                            style = MonoTextStyles.HexBadge.copy(color = MaterialTheme.colorScheme.onSecondaryContainer),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                if (step.modelId.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = step.modelId,
                            style = MonoTextStyles.HexBadge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            // Diagnostic pills extracted by the agent
            if (step.diagnostics.isNotEmpty() && step.state == AgentExecutionState.COMPLETED) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    step.diagnostics.take(3).forEach { diag ->
                        Surface(
                            color = statusColors.connected.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                text = diag,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HorizontalDivider()
                    Text(
                        text = step.errorMessage ?: step.outputText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (step.errorMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
