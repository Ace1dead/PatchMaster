package com.patchmaster.agent

import com.patchmaster.PatchMasterApp
import com.patchmaster.model.ModAction
import com.patchmaster.model.ModScript
import java.io.File

class ToolExecutor {
    private val engine get() = PatchMasterApp.instance.toolManager
    private val apkEngine = PatchMasterApp.instance.let { app ->
        com.patchmaster.engine.ApkEngine(app)
    }

    fun execute(command: String, args: Map<String, String> = emptyMap()): ExecResult {
        return when (command) {
            "decompile" -> decompileApk(args["apk_path"] ?: return error("apk_path required"))
            "modify_manifest" -> modifyManifest(args)
            "modify_smali" -> modifySmali(args)
            "nop_method" -> nopMethod(args)
            "add_permission" -> addPermission(args)
            "remove_permission" -> removePermission(args)
            "edit_resource" -> editResource(args)
            "file_replace" -> fileReplace(args)
            "rebuild" -> rebuildApk(args["input_dir"] ?: return error("input_dir required"),
                args["output_apk"] ?: return error("output_apk required"))
            "sign" -> signApk(args["apk_path"] ?: return error("apk_path required"))
            "install" -> installApk(args["apk_path"] ?: return error("apk_path required"))
            "analyze" -> analyzeApk(args["apk_path"] ?: return error("apk_path required"))
            "run_script" -> runScript(args)
            "search_string" -> searchInSmali(args)
            "read_file" -> readFile(args["path"] ?: return error("path required"))
            "write_file" -> writeFile(args)
            "shell" -> runShell(args["cmd"] ?: return error("cmd required"))
            "list_smali" -> listSmaliFiles(args["dir"] ?: return error("dir required"))
            "get_skill" -> execResult(0, SkillEngine(PatchMasterApp.instance).getSkill())
            else -> error("Unknown command: $command")
        }
    }

    fun executeScript(script: ModScript, apkPath: String): ExecResult {
        val success = apkEngine.applyModScript(apkPath, script)
        return if (success) {
            val outputPath = apkEngine.getModdedApkPath(script.name)
            execResult(0, "Script executed successfully. Output: $outputPath")
        } else {
            execResult(1, "Script execution failed")
        }
    }

    private fun decompileApk(apkPath: String): ExecResult {
        val workDir = File(engine.getWorkDir(), "decompiled")
        val success = apkEngine.decompileApk(apkPath, workDir)
        return if (success) execResult(0, workDir.absolutePath)
        else execResult(1, "Decompilation failed")
    }

    private fun modifyManifest(args: Map<String, String>): ExecResult {
        val manifestFile = File(args["manifest"] ?: return error("manifest required"))
        if (!manifestFile.exists()) return execResult(1, "Manifest not found")
        val action = ModAction.ManifestEdit(
            path = args["attr"] ?: return error("attr required"),
            value = args["value"] ?: return error("value required"),
            action = com.patchmaster.model.ActionType.valueOf(
                args["action"]?.uppercase() ?: "SET"
            )
        )
        com.patchmaster.engine.ManifestEditor.edit(manifestFile, action)
        return execResult(0, "Manifest modified: ${action.path} = ${action.value}")
    }

    private fun modifySmali(args: Map<String, String>): ExecResult {
        val decompiledDir = File(args["decompiled_dir"] ?: return error("decompiled_dir required"))
        val action = ModAction.SmaliEdit(
            file = args["smali_file"] ?: return error("smali_file required"),
            find = args["find"] ?: return error("find required"),
            replace = args["replace"] ?: return error("replace required"),
            method = args["method"]
        )
        com.patchmaster.engine.SmaliEditor.editInSmali(decompiledDir, action)
        return execResult(0, "Smali modified")
    }

    private fun nopMethod(args: Map<String, String>): ExecResult {
        val decompiledDir = File(args["decompiled_dir"] ?: return error("decompiled_dir required"))
        val className = args["class"] ?: return error("class required")
        val method = args["method"] ?: return error("method required")
        com.patchmaster.engine.SmaliEditor.nopMethod(decompiledDir, className, method)
        return execResult(0, "Method nop'd: $className.$method")
    }

    private fun addPermission(args: Map<String, String>): ExecResult {
        val manifestFile = File(args["manifest"] ?: return error("manifest required"))
        com.patchmaster.engine.ManifestEditor.addPermission(manifestFile, args["permission"] ?: return error("permission required"))
        return execResult(0, "Permission added")
    }

    private fun removePermission(args: Map<String, String>): ExecResult {
        val manifestFile = File(args["manifest"] ?: return error("manifest required"))
        com.patchmaster.engine.ManifestEditor.removePermission(manifestFile, args["permission"] ?: return error("permission required"))
        return execResult(0, "Permission removed")
    }

    private fun editResource(args: Map<String, String>): ExecResult {
        val decompiledDir = File(args["decompiled_dir"] ?: return error("decompiled_dir required"))
        val action = ModAction.ResourceEdit(
            resource = args["name"] ?: return error("name required"),
            value = args["value"] ?: return error("value required"),
            type = args["type"] ?: "string"
        )
        com.patchmaster.engine.ResourcePacker.editResource(decompiledDir, action)
        return execResult(0, "Resource edited")
    }

    private fun fileReplace(args: Map<String, String>): ExecResult {
        val from = File(args["from"] ?: return error("from required"))
        val to = File(args["to"] ?: return error("to required"))
        from.copyTo(to, overwrite = true)
        return execResult(0, "File replaced: $from -> $to")
    }

