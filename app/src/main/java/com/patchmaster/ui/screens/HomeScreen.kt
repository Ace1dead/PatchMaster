package com.patchmaster.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patchmaster.PatchMasterApp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    onNavigateToBrowser: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToConsole: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToTemplates: () -> Unit,
    onOpenApk: () -> Unit
) {
    val toolManager = remember { PatchMasterApp.instance.toolManager }
    val toolsReady = remember { toolManager.availableTools.values.count { it.isAvailable } }
    val appVersion = "1.0.0"

    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("PatchMaster", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("APK Modding Suite v$appVersion", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status header
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (toolsReady >= 3) Icons.Default.CheckCircle else Icons.Default.Construction,
                        contentDescription = null,
                        tint = if (toolsReady >= 3) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (toolsReady >= 3) "All systems ready" else "Setup incomplete",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Toolchain: $toolsReady/5 available",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    FilledTonalButton(onClick = { onOpenApk() }, modifier = Modifier.height(40.dp)) {
                        Icon(Icons.Default.FileOpen, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Open", fontSize = 13.sp)
                    }
                }
            }

            // Quick actions grid
            Text("Actions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            // Top row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionCard(
                    icon = Icons.Default.Android,
                    title = "Select APK",
                    desc = "Browse & pick",
                    onClick = onOpenApk,
                    modifier = Modifier.weight(1f)
                )
                ActionCard(
                    icon = Icons.Default.AutoFixHigh,
                    title = "Templates",
                    desc = "Pre-built mods",
                    onClick = onNavigateToTemplates,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionCard(
                    icon = Icons.Default.SmartToy,
                    title = "AI Agent",
                    desc = "Ares chat",
                    onClick = onNavigateToChat,
                    modifier = Modifier.weight(1f)
                )
                ActionCard(
                    icon = Icons.Default.Terminal,
                    title = "Console",
                    desc = "Build logs",
                    onClick = onNavigateToConsole,
                    modifier = Modifier.weight(1f)
                )
            }

            // Recently modded
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Quick Templates", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "Remove Ads" to Icons.Default.Block,
                            "Unlock Premium" to Icons.Default.LockOpen,
                            "Enable Debug" to Icons.Default.BugReport,
                            "Bypass SSL" to Icons.Default.Security
                        ).forEach { (label, icon) ->
                            SuggestionChip(
                                onClick = {
                                    scope.launch {
                                        PatchMasterApp.instance.aresAgent.processInput("$label this APK")
                                    }
                                },
                                label = { Text(label, fontSize = 12.sp) },
                                icon = { Icon(icon, null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                    }
                }
            }

            // Tips
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(modifier = Modifier.padding(12.dp)) {
                    Icon(Icons.Default.Lightbulb, null, modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Tip: Select an APK first, then use the AI Agent to describe what you want to modify in plain English.",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


