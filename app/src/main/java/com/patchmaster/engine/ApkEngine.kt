package com.patchmaster.engine

import android.content.Context
import android.content.pm.PackageManager
import android.util.Base64
import com.patchmaster.PatchMasterApp
import com.patchmaster.model.ApkInfo
import com.patchmaster.model.ModAction
import com.patchmaster.model.ModScript
import com.patchmaster.model.ModTemplate
import com.patchmaster.model.ModTemplateLibrary
import com.patchmaster.agent.PatternMatcher
import java.io.*
import java.security.MessageDigest
import java.util.jar.JarFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipInputStream

class ApkEngine(private val context: Context) {
    private val toolManager get() = PatchMasterApp.instance.toolManager
    private val console get() = com.patchmaster.ui.screens.ConsoleLog.instance
    private val patternMatcher = PatternMatcher()

    fun analyzeApk(apkPath: String): ApkInfo? {
        val apkFile = File(apkPath)
        if (!apkFile.exists()) { console.error("APK not found: $apkPath"); return null }

        return try {
            val pm = context.packageManager
            val pkgInfo = pm.getPackageArchiveInfo(apkPath,
                PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS or
                PackageManager.GET_PERMISSIONS)
            if (pkgInfo == null) { console.error("PackageManager could not parse APK"); return null }

            val appInfo = pkgInfo.applicationInfo
            appInfo.sourceDir = apkPath
            appInfo.publicSourceDir = apkPath

            val label = pm.getApplicationLabel(appInfo)?.toString() ?: pkgInfo.packageName ?: "Unknown"
            val jarFile = JarFile(apkFile)
            val sigDigest = getSignatureDigest(jarFile)
            val nativeLibs = listNativeLibs(jarFile)
            val dexCount = countDexFiles(jarFile)
            jarFile.close()

            console.success("Analyzed: $label (${pkgInfo.packageName})")
            ApkInfo(
                packageName = pkgInfo.packageName ?: "",
                versionName = pkgInfo.versionName ?: "",
                versionCode = pkgInfo.versionCode,
                minSdk = appInfo.minSdkVersion,
                targetSdk = appInfo.targetSdkVersion,
                label = label,
                permissions = pkgInfo.requestedPermissions?.toList() ?: emptyList(),
                activities = pkgInfo.activities?.map { it.name }?.toList() ?: emptyList(),
                services = pkgInfo.services?.map { it.name }?.toList() ?: emptyList(),
                receivers = pkgInfo.receivers?.map { it.name }?.toList() ?: emptyList(),
                providers = pkgInfo.providers?.map { it.name }?.toList() ?: emptyList(),
                signatureDigest = sigDigest,
                isDebuggable = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0,
                nativeLibs = nativeLibs,
                dexCount = dexCount,
                fileSize = apkFile.length(),
                filePath = apkPath
            )
        } catch (e: Exception) {
            console.error("Analysis error: ${e.message}")
            null
        }
    }

    data class PipelineResult(val success: Boolean, val outputPath: String? = null, val error: String? = null)

