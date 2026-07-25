package com.patchmaster.engine

import android.content.Context
import android.util.Log
import com.patchmaster.model.ToolInfo
import com.patchmaster.model.ToolType
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

class ToolManager(private val context: Context) {
    private val toolsDir = File(context.filesDir, "tools").also { it.mkdirs() }
    private val workDir = File(context.filesDir, "workspace").also { it.mkdirs() }

    val availableTools = mutableMapOf<String, ToolInfo>()

    private val requiredTools = listOf(
        ToolSpec("aapt2", ToolType.NATIVE, "aapt2_arm64"),
        ToolSpec("zipalign", ToolType.NATIVE, "zipalign_arm64"),
        ToolSpec("apksigner", ToolType.NATIVE, "apksigner_arm64"),
        ToolSpec("baksmali", ToolType.JAVA_JAR, "baksmali.jar"),
        ToolSpec("smali", ToolType.JAVA_JAR, "smali.jar")
    )

    fun discoverTools() {
        availableTools.clear()
        for (spec in requiredTools) {
            val toolFile = File(toolsDir, spec.assetName)
            val exists = toolFile.exists() && toolFile.canExecute()
            availableTools[spec.name] = ToolInfo(
                name = spec.name,
                path = toolFile.absolutePath,
                isAvailable = exists,
                type = spec.type
            )
        }
    }

    fun isToolAvailable(name: String): Boolean =
        availableTools[name]?.isAvailable == true

    fun getTool(name: String): ToolInfo? = availableTools[name]

    fun getToolPath(name: String): String? =
        availableTools[name]?.takeIf { it.isAvailable }?.path

    fun ensureToolsInstalled(): Boolean {
        val allInstalled = requiredTools.all { spec ->
            val toolFile = File(toolsDir, spec.assetName)
            if (toolFile.exists()) return@all true
            extractTool(spec)
        }
        discoverTools()
        return allInstalled
    }

    private fun extractTool(spec: ToolSpec): Boolean {
        try {
            val targetFile = File(toolsDir, spec.assetName)
            context.assets.open("tools/${spec.assetName}").use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (spec.type == ToolType.NATIVE) {
                targetFile.setExecutable(true)
            }
            return true
        } catch (e: Exception) {
            Log.e("ToolManager", "Failed to extract ${spec.name}", e)
            return false
        }
    }

    fun downloadFromNetwork(toolName: String, url: String): Boolean {
        return try {
            val spec = requiredTools.find { it.name == toolName } ?: return false
            val targetFile = File(toolsDir, spec.assetName)
            val connection = java.net.URL(url).openConnection()
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.getInputStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (spec.type == ToolType.NATIVE) {
                targetFile.setExecutable(true)
            }
            discoverTools()
            true
        } catch (e: Exception) {
            Log.e("ToolManager", "Download failed for $toolName", e)
            false
        }
    }

    fun getWorkDir(): File = File(workDir, "current_${System.currentTimeMillis()}").also { it.mkdirs() }

    fun createOutputDir(): File = File(context.filesDir, "output").also { it.mkdirs() }

    fun createKeystoreDir(): File = File(context.filesDir, "keystore").also { it.mkdirs() }

    fun getJavaRuntime(): String? {
        val candidates = listOf(
            "/system/bin/dalvikvm",
            "/system/bin/app_process",
            "/system/bin/app_process64"
        )
        return candidates.find { File(it).exists() }
    }

    fun executeCommand(cmd: List<String>, workDir: File? = null): CommandResult {
        return try {
            val pb = ProcessBuilder(cmd)
            pb.directory(workDir ?: this.workDir)
            pb.redirectErrorStream(true)
            val process = pb.start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            CommandResult(exitCode, output, null)
        } catch (e: Exception) {
            CommandResult(-1, "", e.message ?: "Execution failed")
        }
    }

    data class CommandResult(val exitCode: Int, val stdout: String, val stderr: String?)

    private data class ToolSpec(val name: String, val type: ToolType, val assetName: String)
}
