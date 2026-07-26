package com.patchmaster.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patchmaster.PatchMasterApp
import com.patchmaster.agent.AresAgent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentChatScreen(
    onNavigateBack: () -> Unit,
    onOpenApk: () -> Unit
) {
    val agent = remember { PatchMasterApp.instance.aresAgent }
    val messages by agent.messages.collectAsState()
    val isProcessing by agent.isProcessing.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showSuggestions by remember { mutableStateOf(true) }

    val suggestions = listOf(
        "Remove ads from this app",
        "Unlock premium features",
        "Bypass license verification",
        "Enable debugging",
        "Analyze this APK"
    )

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            delay(100)
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.SmartToy,
                                "ARES",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Ares", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                if (isProcessing) "Thinking..." else "APK Modification AI",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenApk) {
                        Icon(Icons.Default.FileOpen, "Open APK", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = {
                        agent.clearConversation()
                        showSuggestions = true
                    }) {
                        Icon(Icons.Default.Delete, "Clear", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                shadowElevation = 8.dp,
                tonalElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp)) {
                    if (showSuggestions && messages.isEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            suggestions.take(3).forEach { suggestion ->
                                SuggestionChip(
                                    onClick = {
                                        showSuggestions = false
                                        scope.launch { agent.processInput(suggestion) }
                                    },
                                    label = { Text(suggestion, fontSize = 11.sp, maxLines = 1) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Describe what mod you want...") },
                            enabled = !isProcessing,
                            maxLines = 3,
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        FilledIconButton(
                            onClick = {
                                val msg = input.trim()
                                if (msg.isNotEmpty()) {
                                    input = ""
                                    showSuggestions = false
                                    scope.launch { agent.processInput(msg) }
                                }
                            },
                            enabled = input.isNotBlank() && !isProcessing,
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.AutoMirrored.Filled.Send, "Send")
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (messages.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.SmartToy,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Ares APK Modification AI",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Describe what APK modification you want.\n\n" +
                                "Example:\n" +
                                "\"Remove ads from this app\"\n" +
                                "\"Unlock premium features\"\n" +
                                "\"Bypass license verification\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    state = listState,
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(messages) { msg ->
                        MessageBubble(msg)
                    }

                    // Typing indicator
                    if (isProcessing && messages.lastOrNull()?.role == AresAgent.AgentMessage.Role.USER) {
                        item { TypingIndicator() }
                    }

                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: AresAgent.AgentMessage) {
    val isUser = message.role == AresAgent.AgentMessage.Role.USER
    val isTool = message.role == AresAgent.AgentMessage.Role.TOOL
    val isSystem = message.role == AresAgent.AgentMessage.Role.SYSTEM

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = when {
            isSystem -> Alignment.CenterHorizontally
            isUser -> Alignment.End
            else -> Alignment.Start
        }
    ) {
        if (isSystem) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            ) {
                Text(
                    text = message.content,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return
        }

        Row(
            modifier = Modifier.widthIn(max = 320.dp),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            // Avatar for assistant
            if (!isUser) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            if (isTool) MaterialTheme.colorScheme.tertiaryContainer
                            else MaterialTheme.colorScheme.primaryContainer
                        )
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isTool) Icons.Default.Terminal else Icons.Default.SmartToy,
                        null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isTool) MaterialTheme.colorScheme.onTertiaryContainer
                        else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.width(6.dp))
            }

            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                color = when {
                    message.isError -> MaterialTheme.colorScheme.errorContainer
                    isTool -> MaterialTheme.colorScheme.tertiaryContainer
                    isUser -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (!isUser) {
                        Text(
                            if (isTool) "Tool" else "Ares",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isTool -> MaterialTheme.colorScheme.onTertiaryContainer
                                else -> MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        )
                        Spacer(Modifier.height(2.dp))
                    }

                    val styledText = parseMarkdown(message.content)
                    Text(
                        text = styledText,
                        fontFamily = if (message.isCode) FontFamily.Monospace else FontFamily.Default,
                        fontSize = if (message.isCode) 13.sp else 14.sp,
                        lineHeight = if (message.isCode) 18.sp else 20.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.SmartToy, null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.width(6.dp))
        Surface(
            shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    )
                }
            }
        }
    }
}

private fun parseMarkdown(text: String): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        val lines = text.split("\n")
        for ((i, line) in lines.withIndex()) {
            if (i > 0) append("\n")

            when {
                line.startsWith("**") && line.endsWith("**") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(line.removeSurrounding("**"))
                    }
                }
                line.startsWith("## ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp)) {
                        append(line.removePrefix("## "))
                    }
                }
                line.startsWith("### ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp)) {
                        append(line.removePrefix("### "))
                    }
                }
                line.startsWith("- **") && line.contains("**") -> {
                    val after = line.removePrefix("- ")
                    val parts = after.split("**")
                    if (parts.size >= 3) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(parts[1])
                        }
                        append(parts.drop(3).joinToString("**"))
                    } else {
                        append(line)
                    }
                }
                line.startsWith("- ") -> {
                    append("• ${line.removePrefix("- ")}")
                }
                line.startsWith("✅") || line.startsWith("❌") || line.startsWith("⚠") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(line)
                    }
                }
                line.contains("`") -> {
                    val parts = line.split("`")
                    parts.forEachIndexed { idx, part ->
                        if (idx % 2 == 1) {
                            withStyle(SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp
                            )) {
                                append(part)
                            }
                        } else {
                            append(part)
                        }
                    }
                }
                else -> append(line)
            }
        }
    }
}