    fun runPipeline(apkPath: String, patchMode: PatchMode = PatchMode.SMART): PipelineResult {
        val startTime = System.currentTimeMillis()
        console.info("Starting pipeline: ${File(apkPath).name} (mode=$patchMode)")

        return try {
            val info = analyzeApk(apkPath) ?: return PipelineResult(false, error = "Analysis failed")
            val workDir = toolManager.getWorkDir()
            val decompiledDir = File(workDir, "decompiled")
            val dexDir = File(workDir, "dex")
            dexDir.mkdirs()

            // Extract DEX files
            val dexFiles = extractDexFiles(apkPath, dexDir)
            console.info("Extracted ${dexFiles.size} DEX files")

            // Auto-detect mod patterns
            val analysis = patternMatcher.analyze(info, dexFiles)
            if (analysis.matchedTemplates.isNotEmpty()) {
                console.info("Auto-detected patterns:")
                analysis.matchedTemplates.take(5).forEach {
                    console.info("  [${(it.confidence * 100).toInt()}%] ${it.template.name}")
                }
            }

            when (patchMode) {
                PatchMode.QUICK_DEX -> {
                    // Direct DEX patching without decompile
                    val patched = directDexPatch(dexDir, analysis)
                    if (patched > 0) {
                        console.success("Direct-patched $patched method(s) in DEX")
                        val repacked = repackApk(apkPath, dexDir, File(workDir, "patched.apk"))
                        if (repacked) {
                            val signed = signApk(File(workDir, "patched.apk"))
                            if (signed) {
                                val elapsed = (System.currentTimeMillis() - startTime) / 1000
                                console.success("Pipeline complete in ${elapsed}s")
                                return PipelineResult(true, workDir.absolutePath + "/patched.apk")
                            }
                        }
                    }
                    PipelineResult(false, error = "DEX patching failed or no patches applied")
                }
                PatchMode.FULL_DECOMPILE -> {
                    if (!fullDecompile(apkPath, decompiledDir)) {
                        return PipelineResult(false, error = "Decompilation failed")
                    }
                    PipelineResult(true, outputPath = decompiledDir.absolutePath)
                }
                PatchMode.SMART -> {
                    // Try DEX patching first for simple patches, fall back to full decompile
                    val simplePatches = analysis.matchedTemplates.filter {
                        !it.template.requiresFullDecompile && it.confidence > 0.5f
                    }
                    if (simplePatches.isNotEmpty()) {
                        val patched = directDexPatch(dexDir, analysis)
                        if (patched > 0) {
                            console.info("Quick patches applied. Use FULL_DECOMPILE for complex mods.")
                        }
                    }
                    if (!fullDecompile(apkPath, decompiledDir)) {
                        return PipelineResult(false, error = "Decompilation failed")
                    }
                    val elapsed = (System.currentTimeMillis() - startTime) / 1000
                    console.success("Pipeline complete in ${elapsed}s")
                    PipelineResult(true, outputPath = decompiledDir.absolutePath)
                }
            }
        } catch (e: Exception) {
            console.error("Pipeline error: ${e.message}")
            PipelineResult(false, error = e.message)
        }
    }

    enum class PatchMode { QUICK_DEX, FULL_DECOMPILE, SMART }

    fun fullDecompile(apkPath: String, outputDir: File): Boolean {
        val aapt2 = toolManager.getToolPath("aapt2")
        val baksmali = toolManager.getToolPath("baksmali")
        val dalvikvm = toolManager.getJavaRuntime()

        outputDir.mkdirs()
        val manifestDir = File(outputDir, "manifest").also { it.mkdirs() }
        val resourcesDir = File(outputDir, "res").also { it.mkdirs() }
        val smaliDir = File(outputDir, "smali").also { it.mkdirs() }
        val dexDir = File(outputDir, "dex").also { it.mkdirs() }
        val libDir = File(outputDir, "lib").also { it.mkdirs() }
        val originalDir = File(outputDir, "original").also { it.mkdirs() }
        val unknownDir = File(outputDir, "unknown").also { it.mkdirs() }

        try {
            val jarFile = JarFile(File(apkPath))

            // Extract AndroidManifest.xml
            jarFile.getEntry("AndroidManifest.xml")?.let { entry ->
                jarFile.getInputStream(entry).use { inp ->
                    File(originalDir, "AndroidManifest.xml").outputStream().use { it.write(inp.readBytes()) }
                    File(manifestDir, "AndroidManifest.xml").outputStream().use { it.write(File(originalDir, "AndroidManifest.xml").readBytes()) }
                }
            }

            // Extract resources
            jarFile.entries().asSequence()
                .filter { it.name.startsWith("res/") && !it.isDirectory }
                .forEach { entry ->
                    val outFile = File(outputDir, entry.name)
                    outFile.parentFile?.mkdirs()
                    jarFile.getInputStream(entry).use { inp -> outFile.outputStream().use { it.write(inp.readBytes()) } }
                }

            // Extract and disassemble DEX
            if (baksmali != null && dalvikvm != null) {
                jarFile.entries().asSequence()
                    .filter { it.name.startsWith("classes") && it.name.endsWith(".dex") }
                    .forEach { entry ->
                        val dexFile = File(dexDir, entry.name)
                        jarFile.getInputStream(entry).use { inp -> dexFile.outputStream().use { it.write(inp.readBytes()) } }
                        val smaliOut = File(smaliDir, entry.name.removeSuffix(".dex"))
                        toolManager.executeCommand(listOf(
                            dalvikvm, "-cp", baksmali, "org.jf.baksmali.Main",
                            "d", dexFile.absolutePath, "-o", smaliOut.absolutePath
                        ))
                    }
            } else {
                // Fallback: just extract DEX files
                jarFile.entries().asSequence()
                    .filter { it.name.startsWith("classes") && it.name.endsWith(".dex") }
                    .forEach { entry ->
                        jarFile.getInputStream(entry).use { inp ->
                            File(dexDir, entry.name).outputStream().use { it.write(inp.readBytes()) }
                        }
                    }
                console.warn("baksmali not available - DEX extracted as binary only")
            }

            // Extract native libs
            jarFile.entries().asSequence()
                .filter { it.name.startsWith("lib/") && !it.isDirectory }
                .forEach { entry ->
                    val outFile = File(libDir, entry.name.removePrefix("lib/"))
                    outFile.parentFile?.mkdirs()
                    jarFile.getInputStream(entry).use { inp -> outFile.outputStream().use { it.write(inp.readBytes()) } }
                }

            // Extract unknown files (assets, etc.)
            jarFile.entries().asSequence()
                .filter { entry ->
                    !entry.name.startsWith("res/") && !entry.name.startsWith("META-INF/") &&
                    !entry.name.startsWith("classes") && !entry.name.endsWith(".dex") &&
                    entry.name != "AndroidManifest.xml" && !entry.name.startsWith("lib/") &&
                    !entry.name.startsWith("kotlin/") && !entry.isDirectory
                }.forEach { entry ->
                    val outFile = File(unknownDir, entry.name)
                    outFile.parentFile?.mkdirs()
                    jarFile.getInputStream(entry).use { inp -> outFile.outputStream().use { it.write(inp.readBytes()) } }
                }

            // Extract kotlin metadata
            jarFile.entries().asSequence()
                .filter { it.name.startsWith("kotlin/") && !it.isDirectory }
                .forEach { entry ->
                    val outFile = File(unknownDir, entry.name)
                    outFile.parentFile?.mkdirs()
                    jarFile.getInputStream(entry).use { inp -> outFile.outputStream().use { it.write(inp.readBytes()) } }
                }

            jarFile.close()
            console.success("Decompiled to: $outputDir")
            return true
        } catch (e: Exception) {
            console.error("Decompile error: ${e.message}")
            return false
        }
    }

