package com.patchmaster.agent

import android.content.Context

class SkillEngine(private val context: Context) {
    private val skillCache = mutableMapOf<String, String>()

    fun getSkill(name: String = "apk_modding"): String {
        return skillCache.getOrPut(name) {
            when (name) {
                "apk_modding" -> SkillLibrary.skillContent
                "smali_reference" -> loadSkillFromAssets("skills/smali_reference.md")
                "tool_usage" -> loadSkillFromAssets("skills/tool_usage.md")
                else -> SkillLibrary.skillContent
            }
        }
    }

    fun searchSkill(query: String): String {
        val skill = getSkill()
        val lines = skill.lines()
        val results = mutableListOf<String>()
        val queryLower = query.lowercase()

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.lowercase().contains(queryLower)) {
                val start = maxOf(0, i - 2)
                val end = minOf(lines.size - 1, i + 5)
                for (j in start..end) {
                    results.add(lines[j])
                }
                results.add("---")
            }
            i++
        }

        return if (results.isEmpty()) "No matches found for: $query"
        else results.joinToString("\n")
    }

    fun generateModPlan(apkInfo: String, goal: String): String {
        val skill = getSkill()

        val prompt = """
You are PatchMaster's APK modding AI. Using the following skill knowledge and the APK information provided, create a step-by-step modification plan.

SKILL REFERENCE:
$skill

APK INFO:
$apkInfo

MOD GOAL:
$goal

Create a detailed plan with:
1. What to modify (specific files and methods)
2. What smali changes to make
3. What manifest changes to make
4. What resource changes to make
5. The exact instructions for each change
""".trimIndent()

        return prompt
    }

    private fun loadSkillFromAssets(path: String): String {
        return try {
            context.assets.open(path).bufferedReader().readText()
        } catch (e: Exception) {
            "# $path not found\n"
        }
    }
}
