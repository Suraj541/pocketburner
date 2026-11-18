package com.example.ui.vulnforge

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.LabEntity
import com.example.data.local.VulnerabilityEntity

@Composable
fun VulnForgeScreen(viewModel: VulnForgeViewModel) {
    val vulnerabilities by viewModel.vulnerabilities.collectAsStateWithLifecycle()
    val labs by viewModel.labs.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var activeSeverity by remember { mutableStateOf("All") }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.Filled.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(12.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("VulnForge", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Local CVE intelligence & isolated practice", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { }) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh local data") }
                }
            }
            item {
                Text("Overview", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricCard("Indexed CVEs", vulnerabilities.size.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    MetricCard("Critical / High", vulnerabilities.count { it.severity == "Critical" || it.severity == "High" }.toString(), Color(0xFFFFB4AB), Modifier.weight(1f))
                    MetricCard("KEV", vulnerabilities.count { it.isKev }.toString(), Color(0xFFFFC107), Modifier.weight(1f))
                }
            }
            item {
                Text("CVE Explorer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; viewModel.setQuery(it) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text("Search CVE, vendor, product…") },
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All", "Critical", "High").forEach { severity ->
                        FilterChip(
                            selected = activeSeverity == severity,
                            onClick = { activeSeverity = severity; viewModel.setSeverity(severity) },
                            label = { Text(severity) },
                        )
                    }
                }
            }
            items(vulnerabilities, key = { it.id }) { item ->
                VulnerabilityCard(item = item, onClick = { viewModel.select(item) })
            }
            item {
                Text("Active Laboratories", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            if (labs.isEmpty()) {
                item { EmptyState("No labs provisioned", "Create a manifest from a CVE to plan an isolated practice environment.") }
            } else {
                items(labs, key = { it.id }) { lab ->
                    LabCard(lab, onStatus = { viewModel.updateLabStatus(lab, it) }, onSnapshot = { viewModel.snapshot(lab) })
                }
            }
            item {
                Text("Data Sources", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f))) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        sources.forEach { source ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Icon(if (source.enabled) Icons.Filled.CheckCircle else Icons.Filled.CloudOff, contentDescription = null, tint = if (source.enabled) Color(0xFF5CE09C) else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(source.name, fontWeight = FontWeight.SemiBold)
                                    Text(source.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(source.status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            item {
                Text("Change Timeline", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f))) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (events.isEmpty()) Text("No lab events yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        events.take(5).forEach { event ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(event.description, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { item ->
        AlertDialog(
            onDismissRequest = { viewModel.select(null) },
            title = { Text(item.cveId) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.title, fontWeight = FontWeight.Bold)
                    Text(item.description)
                    Text("Affected: ${item.affectedVersions}", style = MaterialTheme.typography.bodySmall)
                    Text("Fixed: ${item.fixedVersions}", style = MaterialTheme.typography.bodySmall)
                    Text("Source: ${item.source}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { Button(onClick = { viewModel.createLab(item) }) { Text("Create practice lab") } },
            dismissButton = { OutlinedButton(onClick = { viewModel.select(null) }) { Text("Close") } },
        )
    }
}

@Composable
private fun MetricCard(label: String, value: String, tint: Color, modifier: Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = .14f))) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = tint)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun VulnerabilityCard(item: VulnerabilityEntity, onClick: () -> Unit) {
    Card(onClick = onClick, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.cveId, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                AssistChip(onClick = {}, label = { Text(item.severity) })
                if (item.isKev) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.Warning, contentDescription = "CISA KEV", tint = Color(0xFFFFC107), modifier = Modifier.size(18.dp))
                }
            }
            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(item.description, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${item.vendor} · ${item.product} · CVSS ${item.cvss ?: "n/a"}", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun LabCard(lab: LabEntity, onStatus: (String) -> Unit, onSnapshot: () -> Unit) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(lab.name, fontWeight = FontWeight.SemiBold)
                    Text("${lab.provider} · ${lab.os} · ${lab.networkMode}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AssistChip(onClick = {}, label = { Text(lab.status) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (lab.status == "Running") {
                    OutlinedButton(onClick = { onStatus("Stopped") }) { Icon(Icons.Filled.Stop, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Stop") }
                } else {
                    Button(onClick = { onStatus("Running") }) { Icon(Icons.Filled.PlayArrow, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Start") }
                }
                OutlinedButton(onClick = onSnapshot) { Icon(Icons.Filled.CameraAlt, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Snapshot") }
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, description: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f))) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}