    fun rebuildApk(inputDir: File, outputApk: File): Boolean {
        val smali = toolManager.getToolPath("smali")
        val dalvikvm = toolManager.getJavaRuntime()
        val zipalign = toolManager.getToolPath("zipalign")

        console.info("Rebuilding APK from: $inputDir")

        try {
            val smaliDir = File(inputDir, "smali")
            val dexDir = File(inputDir, "dex")
            val unknownDir = File(inputDir, "unknown")
            val libDir = File(inputDir, "lib")

            // Assemble smali back to DEX
            if (smaliDir.exists() && smali != null && dalvikvm != null) {
                smaliDir.listFiles()?.filter { it.isDirectory }?.forEach { smaliSub ->
                    val dexNum = smaliSub.name
                    val outDex = File(dexDir, if (dexNum == "0") "classes.dex" else "classes$dexNum.dex")
                    toolManager.executeCommand(listOf(
                        dalvikvm, "-cp", smali, "org.jf.smali.Main",
                        "a", smaliSub.absolutePath, "-o", outDex.absolutePath
                    ))
                }
                console.info("Smali assembled to DEX")
            }

            // Repack everything into a new APK
            val tempApk = File(inputDir, "unsigned.apk")
            repackDirectory(dexDir, unknownDir, libDir, File(inputDir, "manifest"), tempApk)

            // Align
            val alignedApk = File(inputDir, "aligned.apk")
            if (zipalign != null) {
                toolManager.executeCommand(listOf(
                    zipalign, "-f", "-p", "4", tempApk.absolutePath, alignedApk.absolutePath
                ))
            } else {
                tempApk.copyTo(alignedApk, overwrite = true)
                console.warn("zipalign not available - APK may not be optimally aligned")
            }

            // Sign
            return signApk(alignedApk, outputApk)
        } catch (e: Exception) {
            console.error("Rebuild error: ${e.message}")
            return false
        }
    }

