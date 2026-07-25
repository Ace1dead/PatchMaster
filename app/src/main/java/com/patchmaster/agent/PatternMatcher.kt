package com.patchmaster.agent

import com.patchmaster.model.ApkInfo
import com.patchmaster.model.ModTemplate
import com.patchmaster.model.ModTemplateLibrary
import com.patchmaster.engine.DexPatcher
import java.io.File

class PatternMatcher {
    data class AnalysisResult(
        val apkInfo: ApkInfo,
        val matchedTemplates: List<MatchedTemplate>,
        val detectedPatterns: Map<String, List<String>> = emptyMap(),
        val riskScore: Int = 0,
        val recommendations: List<String> = emptyList()
    )

    data class MatchedTemplate(
        val template: ModTemplate,
        val confidence: Float,
        val evidence: List<String> = emptyList()
    )

    fun analyze(apkInfo: ApkInfo, dexFiles: List<File> = emptyList()): AnalysisResult {
        val matched = mutableListOf<MatchedTemplate>()
        val detected = mutableMapOf<String, MutableList<String>>()
        val allStrings = extractAllStrings(dexFiles)

        for (template in ModTemplateLibrary.templates) {
            if (template.detectionPatterns.isEmpty()) continue
            val (matches, confidence) = scoreTemplate(template, apkInfo, allStrings)
            if (confidence > 0.3f) {
                matched.add(MatchedTemplate(template, confidence, matches))
            }
        }

        val recommendations = generateRecommendations(matched, apkInfo)
        val riskScore = matched.sumOf { it.template.riskLevel.ordinal }

        // Detect components
        detected["activities"] = apkInfo.activities.toMutableList()
        detected["services"] = apkInfo.services.toMutableList()
        detected["permissions"] = apkInfo.permissions.toMutableList()

        return AnalysisResult(
            apkInfo = apkInfo,
            matchedTemplates = matched.sortedByDescending { it.confidence },
            detectedPatterns = detected,
            riskScore = riskScore,
            recommendations = recommendations
        )
    }

    private fun scoreTemplate(
        template: ModTemplate,
        apkInfo: ApkInfo,
        allStrings: List<String>
    ): Pair<List<String>, Float> {
        val evidence = mutableListOf<String>()
        var score = 0f
        val patterns = template.detectionPatterns

        for (pattern in patterns) {
            val lower = pattern.lowercase()

            // Check strings
            val stringMatches = allStrings.count { it.lowercase().contains(lower) }
            if (stringMatches > 0) {
                score += 0.15f * minOf(stringMatches, 5)
                evidence.add("Found '$pattern' in $stringMatches string(s)")
            }

            // Check component names
            val componentMatches = (apkInfo.activities + apkInfo.services + apkInfo.receivers + apkInfo.providers)
                .count { it.lowercase().contains(lower) }
            if (componentMatches > 0) {
                score += 0.2f * minOf(componentMatches, 3)
                evidence.add("Found '$pattern' in ${componentMatches} component(s)")
            }

            // Check permissions
            val permMatches = apkInfo.permissions.count { it.lowercase().contains(lower) }
            if (permMatches > 0) {
                score += 0.1f * permMatches
                evidence.add("Found '$pattern' in permissions")
            }
        }

        return evidence to minOf(score, 1.0f)
    }

    private fun extractAllStrings(dexFiles: List<File>): List<String> {
        val result = mutableListOf<String>()
        for (file in dexFiles) {
            try {
                val patcher = DexPatcher()
                if (patcher.load(file)) {
                    val count = patcher.getStringCount()
                    for (i in 0 until minOf(count, 2000)) {
                        patcher.getString(i)?.let { result.add(it) }
                    }
                }
            } catch (e: Exception) { /* skip unreadable DEX */ }
        }
        return result
    }

    private fun generateRecommendations(
        matched: List<MatchedTemplate>,
        apkInfo: ApkInfo
    ): List<String> {
        val recs = mutableListOf<String>()
        val categories = matched.map { it.template.category }.distinct()

        if (matched.any { it.template.category == ModTemplate.Category.ADS } && matched.none { it.confidence > 0.7f }) {
            recs.add("Ad libraries detected. Consider ad removal templates.")
        }
        if (matched.any { it.template.category == ModTemplate.Category.PREMIUM }) {
            recs.add("Premium/pro features detected. Consider unlocking.")
        }
        if (matched.any { it.template.category == ModTemplate.Category.LICENSE }) {
            recs.add("License verification detected. Consider bypassing.")
        }
        if (matched.any { it.template.category == ModTemplate.Category.SECURITY }) {
            recs.add("Security/root detection detected. Consider disabling.")
        }

        if (apkInfo.isDebuggable) {
            recs.add("APK is already debuggable.")
        } else {
            recs.add("Enable debug mode for easier testing.")
        }

        if (apkInfo.permissions.contains("android.permission.INTERNET")) {
            recs.add("Has internet permission. Could be phoning home.")
        }

        return recs
    }

    fun suggestTemplatesForGoal(goal: String): List<ModTemplate> {
        val lower = goal.lowercase()
        val keywordMap = mapOf(
            "ad" to "ads_remove_all",
            "ads" to "ads_remove_all",
            "advert" to "ads_remove_all",
            "premium" to "premium_force_true",
            "pro" to "premium_force_true",
            "unlock" to "premium_force_true",
            "purchase" to "premium_iap_patch",
            "iap" to "premium_iap_patch",
            "in-app" to "premium_iap_patch",
            "license" to "license_crack",
            "lvl" to "premium_lvl_bypass",
            "signature" to "license_signature_bypass",
            "bypass" to "premium_iap_patch",
            "subscription" to "premium_subscription_bypass",
            "subscrib" to "premium_subscription_bypass",
            "crack" to "license_crack",
            "debug" to "debug_enable",
            "ssl" to "debug_ssl_bypass",
            "pinning" to "debug_ssl_bypass",
            "root" to "sec_disable_root_check",
            "emulator" to "sec_disable_emulator_check",
            "trial" to "license_remove_trial",
            "analytics" to "tweak_remove_analytics",
            "facebook" to "tweak_remove_facebook_sdk",
            "permission" to "perm_remove_all",
            "dpi" to "tweak_dpi_change",
            "toast" to "tweak_inject_toast",
            "splash" to "tweak_remove_splash",
            "energy" to "tweak_unlimited_energy",
            "life" to "tweak_unlimited_energy",
            "coin" to "tweak_unlimited_energy",
            "gem" to "tweak_unlimited_energy",
            "hooking" to "sec_disable_anti_hooking",
            "xposed" to "sec_disable_anti_hooking",
            "frida" to "sec_disable_anti_hooking",
            "rename" to "tweak_package_name",
            "rebrand" to "tweak_package_name"
        )

        val matched = keywordMap.filter { (key) -> lower.contains(key) }
        return matched.values.mapNotNull { ModTemplateLibrary.findById(it) }.distinct()
    }
}