    private fun rebuildApk(inputDir: String, outputApk: String): ExecResult {
        val success = apkEngine.rebuildApk(File(inputDir), File(outputApk))
        return if (success) execResult(0, "APK rebuilt: $outputApk")
        else execResult(1, "Rebuild failed")
    }

    private fun signApk(apkPath: String): ExecResult {
        val success = apkEngine.signApk(File(apkPath))
        return if (success) execResult(0, "APK signed: $apkPath")
        else execResult(1, "Signing failed")
    }

    private fun installApk(apkPath: String): ExecResult {
        val success = apkEngine.installApk(apkPath)
        return if (success) execResult(0, "APK installed")
        else execResult(1, "Install failed. Try installing manually.")
    }

    private fun analyzeApk(apkPath: String): ExecResult {
        val info = apkEngine.analyzeApk(apkPath)
        return if (info != null) execResult(0, info.toString())
        else execResult(1, "Analysis failed")
    }

    private fun runScript(args: Map<String, String>): ExecResult {
        val scriptName = args["name"] ?: "custom_script"
        val apkPath = args["apk_path"] ?: return error("apk_path required")
        val moddedApk = args["modded_apk"] ?: "${engine.createOutputDir()}/modded.apk"

        val actions = mutableListOf<ModAction>()
        var i = 0
        while (args.containsKey("action_${i}_type")) {
            val type = args["action_${i}_type"] ?: break
            val action = when (type) {
                "manifest_set" -> ModAction.ManifestEdit(
                    path = args["action_${i}_attr"] ?: "",
                    value = args["action_${i}_value"] ?: ""
                )
                "smali_edit" -> ModAction.SmaliEdit(
                    file = args["action_${i}_file"] ?: "",
                    find = args["action_${i}_find"] ?: "",
                    replace = args["action_${i}_replace"] ?: ""
                )
                "permission_add" -> ModAction.PermissionAdd(
                    permission = args["action_${i}_permission"] ?: ""
                )
                "permission_remove" -> ModAction.PermissionRemove(
                    permission = args["action_${i}_permission"] ?: ""
                )
                "resource_edit" -> ModAction.ResourceEdit(
                    resource = args["action_${i}_resource"] ?: "",
                    value = args["action_${i}_value"] ?: ""
                )
                "nop_method" -> ModAction.NopMethod(
                    className = args["action_${i}_class"] ?: "",
                    methodName = args["action_${i}_method"] ?: ""
                )
                "shell" -> ModAction.ShellExec(
                    command = args["action_${i}_cmd"] ?: ""
                )
                else -> return execResult(1, "Unknown action type: $type")
            }
            actions.add(action)
            i++
        }

        val script = ModScript(
            name = scriptName,
            description = args["description"] ?: "Auto-generated script",
            targetPackage = args["target_package"],
            actions = actions
        )

        return executeScript(script, apkPath)
    }

    private fun searchInSmali(args: Map<String, String>): ExecResult {
        val smaliDir = File(args["decompiled_dir"] ?: return error("decompiled_dir required"))
        val query = args["query"] ?: return error("query required")
        val queryLower = query.lowercase()

        val results = smaliDir.walkTopDown()
            .filter { it.extension == "smali" }
            .mapNotNull { file ->
                val lines = file.readLines()
                val matches = lines.filter { it.lowercase().contains(queryLower) }
                if (matches.isNotEmpty()) {
                    file.relativeTo(smaliDir).path to matches
                } else null
            }
            .toList()

        val output = results.joinToString("\n\n") { (path, matches) ->
            "$path:\n${matches.joinToString("\n") { "  $it" }}"
        }

        return execResult(0, output.ifEmpty { "No matches for: $query" })
    }

    private fun readFile(args: Map<String, String>): ExecResult {
        val file = File(args["path"] ?: return error("path required"))
        if (!file.exists()) return execResult(1, "File not found")
        val content = file.readText()
        val maxLen = args["max_length"]?.toIntOrNull() ?: 50000
        val truncated = if (content.length > maxLen) content.take(maxLen) + "\n... [truncated]" else content
        return execResult(0, truncated)
    }

    private fun writeFile(args: Map<String, String>): ExecResult {
        val file = File(args["path"] ?: return error("path required"))
        val content = args["content"] ?: return error("content required")
        file.parentFile?.mkdirs()
        file.writeText(content)
        return execResult(0, "Written: ${file.absolutePath} (${content.length} bytes)")
    }

    private fun runShell(cmd: String): ExecResult {
        val parts = cmd.split("\\s+".toRegex())
        val result = engine.executeCommand(parts, engine.getWorkDir())
        return execResult(result.exitCode, result.stdout)
    }

    private fun listSmaliFiles(dir: String): ExecResult {
        val smaliDir = File(dir)
        if (!smaliDir.exists()) return execResult(1, "Directory not found")
        val files = smaliDir.walkTopDown()
            .filter { it.extension == "smali" }
            .map { it.relativeTo(smaliDir).path }
            .toList()
        return execResult(0, files.joinToString("\n"))
    }

    private fun error(msg: String): ExecResult {
        return ExecResult(1, msg, isError = true)
    }

    private fun execResult(code: Int, output: String): ExecResult {
        return ExecResult(code, output, isError = code != 0)
    }

    data class ExecResult(val exitCode: Int, val output: String, val isError: Boolean = false)
}