    fun signApk(inputApk: File, outputApk: File? = null): Boolean {
        val apksigner = toolManager.getToolPath("apksigner")
        val target = outputApk ?: inputApk
        val keystoreDir = toolManager.createKeystoreDir()
        val keystoreFile = File(keystoreDir, "patchmaster.jks")

        return try {
            if (!keystoreFile.exists()) {
                console.info("Creating debug keystore...")
                val cmd = listOf(
                    "keytool", "-genkey", "-v",
                    "-keystore", keystoreFile.absolutePath,
                    "-alias", "patchmaster",
                    "-keyalg", "RSA",
                    "-keysize", "2048",
                    "-validity", "36500",
                    "-storepass", "patchmaster",
                    "-keypass", "patchmaster",
                    "-dname", "CN=PatchMaster, OU=APK, O=PatchMaster, L=Unknown, ST=Unknown, C=US"
                )
                toolManager.executeCommand(cmd)
            }

            if (apksigner != null) {
                val result = toolManager.executeCommand(listOf(
                    apksigner, "sign",
                    "--ks", keystoreFile.absolutePath,
                    "--ks-key-alias", "patchmaster",
                    "--ks-pass", "pass:patchmaster",
                    "--key-pass", "pass:patchmaster",
                    "--v1-signing-enabled", "true",
                    "--v2-signing-enabled", "true",
                    "--v3-signing-enabled", "true",
                    "--out", target.absolutePath,
                    inputApk.absolutePath
                ))
                if (result.exitCode == 0) {
                    console.success("APK signed: ${target.name}")
                    true
                } else {
                    console.error("Signing failed: ${result.stdout}")
                    false
                }
            } else {
                // Fallback: use keytool + jarsigner
                console.warn("apksigner not available, using jarsigner fallback")
                toolManager.executeCommand(listOf(
                    "jarsigner", "-verbose",
                    "-sigalg", "SHA1withRSA",
                    "-digestalg", "SHA1",
                    "-keystore", keystoreFile.absolutePath,
                    "-storepass", "patchmaster",
                    "-keypass", "patchmaster",
                    inputApk.absolutePath,
                    "patchmaster"
                ))
                inputApk.copyTo(target, overwrite = true)
                true
            }
        } catch (e: Exception) {
            console.error("Signing error: ${e.message}")
            false
        }
    }

    fun quickMod(apkPath: String, templateId: String): PipelineResult {
        val template = ModTemplateLibrary.findById(templateId)
            ?: return PipelineResult(false, error = "Template not found: $templateId")

        console.info("Applying quick mod: ${template.name}")
        val script = ModScript(
            name = template.name,
            description = template.description,
            actions = template.actions
        )

        val result = applyModScript(apkPath, script)
        return if (result) {
            val outPath = getModdedApkPath(template.name)
            PipelineResult(true, outPath)
        } else {
            PipelineResult(false, error = "Mod '${template.name}' failed")
        }
    }

    fun applyModScript(apkPath: String, script: ModScript): Boolean {
        val startTime = System.currentTimeMillis()
        console.info("Applying mod: ${script.name}")
        console.info("${script.actions.size} action(s)")

        // For manifest-only changes, skip full decompile
        val hasManifestActions = script.actions.any { it is ModAction.ManifestEdit || it is ModAction.PermissionAdd || it is ModAction.PermissionRemove }
        val hasSmaliActions = script.actions.any { it is ModAction.SmaliEdit || it is ModAction.NopMethod }
        val hasResourceActions = script.actions.any { it is ModAction.ResourceEdit }

        val workDir = toolManager.getWorkDir()

        try {
            if (!hasSmaliActions && hasManifestActions && !hasResourceActions) {
                // Quick mode: only modify manifest in APK
                return quickManifestPatch(apkPath, script, workDir)
            }

            // Full mode: decompile, apply, rebuild
            val decompiledDir = File(workDir, "decompiled")
            if (!fullDecompile(apkPath, decompiledDir)) return false

            for (action in script.actions) {
                applyAction(decompiledDir, action)
            }

            val outputDir = toolManager.createOutputDir()
            val outputApk = File(outputDir, "${script.name.replace(" ", "_")}_modded.apk")
            val rebuilt = rebuildApk(decompiledDir, outputApk)
            if (rebuilt) {
                val elapsed = (System.currentTimeMillis() - startTime) / 1000
                console.success("Mod complete in ${elapsed}s: ${outputApk.name}")
            }
            return rebuilt
        } catch (e: Exception) {
            console.error("Mod error: ${e.message}")
            return false
        }
    }

