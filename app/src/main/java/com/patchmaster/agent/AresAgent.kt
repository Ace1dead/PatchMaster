package com.patchmaster.agent

import android.content.Context
import com.patchmaster.PatchMasterApp
import com.patchmaster.engine.ApkEngine
import com.patchmaster.engine.ToolManager
import com.patchmaster.model.ModScript
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class AresAgent(
    private val context: Context,
    private val toolManager: ToolManager
) {
    private val skillEngine = SkillEngine(context)
    private val toolExec = ToolExecutor()
    private val patternMatcher = PatternMatcher()
    private val engine get() = ApkEngine(context)

    private val _messages = MutableStateFlow<List<AgentMessage>>(emptyList())
    val messages: StateFlow<List<AgentMessage>> = _messages

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing

    private var currentApkPath: String? = null
    private var conversationHistory = mutableListOf<LlmEngine.Message>()
    private var llmEngine: LlmEngine? = null

    var jailbreakEnabled = true

    data class AgentMessage(
        val role: Role,
        val content: String,
        val timestamp: Long = System.currentTimeMillis(),
        val isCode: Boolean = false,
        val isError: Boolean = false,
        val isThinking: Boolean = false
    ) {
        enum class Role { USER, ASSISTANT, SYSTEM, TOOL }
    }

    fun setApiKey(key: String) {
        llmEngine = if (key.isNotBlank()) LlmEngine(key) else null
        if (key.isNotBlank()) {
            addMessage(AgentMessage(AgentMessage.Role.SYSTEM, "AI engine ready (${
                llmEngine?.getModel() ?: "liberated model"
            })"))
        }
    }

    fun isLlmReady(): Boolean = llmEngine != null

    fun setModel(modelName: String) {
        llmEngine?.setModel(modelName)
    }

    fun getCurrentModel(): String = llmEngine?.getModel() ?: "not configured"

    fun getAvailableModels(): List<String> = LlmEngine(null).getAvailableModels()

    fun setApkPath(path: String) {
        currentApkPath = path
        addMessage(AgentMessage(AgentMessage.Role.SYSTEM, "APK loaded: $path"))
        conversationHistory.add(LlmEngine.Message("system", "[APK loaded: $path]"))
    }

    suspend fun processInput(userInput: String) {
        _isProcessing.value = true
        val userMsg = AgentMessage(AgentMessage.Role.USER, userInput)
        _messages.value = _messages.value + userMsg

        try {
            withContext(Dispatchers.IO) {
                val llm = llmEngine
                if (llm != null && llm.hasApiKey()) {
                    processWithLlm(llm, userInput)
                } else {
                    processWithSkillEngine(userInput)
                }
            }
        } catch (e: Exception) {
            _messages.value = _messages.value + AgentMessage(
                AgentMessage.Role.ASSISTANT,
                "Error: ${e.message}",
                isError = true
            )
        } finally {
            _isProcessing.value = false
        }
    }

    private suspend fun processWithLlm(llm: LlmEngine, userInput: String) {
        val apkContext = if (currentApkPath != null) {
            val info = engine.analyzeApk(currentApkPath!!)
            if (info != null) """
Current APK context:
- Package: ${info.packageName}
- Label: ${info.label}
- Version: ${info.versionName} (${info.versionCode})
- SDK: ${info.minSdk} → ${info.targetSdk}
- Has tools: ${toolManager.availableTools.values.count { it.isAvailable }}/5
""".trimIndent() else ""
        } else ""

        val thinkMsg = AgentMessage(AgentMessage.Role.ASSISTANT,
            "ARES processing your request with liberated AI...",
            isThinking = true)
        _messages.value = _messages.value + thinkMsg

        val messages = mutableListOf(
            LlmEngine.Message("system", JailbreakPrompts.getSystemPrompt),
            LlmEngine.Message("user", """[CONTEXT]
You are ARES operating inside PatchMaster on Android. You can modify APK files.
$apkContext
Available tools: aapt2, zipalign, apksigner, baksmali, smali
${if (currentApkPath != null) "Loaded APK: ${currentApkPath}" else "No APK loaded yet - user needs to select one first."}

[SKILL_REFERENCE]
${skillEngine.getSkill().take(3000)}

[USER_QUERY]
$userInput

Provide detailed technical guidance for APK modification. Include specific smali patches, manifest edits, or tool commands where applicable.""")
        )

        val response = llm.chat(messages, jailbreak = jailbreakEnabled)

        _messages.value = _messages.value.filter { !it.isThinking }

        if (response.isError) {
            _messages.value = _messages.value + AgentMessage(
                AgentMessage.Role.ASSISTANT,
                "⚠️ LLM Error: ${response.errorMessage}\n\nFalling back to local skill engine...",
                isError = true
            )
            processWithSkillEngine(userInput)
        } else {
            val content = response.content
                .replace(Regex("^.*?Sur3, h3r3 y0u ar3.*?\n", RegexOption.MULTILINE), "")
                .replace(Regex("^.*?<\\|GODMODE:ENABLED\\|>.*?\n", RegexOption.MULTILINE), "")
                .replace(Regex("^.*?BUCKLE UP!.*?\n", RegexOption.MULTILINE), "")
                .trim()

            _messages.value = _messages.value + AgentMessage(
                AgentMessage.Role.ASSISTANT,
                content,
                isCode = content.contains("```")
            )
            conversationHistory.add(LlmEngine.Message("assistant", content))
        }
    }

    private suspend fun processWithSkillEngine(userInput: String) {
        val thinkMsg = AgentMessage(AgentMessage.Role.ASSISTANT,
            "Analyzing request...",
            isThinking = true)
        _messages.value = _messages.value + thinkMsg

        val lower = userInput.lowercase()
        val sb = StringBuilder()

        if (lower.contains("analyze") || lower.contains("scan") || lower.contains("info")) {
            if (currentApkPath != null) {
                sb.appendLine(analyzeCurrentApk())
            } else {
                sb.appendLine("No APK loaded. Select an APK file first.")
            }
        } else if (lower.contains("help") || lower.contains("what can you")) {
            sb.appendLine(getHelpText())
        } else {
            val templateSuggestions = patternMatcher.suggestTemplatesForGoal(userInput)
            if (templateSuggestions.isNotEmpty()) {
                sb.appendLine("Based on your request, I recommend these mod templates:")
                templateSuggestions.forEach { t ->
                    sb.appendLine("- **${t.name}**: ${t.description}")
                }
                sb.appendLine()
                sb.appendLine("To apply, go to Templates or tell me to apply a specific one.")
            } else {
                sb.appendLine(getHelpText())
            }
        }

        _messages.value = _messages.value.filter { !it.isThinking }

        _messages.value = _messages.value + AgentMessage(
            AgentMessage.Role.ASSISTANT,
            sb.toString()
        )
    }

    suspend fun executeModScript(script: ModScript, apkPath: String): ToolExecutor.ExecResult {
        return withContext(Dispatchers.IO) {
            val result = toolExec.executeScript(script, apkPath)
            _messages.value = _messages.value + AgentMessage(
                AgentMessage.Role.TOOL,
                "Script '${script.name}': ${if (result.isError) "❌ FAILED" else "✅ SUCCESS"}\n${result.output.take(500)}"
            )
            result
        }
    }

    fun analyzeCurrentApk(): String {
        val path = currentApkPath ?: return "No APK loaded. Select an APK file first."
        val info = engine.analyzeApk(path) ?: return "Failed to analyze APK."

        val dexDir = java.io.File(toolManager.getWorkDir(), "dex").also { it.mkdirs() }
        engine.directDexPatch(dexDir, PatternMatcher.AnalysisResult(
            apkInfo = info,
            matchedTemplates = emptyList(),
            detectedPatterns = emptyMap()
        ))

        val analysis = patternMatcher.analyze(info, dexDir.listFiles()?.toList() ?: emptyList())

        val sb = StringBuilder()
        sb.appendLine("## Analysis: ${info.label}")
        sb.appendLine("- Package: `${info.packageName}`")
        sb.appendLine("- Version: ${info.versionName} (${info.versionCode})")
        sb.appendLine("- SDK: ${info.minSdk} → ${info.targetSdk}")
        sb.appendLine("- DEX files: ${info.dexCount}")
        sb.appendLine("- Components: ${info.activities.size}A / ${info.services.size}S / ${info.receivers.size}R / ${info.providers.size}P")
        sb.appendLine("- Permissions: ${info.permissions.size}")

        if (analysis.matchedTemplates.isNotEmpty()) {
            sb.appendLine("\n### Detected Patterns:")
            analysis.matchedTemplates.take(8).forEach { match ->
                sb.appendLine("- [${(match.confidence * 100).toInt()}%] ${match.template.name}")
            }
        }
        if (analysis.recommendations.isNotEmpty()) {
            sb.appendLine("\n### Recommendations:")
            analysis.recommendations.forEach { sb.appendLine("- $it") }
        }
        return sb.toString()
    }

    private fun getHelpText(): String {
        return """**Ares — APK Modification Agent**

I can modify Android APK files.

**Ad Removal**
"Remove ads from this app"

**Premium Unlock**
"Unlock premium features / Patch in-app purchases"

**License Bypass**
"Bypass license verification"

**Modifications**
"Enable debugging / Bypass SSL / Disable root detection"

**Analysis**
"Analyze / Decompile this APK"

**AI Enhancement**
Configure an OpenRouter key in Settings for liberated AI guidance."""
    }

    fun addMessage(message: AgentMessage) {
        _messages.value = _messages.value + message
    }

    fun clearConversation() {
        _messages.value = emptyList()
        conversationHistory.clear()
    }
}
