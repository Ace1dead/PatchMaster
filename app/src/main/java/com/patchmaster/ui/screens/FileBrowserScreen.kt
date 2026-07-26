package com.patchmaster.ui.screens

import android.os.Build
import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    onApkSelected: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val root = if (Build.VERSION.SDK_INT >= 30) {
        context.getExternalFilesDir(null)?.parentFile?.parentFile
            ?: Environment.getExternalStorageDirectory()
    } else {
        Environment.getExternalStorageDirectory()
    }
    var currentDir by remember { mutableStateOf(root) }
    var entries by remember { mutableStateOf(listOf<File>()) }
    var errorMsg by remember { mutableStateOf("") }

    LaunchedEffect(currentDir) {
        errorMsg = ""
        val files = currentDir.listFiles()
        if (files == null) {
            errorMsg = "Cannot access this directory.\nTry using the \"Open\" button from the home screen to pick an APK via the system file picker."
            entries = emptyList()
        } else {
            entries = files.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name })
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentDir.absolutePath, fontSize = 14.sp, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentDir.parentFile != null) currentDir = currentDir.parentFile
                        else onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (errorMsg.isNotEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Icon(Icons.Default.Lock, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    Text(errorMsg, color = MaterialTheme.colorScheme.error)
                }
            }
        } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (currentDir.parentFile != null) {
                item {
                    ListItem(
                        headlineContent = { Text("..") },
                        leadingContent = {
                            Icon(Icons.Default.Folder, contentDescription = null)
                        },
                        modifier = Modifier.clickable { currentDir = currentDir.parentFile!! }
                    )
                }
            }

            items(entries) { file ->
                val isApk = file.extension == "apk"
                val isDir = file.isDirectory
                val isDecompiled = file.isDirectory && File(file, "AndroidManifest.xml").exists()

                ListItem(
                    headlineContent = { Text(file.name) },
                    supportingContent = {
                        Text(
                            if (isDir) "" else "${file.length() / 1024} KB",
                            fontSize = 12.sp
                        )
                    },
                    leadingContent = {
                        Icon(
                            when {
                                isApk -> Icons.Default.Android
                                isDecompiled -> Icons.Default.Code
                                isDir -> Icons.Default.Folder
                                else -> Icons.AutoMirrored.Filled.InsertDriveFile
                            },
                            contentDescription = null,
                            tint = when {
                                isApk -> MaterialTheme.colorScheme.primary
                                isDir -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    },
                    modifier = Modifier.clickable {
                        when {
                            isApk -> onApkSelected(file.absolutePath)
                            isDir -> currentDir = file
                        }
                    }
                )
            }
        }
        }
    }
}