    private fun quickManifestPatch(apkPath: String, script: ModScript, workDir: File): Boolean {
        val apkFile = File(apkPath)
        val jarFile = JarFile(apkFile)
        val tempDir = File(workDir, "manifest_patch").also { it.mkdirs() }

        try {
            // Extract everything
            jarFile.entries().asSequence().forEach { entry ->
                val f = File(tempDir, entry.name)
                if (entry.isDirectory) f.mkdirs()
                else {
                    f.parentFile?.mkdirs()
                    jarFile.getInputStream(entry).use { inp -> f.outputStream().use { it.write(inp.readBytes()) } }
                }
            }

            // Modify manifest
            val manifestFile = File(tempDir, "AndroidManifest.xml")
            if (manifestFile.exists()) {
                for (action in script.actions) {
                    when (action) {
                        is ModAction.ManifestEdit -> ManifestEditor.edit(manifestFile, action)
                        is ModAction.PermissionAdd -> ManifestEditor.addPermission(manifestFile, action.permission)
                        is ModAction.PermissionRemove -> ManifestEditor.removePermission(manifestFile, action.permission)
                        is ModAction.ComponentEnable -> ManifestEditor.setComponentEnabled(manifestFile, action.component, true)
                        is ModAction.ComponentDisable -> ManifestEditor.setComponentEnabled(manifestFile, action.component, false)
                        else -> {}
                    }
                }
            }

            // Repack
            val outputApk = File(workDir, "patched.apk")
            repackDirectory(tempDir, outputApk)
            return signApk(outputApk)
        } catch (e: Exception) {
            console.error("Quick patch error: ${e.message}")
            return false
        } finally {
            jarFile.close()
        }
    }

    private fun applyAction(decompiledDir: File, action: ModAction) {
        when (action) {
            is ModAction.ManifestEdit -> {
                val mf = File(decompiledDir, "AndroidManifest.xml")
                if (!mf.exists()) mf = File(decompiledDir, "manifest/AndroidManifest.xml")
                if (mf.exists()) ManifestEditor.edit(mf, action)
            }
            is ModAction.SmaliEdit -> SmaliEditor.editInSmali(decompiledDir, action)
            is ModAction.NopMethod -> SmaliEditor.nopMethod(decompiledDir, action.className, action.methodName)
            is ModAction.PermissionAdd -> {
                val mf = File(decompiledDir, "AndroidManifest.xml")
                ManifestEditor.addPermission(mf, action.permission)
            }
            is ModAction.PermissionRemove -> {
                val mf = File(decompiledDir, "AndroidManifest.xml")
                ManifestEditor.removePermission(mf, action.permission)
            }
            is ModAction.ComponentEnable -> {
                val mf = File(decompiledDir, "AndroidManifest.xml")
                ManifestEditor.setComponentEnabled(mf, action.component, true)
            }
            is ModAction.ComponentDisable -> {
                val mf = File(decompiledDir, "AndroidManifest.xml")
                ManifestEditor.setComponentEnabled(mf, action.component, false)
            }
            is ModAction.ResourceEdit -> ResourcePacker.editResource(decompiledDir, action)
            is ModAction.FileReplace -> File(action.from).copyTo(File(action.to), overwrite = true)
            is ModAction.DexAdd -> {
                val dexDir = File(decompiledDir, "dex").also { it.mkdirs() }
                File(action.dexPath).copyTo(File(dexDir, File(action.dexPath).name), overwrite = true)
            }
            is ModAction.NativeLibAdd -> {
                val libDir = File(decompiledDir, "lib").also { it.mkdirs() }
                File(action.libPath).copyTo(File(libDir, File(action.libPath).name), overwrite = true)
            }
            is ModAction.ShellExec -> {
                val parts = action.command.split("\\s+".toRegex())
                toolManager.executeCommand(parts, decompiledDir)
            }
        }
    }

    fun directDexPatch(dexDir: File, analysis: PatternMatcher.AnalysisResult): Int {
        var totalPatched = 0
        val dexFiles = dexDir.listFiles()?.filter { it.name.endsWith(".dex") } ?: return 0

        for (dexFile in dexFiles) {
            try {
                val patcher = DexPatcher()
                if (!patcher.load(dexFile)) continue

                // Auto-patch license check methods
                val licensePatches = patcher.patchAllLicenseChecks()
                totalPatched += licensePatches

                if (licensePatches > 0) {
                    patcher.save(dexFile)
                    console.info("Patched $licensePatches method(s) in ${dexFile.name}")
                }
            } catch (e: Exception) {
                console.warn("DEX patch error in ${dexFile.name}: ${e.message}")
            }
        }
        return totalPatched
    }

