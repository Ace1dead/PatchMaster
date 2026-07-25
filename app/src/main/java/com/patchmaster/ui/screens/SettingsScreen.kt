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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patchmaster.PatchMasterApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onNavigateBack: () -> Unit) {
    val app = remember { PatchMasterApp.instance }
    val toolManager = remember { app.toolManager }
    var toolStatus by remember { mutableStateOf(toolManager.availableTools.toMap()) }
    var showDebugInfo by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, "Back")
                }}
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App info
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("PatchMaster", style = MaterialTheme.typography.titleLarge)
                    Text("v1.0.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("APK Modification Suite with Ares AI Agent")
                    Text("Built for Android ARM64", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Toolchain status
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Toolchain", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    toolStatus.forEach { (name, info) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (info.isAvailable) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                contentDescription = null,
                                tint = if (info.isAvailable) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (info.isAvailable) "Ready" else "Missing",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            toolManager.ensureToolsInstalled()
                            toolStatus = toolManager.availableTools.toMap()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Reinstall Tools")
                    }
                }
            }

            // Storage
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Storage", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    InfoRow("Data directory", app.filesDir.absolutePath)
                    InfoRow("Cache directory", app.cacheDir.absolutePath)
                    InfoRow("Work directory", toolManager.getWorkDir().parentFile?.absolutePath ?: "")
                    InfoRow("Keystore", toolManager.createKeystoreDir().absolutePath)
                }
            }

            // Debug
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Debug Info", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Switch(checked = showDebugInfo, onCheckedChange = { showDebugInfo = it })
                    }
                    if (showDebugInfo) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = """
                                |Device: ${android.os.Build.MODEL}
                                |Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})
                                |ABI: ${android.os.Build.SUPPORTED_ABIS.joinToString()}
                                |Java: ${System.getProperty("java.vm.version") ?: "N/A"}
                                |Heap: ${Runtime.getRuntime().totalMemory() / 1024 / 1024}MB / ${Runtime.getRuntime().maxMemory() / 1024 / 1024}MB
                                |Free: ${Runtime.getRuntime().freeMemory() / 1024 / 1024}MB
                            """.trimMargin(),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Danger zone
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Cleanup", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Clear all working directories and temporary files.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            com.patchmaster.util.FileUtils.deleteRecursive(toolManager.getWorkDir().parentFile!!)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Clear All Working Data")
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, modifier = Modifier.width(120.dp), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
    }
}
