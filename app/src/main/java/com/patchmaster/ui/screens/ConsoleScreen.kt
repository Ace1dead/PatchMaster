package com.patchmaster.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

data class ConsoleEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel = LogLevel.INFO,
    val message: String,
    val source: String = "system"
) {
    enum class LogLevel { DEBUG, INFO, WARN, ERROR, SUCCESS }
}

class ConsoleLog {
    private val _entries = mutableListOf<ConsoleEntry>()
    val entries: List<ConsoleEntry> get() = _entries.toList()

    fun log(message: String, source: String = "system", level: ConsoleEntry.LogLevel = ConsoleEntry.LogLevel.INFO) {
        _entries.add(ConsoleEntry(level = level, message = message, source = source))
    }

    fun debug(msg: String) = log(msg, level = ConsoleEntry.LogLevel.DEBUG)
    fun info(msg: String) = log(msg, level = ConsoleEntry.LogLevel.INFO)
    fun warn(msg: String) = log(msg, level = ConsoleEntry.LogLevel.WARN)
    fun error(msg: String) = log(msg, level = ConsoleEntry.LogLevel.ERROR)
    fun success(msg: String) = log(msg, level = ConsoleEntry.LogLevel.SUCCESS)
    fun clear() = _entries.clear()

    companion object {
        val instance = ConsoleLog()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleScreen(onNavigateBack: () -> Unit) {
    val console = remember { ConsoleLog.instance }
    var entries by remember { mutableStateOf(console.entries) }
    var autoScroll by remember { mutableStateOf(true) }
    var filter by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()
    val filteredEntries = remember(entries, filter) {
        if (filter.isBlank()) entries
        else entries.filter { it.message.contains(filter, ignoreCase = true) ||
                it.source.contains(filter, ignoreCase = true) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            entries = console.entries
            kotlinx.coroutines.delay(500)
        }
    }

    LaunchedEffect(filteredEntries.size, autoScroll) {
        if (autoScroll) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Console") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { console.clear(); entries = emptyList() }) {
                        Icon(Icons.Default.ClearAll, "Clear")
                    }
                    IconButton(onClick = { autoScroll = !autoScroll }) {
                        Icon(
                            if (autoScroll) Icons.Default.VerticalAlignBottom else Icons.Default.VerticalAlignCenter,
                            "Auto-scroll: $autoScroll"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Filter row
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                placeholder = { Text("Filter logs...") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
            )

            // Stats
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val errorCount = entries.count { it.level == ConsoleEntry.LogLevel.ERROR }
                val warnCount = entries.count { it.level == ConsoleEntry.LogLevel.WARN }
                val successCount = entries.count { it.level == ConsoleEntry.LogLevel.SUCCESS }
                Text("${filteredEntries.size} entries", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (errorCount > 0) Text("$errorCount errors", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                if (warnCount > 0) Text("$warnCount warnings", fontSize = 11.sp, color = MaterialTheme.colorScheme.tertiary)
                if (successCount > 0) Text("$successCount success", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            }

            HorizontalDivider()

            // Log entries
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(4.dp)
            ) {
                if (filteredEntries.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No log entries", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                filteredEntries.forEach { entry ->
                    LogEntryRow(entry)
                }
            }
        }
    }
}

@Composable
private fun LogEntryRow(entry: ConsoleEntry) {
    val color = when (entry.level) {
        ConsoleEntry.LogLevel.ERROR -> MaterialTheme.colorScheme.error
        ConsoleEntry.LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
        ConsoleEntry.LogLevel.SUCCESS -> MaterialTheme.colorScheme.primary
        ConsoleEntry.LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
        ConsoleEntry.LogLevel.INFO -> MaterialTheme.colorScheme.onSurface
    }
    val label = when (entry.level) {
        ConsoleEntry.LogLevel.ERROR -> "E"
        ConsoleEntry.LogLevel.WARN -> "W"
        ConsoleEntry.LogLevel.SUCCESS -> "S"
        ConsoleEntry.LogLevel.DEBUG -> "D"
        ConsoleEntry.LogLevel.INFO -> "I"
    }

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp, horizontal = 2.dp)) {
        Text(
            text = label,
            color = color,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.width(16.dp)
        )
        Text(
            text = entry.source,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = entry.message,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
    }
}