    private fun extractDexFiles(apkPath: String, outputDir: File): List<File> {
        val files = mutableListOf<File>()
        try {
            val jarFile = JarFile(File(apkPath))
            jarFile.entries().asSequence()
                .filter { it.name.startsWith("classes") && it.name.endsWith(".dex") }
                .forEach { entry ->
                    val f = File(outputDir, entry.name)
                    jarFile.getInputStream(entry).use { inp -> f.outputStream().use { it.write(inp.readBytes()) } }
                    files.add(f)
                }
            jarFile.close()
        } catch (e: Exception) { console.error("DEX extraction error: ${e.message}") }
        return files
    }

    private fun repackApk(originalApk: String, dexDir: File, outputApk: File): Boolean {
        return try {
            val jarFile = JarFile(File(originalApk))
            val originalFiles = mutableMapOf<String, ByteArray>()
            jarFile.entries().asSequence()
                .filter { !it.isDirectory }
                .forEach { entry ->
                    if (!entry.name.startsWith("classes") || !entry.name.endsWith(".dex")) {
                        originalFiles[entry.name] = jarFile.getInputStream(entry).readBytes()
                    }
                }
            jarFile.close()

            ZipOutputStream(FileOutputStream(outputApk)).use { zos ->
                // Write original files except DEX
                for ((name, data) in originalFiles) {
                    zos.putNextEntry(ZipEntry(name))
                    zos.write(data)
                    zos.closeEntry()
                }
                // Write patched DEX files
                dexDir.listFiles()?.filter { it.name.endsWith(".dex") }?.forEach { dexFile ->
                    zos.putNextEntry(ZipEntry(dexFile.name))
                    zos.write(dexFile.readBytes())
                    zos.closeEntry()
                }
            }
            true
        } catch (e: Exception) {
            console.error("Repack error: ${e.message}")
            false
        }
    }

    private fun repackDirectory(vararg dirs: File, outputFile: File) {
        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
            for (dir in dirs) {
                if (!dir.exists()) continue
                dir.walkTopDown().filter { it.isFile }.forEach { f ->
                    val relPath = f.relativeTo(dir.parentFile ?: dir).path
                    zos.putNextEntry(ZipEntry(relPath))
                    zos.write(f.readBytes())
                    zos.closeEntry()
                }
            }
        }
    }

    private fun repackDirectory(dir: File, outputFile: File) {
        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
            dir.walkTopDown().filter { it.isFile }.forEach { f ->
                val relPath = f.relativeTo(dir).path
                zos.putNextEntry(ZipEntry(relPath))
                zos.write(f.readBytes())
                zos.closeEntry()
            }
        }
    }

    fun installApk(apkPath: String): Boolean {
        console.info("Installing: $apkPath")
        val result = toolManager.executeCommand(listOf("pm", "install", "-r", apkPath))
        if (result.exitCode == 0) {
            console.success("APK installed successfully")
            return true
        } else {
            console.error("Install failed: ${result.stdout.take(500)}")
            return false
        }
    }

    fun getModdedApkPath(scriptName: String): String {
        return File(toolManager.createOutputDir(), "${scriptName.replace(" ", "_")}_modded.apk").absolutePath
    }

    private fun getSignatureDigest(jarFile: JarFile): String {
        return try {
            val sigEntry = jarFile.entries().asSequence()
                .find { it.name.startsWith("META-INF/") && it.name.endsWith(".RSA") }
            if (sigEntry != null) {
                val digest = MessageDigest.getInstance("SHA-256").digest(
                    jarFile.getInputStream(sigEntry).readBytes()
                )
                Base64.encodeToString(digest, Base64.NO_WRAP)
            } else ""
        } catch (e: Exception) { "" }
    }

    private fun listNativeLibs(jarFile: JarFile): List<String> {
        return jarFile.entries().asSequence()
            .filter { it.name.startsWith("lib/") && !it.isDirectory }
            .map { it.name.substringAfterLast("/") }
            .distinct().toList()
    }

    private fun countDexFiles(jarFile: JarFile): Int {
        return jarFile.entries().asSequence()
            .count { it.name.startsWith("classes") && it.name.endsWith(".dex") }
    }
}
