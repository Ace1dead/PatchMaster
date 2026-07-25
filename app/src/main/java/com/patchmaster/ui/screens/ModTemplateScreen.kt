package com.patchmaster.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patchmaster.model.ModTemplate
import com.patchmaster.model.ModTemplateLibrary
import com.patchmaster.PatchMasterApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModTemplateScreen(
    apkPath: String?,
    onNavigateBack: () -> Unit,
    onApplyTemplate: (ModTemplate) -> Unit
) {
    val templates = remember { ModTemplateLibrary.templates }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<ModTemplate.Category?>(null) }

    val filtered = remember(templates, searchQuery, selectedCategory) {
        templates.filter { t ->
            (searchQuery.isBlank() || t.name.contains(searchQuery, ignoreCase = true) ||
                    t.description.contains(searchQuery, ignoreCase = true)) &&
            (selectedCategory == null || t.category == selectedCategory)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mod Templates") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Search
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search templates...") },
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true
            )

            // Category filter chips
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val allCategories = ModTemplate.Category.entries
                allCategories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = {
                            selectedCategory = if (selectedCategory == cat) null else cat
                        },
                        label = { Text(cat.name.take(1) + cat.name.lowercase().dropLast(1), fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(
                                when (cat) {
                                    ModTemplate.Category.ADS -> Icons.Default.Block
                                    ModTemplate.Category.PREMIUM -> Icons.Default.LockOpen
                                    ModTemplate.Category.LICENSE -> Icons.Default.Verified
                                    ModTemplate.Category.DEBUG -> Icons.Default.BugReport
                                    ModTemplate.Category.SECURITY -> Icons.Default.Security
                                    ModTemplate.Category.PERMISSIONS -> Icons.Default.Shield
                                    ModTemplate.Category.TWEAKS -> Icons.Default.Tune
                                    ModTemplate.Category.CUSTOM -> Icons.Default.Code
                                },
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }
            }

            Divider()

            // Template list
            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No templates match", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered) { template ->
                        TemplateCard(
                            template = template,
                            apkLoaded = apkPath != null,
                            onApply = { onApplyTemplate(template) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateCard(
    template: ModTemplate,
    apkLoaded: Boolean,
    onApply: () -> Unit
) {
    val riskColor = when (template.riskLevel) {
        ModTemplate.RiskLevel.LOW -> Color(0xFF4CAF50)
        ModTemplate.RiskLevel.MEDIUM -> Color(0xFFFFC107)
        ModTemplate.RiskLevel.HIGH -> Color(0xFFFF9800)
        ModTemplate.RiskLevel.BRICK_RISK -> Color(0xFFF44336)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { if (apkLoaded) onApply() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Category icon
                Icon(
                    when (template.category) {
                        ModTemplate.Category.ADS -> Icons.Default.Block
                        ModTemplate.Category.PREMIUM -> Icons.Default.LockOpen
                        ModTemplate.Category.LICENSE -> Icons.Default.Verified
                        ModTemplate.Category.DEBUG -> Icons.Default.BugReport
                        ModTemplate.Category.SECURITY -> Icons.Default.Security
                        ModTemplate.Category.PERMISSIONS -> Icons.Default.Shield
                        ModTemplate.Category.TWEAKS -> Icons.Default.Tune
                        ModTemplate.Category.CUSTOM -> Icons.Default.Code
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(template.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(template.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Tags row
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Complexity
                val complexityLabel = when (template.complexity) {
                    ModTemplate.Complexity.EASY -> "Easy"
                    ModTemplate.Complexity.MEDIUM -> "Medium"
                    ModTemplate.Complexity.HARD -> "Hard"
                    ModTemplate.Complexity.EXPERT -> "Expert"
                }
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ) {
                    Text(
                        complexityLabel,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Risk
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = riskColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        template.riskLevel.name,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = riskColor
                    )
                }

                // Decompile required
                if (template.requiresFullDecompile) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    ) {
                        Text(
                            "Decompile",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                // Apply button
                FilledTonalButton(
                    onClick = onApply,
                    enabled = apkLoaded,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Apply", fontSize = 12.sp)
                }
            }
        }
    }
}
