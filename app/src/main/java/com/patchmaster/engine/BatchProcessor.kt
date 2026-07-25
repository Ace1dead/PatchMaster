package com.patchmaster.engine

import com.patchmaster.PatchMasterApp
import com.patchmaster.model.ModAction
import com.patchmaster.model.ModScript
import com.patchmaster.model.ModTemplate
import com.patchmaster.model.ModTemplateLibrary
import com.patchmaster.ui.screens.ConsoleLog
import java.io.File

class BatchProcessor {
    private val console = ConsoleLog.instance
    private val engine get() = ApkEngine(PatchMasterApp.instance)

    data class BatchJob(
        val id: String = java.util.UUID.randomUUID().toString().take(8),
        val name: String,
        val apkPaths: List<String>,
        val templateId: String? = null,
        val customScript: ModScript? = null,
        val outputDir: String? = null
    )

    data class BatchResult(
        val jobId: String,
        val successCount: Int,
        val failCount: Int,
        val results: List<SingleResult>
    )

    data class SingleResult(
        val apkPath: String,
        val success: Boolean,
        val outputPath: String? = null,
        val error: String? = null
    )

    fun process(job: BatchJob): BatchResult {
        val results = mutableListOf<SingleResult>()
        console.info("Batch: ${job.name} (${job.apkPaths.size} APK(s))")

        for ((i, apkPath) in job.apkPaths.withIndex()) {
            console.info("[${i + 1}/${job.apkPaths.size}] Processing: ${File(apkPath).name}")
            val result = try {
                if (job.templateId != null) {
                    val r = engine.quickMod(apkPath, job.templateId)
                    SingleResult(apkPath, r.success, r.outputPath, r.error)
                } else if (job.customScript != null) {
                    val r = engine.applyModScript(apkPath, job.customScript)
                    SingleResult(apkPath, r, if (r) engine.getModdedApkPath(job.customScript.name) else null)
                } else {
                    SingleResult(apkPath, false, error = "No template or script specified")
                }
            } catch (e: Exception) {
                console.error("Batch item failed: ${e.message}")
                SingleResult(apkPath, false, error = e.message)
            }
            results.add(result)
        }

        val success = results.count { it.success }
        val fail = results.count { !it.success }
        console.success("Batch complete: $success succeeded, $fail failed")
        return BatchResult(job.id, success, fail, results)
    }

    fun processAllTemplates(apkPath: String): List<BatchResult> {
        val results = mutableListOf<BatchResult>()
        for (template in ModTemplateLibrary.templates) {
            if (template.requiresFullDecompile) continue
            val job = BatchJob(
                name = "Batch: ${template.name}",
                apkPaths = listOf(apkPath),
                templateId = template.id
            )
            results.add(process(job))
        }
        return results
    }
}
