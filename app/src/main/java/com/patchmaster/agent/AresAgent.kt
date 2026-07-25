package com.patchmaster.agent

import android.content.Context
import com.patchmaster.PatchMasterApp
import com.patchmaster.engine.ApkEngine
import com.patchmaster.engine.ToolManager
import com.patchmaster.model.ModAction
import com.patchmaster.model.ModScript
import com.patchmaster.model.ModTemplate
import com.patchmaster.model.ModTemplateLibrary
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
    private var lastAnalysisResult: PatternMatcher.AnalysisResult? = null

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

    fun setApkPath(path: String) {
        currentApkPath = path
        addMessage(AgentMessage(AgentMessage.Role.SYSTEM, "APK loaded: $path"))
    }

    suspend fun processInput(userInput: String) {
        _isProcessing.value = true
        val userMsg = AgentMessage(AgentMessage.Role.USER, userInput)
        _messages.value = _messages.value + userMsg

        try {
            withContext(Dispatchers.IO) {
                val plan = generateActionPlan(userInput)

                // Show thinking
                val thinkMsg = AgentMessage(AgentMessage.Role.ASSISTANT,
                    "Analyzing request...\nDetected intent: **${plan.intent}**\nConfidence: ${(plan.confidence * 100).toInt()}%\n${plan.reasoning}",
                    isThinking = true)
                _messages.value = _messages.value + thinkMsg

                // Execute plan
                val results = executePlan(plan)

                // Build response
                val response = buildResponse(plan, results)
                _messages.value = _messages.value + AgentMessage(AgentMessage.Role.ASSISTANT, response)
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
        lastAnalysisResult = analysis

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

    private data class ActionPlan(
        val intent: String,
        val confidence: Float,
        val reasoning: String,
        val steps: List<PlanStep>,
        val suggestedTemplates: List<ModTemplate> = emptyList(),
        val requiresApk: Boolean = false
    )

    private data class PlanStep(
        val action: String,
        val params: Map<String, String> = emptyMap(),
        val description: String = ""
    )

    private data class IntentMatch(val name: String, val score: Float, val templates: List<ModTemplate>)

    private fun generateActionPlan(input: String): ActionPlan {
        val lower = input.lowercase()

        val intents = listOf(
            detectAdRemoval(lower),
            detectPremiumUnlock(lower),
            detectLicenseBypass(lower),
            detectIAPPatch(lower),
            detectDebugEnable(lower),
            detectSSLBypass(lower),
            detectRootBypass(lower),
            detectPermissionChange(lower),
            detectAnalysis(lower),
            detectDecompile(lower),
            detectInstall(lower),
            detectHelp(lower),
            detectMod(lower)
        ).filterNotNull()

        val bestIntent = intents.maxByOrNull { it.score }
            ?: return ActionPlan("general_assistance", 0.5f,
                "I can help with APK modification. Available options: remove ads, unlock premium, bypass licenses, enable debugging, modify permissions, or analyze an APK.",
                listOf(PlanStep("get_skill", mapOf(), "Loading modding knowledge")),
                requiresApk = false)

        val steps = buildStepsForIntent(bestIntent.name, bestIntent.templates)
        return ActionPlan(
            intent = bestIntent.name,
            confidence = bestIntent.score,
            reasoning = "Detected intent '${bestIntent.name}' with score ${(bestIntent.score * 100).toInt()}%",
            steps = steps,
            suggestedTemplates = bestIntent.templates,
            requiresApk = steps.any { it.action == "analyze" || it.action == "apply_mod" }
        )
    }

    private fun scoreFor(lower: String, keywords: List<String>, weight: Float): Float {
        val count = keywords.count { lower.contains(it) }
        return count * weight
    }

    private fun detectAdRemoval(lower: String): IntentMatch? {
        val keywords = listOf("remove ad", "block ad", "ad free", "no ads", "adblock", "ad removal",
            "disable ad", "stop ad", "advert", "remove advertisement")
        val score = scoreFor(lower, keywords, 0.2f)
        if (score == 0f) return null
        return IntentMatch("remove_ads", minOf(score, 1.0f),
            listOfNotNull(
                ModTemplateLibrary.findById("ads_remove_all"),
                ModTemplateLibrary.findById("ads_remove_google"),
                ModTemplateLibrary.findById("ads_bypass_rewarded")
            ))
    }

    private fun detectPremiumUnlock(lower: String): IntentMatch? {
        val keywords = listOf("premium", "unlock", "pro", "vip", "paid feature", "crack",
            "full version", "unlimited", "premuim", "ispremium", "ispro")
        val score = scoreFor(lower, keywords, 0.15f)
        if (score == 0f) return null
        return IntentMatch("unlock_premium", minOf(score, 1.0f),
            listOfNotNull(
                ModTemplateLibrary.findById("premium_force_true"),
                ModTemplateLibrary.findById("premium_iap_patch"),
                ModTemplateLibrary.findById("premium_lvl_bypass"),
                ModTemplateLibrary.findById("premium_subscription_bypass"),
                ModTemplateLibrary.findById("license_remove_trial")
            ))
    }

    private fun detectLicenseBypass(lower: String): IntentMatch? {
        val keywords = listOf("license", "lvl", "bypass license", "remove license", "crack license",
            "verification", "signature check", "validate", "allow")
        val score = scoreFor(lower, keywords, 0.15f)
        if (score == 0f) return null
        return IntentMatch("bypass_license", minOf(score, 1.0f),
            listOfNotNull(
                ModTemplateLibrary.findById("license_crack"),
                ModTemplateLibrary.findById("premium_lvl_bypass"),
                ModTemplateLibrary.findById("license_signature_bypass"),
                ModTemplateLibrary.findById("license_remove_trial")
            ))
    }

    private fun detectIAPPatch(lower: String): IntentMatch? {
        val keywords = listOf("iap", "in-app", "purchase", "billing", "buy", "shopping", "store",
            "google play", "payment", "subscribe")
        val score = scoreFor(lower, keywords, 0.12f)
        if (score == 0f) return null
        return IntentMatch("patch_iap", minOf(score, 1.0f),
            listOfNotNull(
                ModTemplateLibrary.findById("premium_iap_patch"),
                ModTemplateLibrary.findById("premium_subscription_bypass")
            ))
    }

    private fun detectDebugEnable(lower: String): IntentMatch? {
        val keywords = listOf("debug", "debuggable", "enable debug", "make debuggable")
        val score = scoreFor(lower, keywords, 0.2f)
        if (score == 0f) return null
        return IntentMatch("enable_debug", minOf(score, 1.0f),
            listOfNotNull(
                ModTemplateLibrary.findById("debug_enable"),
                ModTemplateLibrary.findById("debug_log_enable")
            ))
    }

    private fun detectSSLBypass(lower: String): IntentMatch? {
        val keywords = listOf("ssl", "pinning", "certificate", "https", "security", "network config",
            "cleartext", "bypass ssl")
        val score = scoreFor(lower, keywords, 0.15f)
        if (score == 0f) return null
        return IntentMatch("bypass_ssl", minOf(score, 1.0f),
            listOfNotNull(ModTemplateLibrary.findById("debug_ssl_bypass")))
    }

    private fun detectRootBypass(lower: String): IntentMatch? {
        val keywords = listOf("root", "detection", "root check", "jailbreak", "su", "superuser")
        val score = scoreFor(lower, keywords, 0.15f)
        if (score == 0f) return null
        return IntentMatch("disable_root_check", minOf(score, 1.0f),
            listOfNotNull(
                ModTemplateLibrary.findById("sec_disable_root_check"),
                ModTemplateLibrary.findById("sec_disable_emulator_check"),
                ModTemplateLibrary.findById("sec_disable_anti_hooking")
            ))
    }

    private fun detectPermissionChange(lower: String): IntentMatch? {
        if ("permission" !in lower) return null
        val remove = "remove" in lower || "delete" in lower
        val add = "add" in lower || "grant" in lower
        val internet = "internet" in lower
        return IntentMatch("modify_permissions", 0.8f,
            listOfNotNull(
                if (internet) ModTemplateLibrary.findById("perm_remove_internet") else null,
                if (remove) ModTemplateLibrary.findById("perm_remove_all") else null
            ))
    }

    private fun detectAnalysis(lower: String): IntentMatch? {
        val keywords = listOf("analyze", "analyse", "scan", "inspect", "info", "what is this",
            "details", "show", "examine")
        val score = scoreFor(lower, keywords, 0.2f)
        if (score == 0f) return null
        return IntentMatch("analyze_apk", minOf(score, 1.0f), emptyList())
    }

    private fun detectDecompile(lower: String): IntentMatch? {
        val keywords = listOf("decompile", "extract", "unpack", "open", "view code", "view smali")
        val score = scoreFor(lower, keywords, 0.2f)
        if (score == 0f) return null
        return IntentMatch("decompile_apk", minOf(score, 1.0f), emptyList())
    }

    private fun detectInstall(lower: String): IntentMatch? {
        val keywords = listOf("install", "push", "deploy", "side load", "sideload")
        val score = scoreFor(lower, keywords, 0.25f)
        if (score == 0f) return null
        return IntentMatch("install_apk", minOf(score, 1.0f), emptyList())
    }

    private fun detectHelp(lower: String): IntentMatch? {
        val keywords = listOf("help", "what can you", "how to", "guide", "tutorial", "capabilities",
            "features", "what do", "what are", "commands")
        val score = scoreFor(lower, keywords, 0.2f)
        if (score == 0f) return null
        return IntentMatch("show_help", minOf(score, 1.0f), emptyList())
    }

    private fun detectMod(lower: String): IntentMatch? {
        val keywords = listOf("mod", "patch", "hack", "tweak", "change", "edit", "customize",
            "modify", "alter")
        val score = scoreFor(lower, keywords, 0.1f)
        if (score == 0f) return null
        val suggestions = patternMatcher.suggestTemplatesForGoal(lower)
        return IntentMatch("custom_mod", minOf(score, 0.6f), suggestions)
    }

    private fun buildStepsForIntent(intent: String, templates: List<ModTemplate>): List<PlanStep> {
        val steps = mutableListOf<PlanStep>()

        return when (intent) {
            "remove_ads" -> {
                steps.add(PlanStep("analyze", mapOf(), "Analyzing APK structure"))
                steps.add(PlanStep("apply_mod", mapOf("template" to (templates.firstOrNull()?.id ?: "ads_remove_all")), "Removing ad components"))
                steps.add(PlanStep("rebuild", mapOf(), "Rebuilding modified APK"))
                steps
            }
            "unlock_premium" -> {
                steps.add(PlanStep("analyze", mapOf(), "Analyzing APK for premium checks"))
                steps.add(PlanStep("auto_patch_dex", mapOf(), "Auto-patching known license/premium methods"))
                steps.add(PlanStep("apply_mod", mapOf("template" to (templates.firstOrNull()?.id ?: "premium_force_true")), "Forcing premium checks"))
                steps.add(PlanStep("rebuild", mapOf(), "Rebuilding APK"))
                steps
            }
            "bypass_license" -> {
                steps.add(PlanStep("analyze", mapOf(), "Analyzing APK for license verification"))
                steps.add(PlanStep("auto_patch_dex", mapOf(), "Auto-patching license check methods"))
                steps.add(PlanStep("apply_mod", mapOf("template" to (templates.firstOrNull()?.id ?: "license_crack")), "Applying license bypass"))
                steps.add(PlanStep("rebuild", mapOf(), "Rebuilding"))
                steps
            }
            "patch_iap" -> {
                steps.add(PlanStep("analyze", mapOf(), "Analyzing billing implementation"))
                steps.add(PlanStep("apply_mod", mapOf("template" to "premium_iap_patch"), "Patching IAP"))
                steps.add(PlanStep("rebuild", mapOf(), "Rebuilding"))
                steps
            }
            "enable_debug" -> {
                steps.add(PlanStep("quick_mod", mapOf("template" to "debug_enable"), "Enabling debug mode"))
                steps.add(PlanStep("rebuild", mapOf(), "Rebuilding"))
                steps.add(PlanStep("verify", mapOf(), "Verifying mod"))
                steps
            }
            "bypass_ssl" -> {
                steps.add(PlanStep("quick_mod", mapOf("template" to "debug_ssl_bypass"), "Bypassing SSL pinning"))
                steps.add(PlanStep("rebuild", mapOf(), "Rebuilding"))
                steps
            }
            "disable_root_check" -> {
                steps.add(PlanStep("analyze", mapOf(), "Analyzing security checks"))
                steps.add(PlanStep("auto_patch_dex", mapOf(), "Auto-patching root/security checks"))
                steps.add(PlanStep("apply_mod", mapOf("template" to (templates.firstOrNull()?.id ?: "sec_disable_root_check")), "Disabling detections"))
                steps.add(PlanStep("rebuild", mapOf(), "Rebuilding"))
                steps
            }
            "modify_permissions" -> {
                steps.add(PlanStep("quick_mod", mapOf("template" to (templates.firstOrNull()?.id ?: "perm_remove_all")), "Modifying permissions"))
                steps.add(PlanStep("rebuild", mapOf(), "Rebuilding"))
                steps
            }
            "analyze_apk" -> {
                steps.add(PlanStep("analyze", mapOf(), "Analyzing APK"))
                steps
            }
            "decompile_apk" -> {
                steps.add(PlanStep("decompile", mapOf(), "Decompiling APK"))
                steps
            }
            "install_apk" -> {
                steps.add(PlanStep("install", mapOf(), "Installing APK"))
                steps
            }
            "show_help" -> emptyList()
            "custom_mod" -> {
                if (templates.isNotEmpty()) {
                    steps.add(PlanStep("analyze", mapOf(), "Analyzing APK"))
                    templates.forEach { t ->
                        steps.add(PlanStep("apply_mod", mapOf("template" to t.id), "Applying: ${t.name}"))
                    }
                    steps.add(PlanStep("rebuild", mapOf(), "Rebuilding"))
                } else {
                    steps.add(PlanStep("analyze", mapOf(), "Analyzing APK to find mod opportunities"))
                }
                steps
            }
            else -> {
                steps.add(PlanStep("analyze", mapOf(), "Analyzing APK"))
                steps
            }
        }
    }

    private fun executePlan(plan: ActionPlan): List<StepResult> {
        val results = mutableListOf<StepResult>()
        val apkPath = currentApkPath

        if (plan.requiresApk && apkPath == null) {
            results.add(StepResult("error", "No APK loaded. Select an APK file first."))
            return results
        }

        for (step in plan.steps) {
            val result = when (step.action) {
                "analyze" -> {
                    val analysis = if (apkPath != null) analyzeCurrentApk() else "APK analysis requires a loaded APK."
                    StepResult("analysis", analysis)
                }
                "auto_patch_dex" -> {
                    if (apkPath != null) {
                        val dexDir = java.io.File(toolManager.getWorkDir(), "dex").also { it.mkdirs() }
                        val info = engine.analyzeApk(apkPath)
                        if (info != null) {
                            val dexFiles = java.io.File(toolManager.getWorkDir(), "dex_extracted").also { it.mkdirs() }
                            com.patchmaster.util.FileUtils.extractDexFiles(apkPath, dexFiles)
                            val analysis = patternMatcher.analyze(info, dexFiles.listFiles()?.toList() ?: emptyList())
                            val patched = engine.directDexPatch(dexFiles, analysis)
                            StepResult("dex_patch", "Auto-patched $patched method(s) in DEX")
                        } else StepResult("error", "Analysis failed")
                    } else StepResult("error", "No APK loaded")
                }
                "apply_mod" -> {
                    val templateId = step.params["template"] ?: ""
                    val template = ModTemplateLibrary.findById(templateId)
                    if (template != null && apkPath != null) {
                        val result = engine.quickMod(apkPath, templateId)
                        if (result.success) {
                            currentApkPath = result.outputPath
                            StepResult("mod_applied", "✅ ${template.name} applied successfully\nOutput: ${result.outputPath}")
                        } else {
                            StepResult("error", "❌ ${template.name} failed: ${result.error}")
                        }
                    } else StepResult("error", "Template '${templateId}' not found or no APK")
                }
                "quick_mod" -> {
                    val templateId = step.params["template"] ?: ""
                    if (apkPath != null) {
                        val result = engine.quickMod(apkPath, templateId)
                        if (result.success) {
                            currentApkPath = result.outputPath
                            StepResult("mod_applied", "✅ Quick mod applied\nOutput: ${result.outputPath}")
                        } else StepResult("error", "❌ Quick mod failed: ${result.error}")
                    } else StepResult("error", "No APK loaded")
                }
                "rebuild" -> {
                    // Already handled in apply_mod
                    StepResult("info", "Rebuild completed as part of mod application")
                }
                "decompile" -> {
                    if (apkPath != null) {
                        val workDir = toolManager.getWorkDir()
                        val decompiledDir = java.io.File(workDir, "decompiled")
                        val success = engine.fullDecompile(apkPath, decompiledDir)
                        if (success) StepResult("decompiled", "Decompiled to: ${decompiledDir.absolutePath}")
                        else StepResult("error", "Decompilation failed")
                    } else StepResult("error", "No APK loaded")
                }
                "install" -> {
                    if (apkPath != null) {
                        val success = engine.installApk(apkPath)
                        if (success) StepResult("installed", "APK installed successfully")
                        else StepResult("error", "Install failed")
                    } else StepResult("error", "No APK loaded")
                }
                "verify" -> {
                    if (apkPath != null) {
                        val info = engine.analyzeApk(apkPath)
                        if (info != null) StepResult("verified", "APK verified: ${info.label} (${info.packageName})")
                        else StepResult("error", "Verification failed")
                    } else StepResult("error", "No APK")
                }
                else -> StepResult("unknown", "Unknown step: ${step.action}")
            }
            results.add(result)
        }
        return results
    }

    private data class StepResult(val type: String, val message: String)

    private fun buildResponse(plan: ActionPlan, results: List<StepResult>): String {
        if (plan.intent == "show_help") return getHelpText()
        if (plan.steps.isEmpty()) return getHelpText()

        val sb = StringBuilder()
        val hasError = results.any { it.type == "error" }

        if (hasError) {
            sb.appendLine("⚠️ **Mod encountered issues**")
        } else {
            sb.appendLine("✅ **Mod complete!**")
        }

        sb.appendLine()
        for ((i, step) in plan.steps.withIndex()) {
            val result = if (i < results.size) results[i] else null
            val icon = when {
                result?.type == "error" -> "❌"
                result == null -> "⏳"
                else -> "✅"
            }
            sb.appendLine("$icon **${step.description}**")
            if (result != null && result.type != "info") {
                sb.appendLine("  ${result.message.take(300)}")
            }
        }

        if (plan.suggestedTemplates.isNotEmpty() && hasError) {
            sb.appendLine()
            sb.appendLine("**Alternative approaches:**")
            plan.suggestedTemplates.take(3).forEach { t ->
                sb.appendLine("- `${t.id}`: ${t.description}")
            }
        }

        if (!hasError && currentApkPath != null) {
            sb.appendLine()
            sb.appendLine("**Output:** `${currentApkPath}`")
            sb.appendLine("You can now install or save the modded APK.")
        }

        return sb.toString()
    }

    private fun getHelpText(): String {
        return """**Ares APK Modification Agent**

I can modify Android APK files. Here's what I can do:

**🗑️ Ad Removal**
"Remove ads from this app" — Removes ad libraries and components

**🔓 Premium Unlock**
"Unlock premium features" — Forces premium/pro checks to pass
"Patch in-app purchases" — Bypasses IAP billing

**🛡️ License Bypass**
"Bypass license verification" — Cracks LVL and signature checks
"Remove trial limitations" — Removes demo/trial restrictions

**🔧 Modifications**
"Enable debugging" — Makes APK debuggable
"Bypass SSL pinning" — Disables certificate pinning
"Disable root detection" — Removes root checks
"Modify permissions" — Add or remove permissions

**📋 Other**
"Analyze this APK" — Shows APK details
"Decompile this APK" — Extracts all code and resources
"Install the modded APK" — Installs using package manager

**How to use:**
1. Select an APK file using the 📂 button
2. Tell me what you want to do in plain English
3. I'll handle the technical steps automatically"""
    }

    fun addMessage(message: AgentMessage) {
        _messages.value = _messages.value + message
    }

    fun clearConversation() {
        _messages.value = emptyList()
    }
}
