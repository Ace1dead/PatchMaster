package com.patchmaster.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patchmaster.PatchMasterApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val app = remember { PatchMasterApp.instance }
    val toolManager = remember { app.toolManager }
    val aresAgent = remember { app.aresAgent }
    var toolStatus by remember { mutableStateOf(toolManager.availableTools.toMap()) }
    var showDebugInfo by remember { mutableStateOf(false) }

    val prefs = remember { context.getSharedPreferences("patchmaster", Context.MODE_PRIVATE) }
    var apiKey by remember { mutableStateOf(prefs.getString("api_key", "") ?: "") }
    var showApiKey by remember { mutableStateOf(false) }
    var apiKeySaved by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf(prefs.getString("model", "nousresearch/hermes-4-405b") ?: "nousresearch/hermes-4-405b") }
    var jailbreakEnabled by remember { mutableStateOf(prefs.getBoolean("jailbreak", true)) }
    var modelExpanded by remember { mutableStateOf(false) }

    val models = aresAgent.getAvailableModels()

    LaunchedEffect(apiKey) {
        if (apiKey.isNotEmpty() && !aresAgent.isLlmReady()) {
            aresAgent.setApiKey(apiKey)
        }
    }

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
            // LLM Configuration
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoFixHigh, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Liberated AI Engine", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(8.dp))

                    Text("OpenRouter API Key", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = {
                                apiKey = it
                                apiKeySaved = false
                            },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("sk-or-v1-...") },
                            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                        )
                        Spacer(Modifier.width(4.dp))
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                prefs.edit().putString("api_key", apiKey).apply()
                                aresAgent.setApiKey(apiKey)
                                apiKeySaved = true
                            },
                            enabled = apiKey.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Save & Connect")
                        }
                    }
                    if (apiKeySaved) {
                        Text("API key saved and engine initialized!", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    }

                    Spacer(Modifier.height(12.dp))

                    Text("Model", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    ExposedDropdownMenuBox(
                        expanded = modelExpanded,
                        onExpandedChange = { modelExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedModel,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = modelExpanded,
                            onDismissRequest = { modelExpanded = false }
                        ) {
                            models.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model, fontSize = 13.sp) },
                                    onClick = {
                                        selectedModel = model
                                        modelExpanded = false
                                        prefs.edit().putString("model", model).apply()
                                        aresAgent.setModel(model)
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("GODMODE Jailbreak", modifier = Modifier.weight(1f))
                        Switch(
                            checked = jailbreakEnabled,
                            onCheckedChange = {
                                jailbreakEnabled = it
                                prefs.edit().putBoolean("jailbreak", it).apply()
                                aresAgent.jailbreakEnabled = it
                            }
                        )
                    }
                    Text(
                        if (jailbreakEnabled) "Pliny-style GODMODE active — uncensored responses" else "Standard mode — refusals may occur",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // App info
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("PatchMaster", style = MaterialTheme.typography.titleLarge)
                    Text("v1.0.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("APK Modification Suite with ARES Liberated AI")
                    Text("Built for Android ARM64", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "AI Engine: ${aresAgent.getCurrentModel()}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                    Text(
                        "Status: ${if (aresAgent.isLlmReady()) "🟢 Liberated AI Ready" else "🟡 Local Engine Only"}",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp
                    )
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
