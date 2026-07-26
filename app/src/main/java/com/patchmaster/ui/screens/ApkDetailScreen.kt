package com.patchmaster.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patchmaster.PatchMasterApp
import com.patchmaster.model.ApkInfo
import com.patchmaster.model.ModAction
import com.patchmaster.model.ModScript
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkDetailScreen(
    apkPath: String,
    onModify: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onSaveApk: () -> Unit
) {
    val engine = remember { com.patchmaster.engine.ApkEngine(PatchMasterApp.instance) }
    var apkInfo by remember { mutableStateOf<ApkInfo?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf("") }
    var showQuickMods by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val console = remember { ConsoleLog.instance }

    LaunchedEffect(apkPath) {
        isLoading = true
        try {
            val info = engine.analyzeApk(apkPath)
            apkInfo = info
            if (info != null) {
                console.log("Analyzed: ${info.label} (${info.packageName})", "analyzer")
            } else {
                errorMsg = "Failed to analyze APK"
                console.error("Failed to analyze APK: $apkPath")
            }
        } catch (e: Exception) {
            errorMsg = e.message ?: "Unknown error"
            console.error("Analysis error: ${e.message}")
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        apkInfo?.label?.take(20) ?: "APK Detail",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onModify(apkPath) }) {
                        Icon(Icons.Default.Edit, "Modify")
                    }
                }
            )
        },
        bottomBar = {
            if (apkInfo != null) {
                Surface(shadowElevation = 8.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = { showQuickMods = true },
                            modifier = Modifier.weight(1f).padding(end = 4.dp)
                        ) {
                            Icon(Icons.Default.AutoFixHigh, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Quick Mods", fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = { onModify(apkPath) },
                            modifier = Modifier.weight(1f).padding(start = 4.dp)
                        ) {
                            Icon(Icons.Default.SmartToy, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("AI Agent", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    ) { padding ->
        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Analyzing APK...")
                    }
                }
            }
            errorMsg.isNotEmpty() -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Error, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(16.dp))
                        Text(errorMsg, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            apkInfo != null -> {
                ApkInfoContent(apkInfo!!, modifier = Modifier.padding(padding))
            }
        }
    }

    if (showQuickMods && apkInfo != null) {
        QuickModsDialog(
            apkInfo = apkInfo!!,
            apkPath = apkPath,
            onDismiss = { showQuickMods = false },
            onModApplied = { script ->
                showQuickMods = false
                console.success("Applied mod: ${script.name}")
            }
        )
    }
}

@Composable
private fun ApkInfoContent(info: ApkInfo, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Main info card
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(info.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(info.packageName, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Divider()
                    Spacer(Modifier.height(8.dp))
                    InfoRow("Version", "${info.versionName} (${info.versionCode})")
                    InfoRow("Size", "${info.fileSize / 1024} KB")
                    InfoRow("SDK", "${info.minSdk} → ${info.targetSdk}")
                    InfoRow("DEX files", info.dexCount.toString())
                    InfoRow("Native libs", if (info.nativeLibs.isEmpty()) "None" else info.nativeLibs.take(3).joinToString(", "))
                    InfoRow("Debuggable", if (info.isDebuggable) "Yes" else "No")
                }
            }
        }

        // Components card
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Components", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    InfoRow("Activities", info.activities.size.toString())
                    InfoRow("Services", info.services.size.toString())
                    InfoRow("Receivers", info.receivers.size.toString())
                    InfoRow("Providers", info.providers.size.toString())
                    InfoRow("Permissions", info.permissions.size.toString())
                }
            }
        }

        // Permissions
        if (info.permissions.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Permissions", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        info.permissions.forEach { perm ->
                            Text(
                                perm.substringAfterLast("."),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(60.dp)) }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, modifier = Modifier.width(100.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun QuickModsDialog(
    apkInfo: ApkInfo,
    apkPath: String,
    onDismiss: () -> Unit,
    onModApplied: (ModScript) -> Unit
) {
    val scope = rememberCoroutineScope()
    val console = remember { ConsoleLog.instance }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quick Mods for ${apkInfo.label}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                QuickModButton("Remove All Ads") {
                    val script = ModScript(
                        name = "Remove Ads",
                        description = "Removes ad libraries and patches",
                        targetPackage = apkInfo.packageName,
                        actions = listOf(
                            ModAction.ManifestEdit("android:debuggable", "true", com.patchmaster.model.ActionType.ADD),
                            ModAction.ComponentDisable("com.google.android.gms.ads.AdActivity"),
                            ModAction.ComponentDisable("com.facebook.ads.InterstitialAdActivity"),
                            ModAction.ComponentDisable("com.unity3d.ads.adunit.AdUnitActivity")
                        )
                    )
                    scope.launch {
                        PatchMasterApp.instance.aresAgent.executeModScript(script, apkPath)
                        onModApplied(script)
                        console.success("Ad removal mod applied")
                    }
                }
                QuickModButton("Unlock Premium") {
                    val script = ModScript(
                        name = "Unlock Premium",
                        description = "Forces premium/pro features enabled",
                        targetPackage = apkInfo.packageName,
                        actions = listOf(
                            ModAction.ManifestEdit("android:debuggable", "true", com.patchmaster.model.ActionType.ADD)
                        )
                    )
                    scope.launch {
                        PatchMasterApp.instance.aresAgent.executeModScript(script, apkPath)
                        onModApplied(script)
                        console.success("Premium unlock mod applied (may need method-level patches)")
                    }
                }
                QuickModButton("Enable Debugging") {
                    val script = ModScript(
                        name = "Enable Debug",
                        description = "Enables debug mode and backup",
                        targetPackage = apkInfo.packageName,
                        actions = listOf(
                            ModAction.ManifestEdit("android:debuggable", "true", com.patchmaster.model.ActionType.ADD),
                            ModAction.ManifestEdit("android:allowBackup", "true", com.patchmaster.model.ActionType.ADD),
                            ModAction.ManifestEdit("android:extractNativeLibs", "true", com.patchmaster.model.ActionType.ADD)
                        )
                    )
                    scope.launch {
                        PatchMasterApp.instance.aresAgent.executeModScript(script, apkPath)
                        onModApplied(script)
                        console.success("Debug mode enabled")
                    }
                }
                QuickModButton("Remove All Permissions") {
                    val script = ModScript(
                        name = "Remove Permissions",
                        description = "Removes all permissions from APK",
                        targetPackage = apkInfo.packageName,
                        actions = apkInfo.permissions.map { ModAction.PermissionRemove(it) }
                    )
                    scope.launch {
                        PatchMasterApp.instance.aresAgent.executeModScript(script, apkPath)
                        onModApplied(script)
                        console.success("All permissions removed")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun QuickModButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text, fontSize = 13.sp)
    }
}
