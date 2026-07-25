package com.patchmaster.engine

import com.patchmaster.model.ModAction
import java.io.File

object SmaliEditor {
    private var smaliDir: File? = null

    fun editInSmali(decompiledDir: File, action: ModAction.SmaliEdit) {
        smaliDir = File(decompiledDir, "smali")
        if (!smaliDir!!.exists()) {
            smaliDir = File(decompiledDir, "smali_classes2")
            if (!smaliDir!!.exists()) return
        }

        val smaliFile = findSmaliFile(action.file)
            ?: findSmaliFile(action.file.replace('.', '/'))
            ?: return

        var content = smaliFile.readText()

        if (action.method != null) {
            val methodBlock = extractMethodBlock(content, action.method)
            if (methodBlock != null) {
                content = content.replace(methodBlock, methodBlock.replace(action.find, action.replace))
            }
        } else {
            content = content.replace(action.find, action.replace)
        }

        smaliFile.writeText(content)
    }

    fun nopMethod(decompiledDir: File, className: String, methodName: String) {
        smaliDir = File(decompiledDir, "smali")
        if (!smaliDir!!.exists()) {
            smaliDir = File(decompiledDir, "smali_classes2")
            if (!smaliDir!!.exists()) return
        }

        val smaliFile = findSmaliFile(className.replace('.', '/'))
            ?: return

        var content = smaliFile.readText()
        val methodBlock = extractMethodBlock(content, methodName) ?: return

        val nopBody = """
    .registers 1
    .prologue
    return-void
    .end method
""".trimStart()

        val methodHeader = methodBlock.substringBefore(".registers")
        val newMethod = methodHeader + nopBody
        content = content.replace(methodBlock, newMethod)
        smaliFile.writeText(content)
    }

    fun addField(decompiledDir: File, className: String, fieldDef: String) {
        smaliDir = File(decompiledDir, "smali")
        val smaliFile = findSmaliFile(className.replace('.', '/')) ?: return
        var content = smaliFile.readText()
        content = content.replace("# fields", "# fields\n$fieldDef")
        smaliFile.writeText(content)
    }

    fun addMethod(decompiledDir: File, className: String, methodDef: String) {
        smaliDir = File(decompiledDir, "smali")
        val smaliFile = findSmaliFile(className.replace('.', '/')) ?: return
        var content = smaliFile.readText()
        content = content.replace("# virtual methods", "# virtual methods\n$methodDef")
        smaliFile.writeText(content)
    }

    fun injectLogHook(
        decompiledDir: File,
        targetClass: String,
        methodName: String,
        tag: String = "PatchMaster",
        message: String = "Hooked"
    ) {
        smaliDir = File(decompiledDir, "smali")
        val smaliFile = findSmaliFile(targetClass.replace('.', '/')) ?: return
        var content = smaliFile.readText()
        val methodBlock = extractMethodBlock(content, methodName) ?: return
        val firstLine = methodBlock.lineSequence().firstOrNull {
            it.trimStart().startsWith("invoke-") || it.trimStart().startsWith("sget-")
        } ?: return

        val logLine = """
    const-string v0, "$tag"
    const-string v1, "$message"
    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
    move-result v0
""".trimIndent()

        content = content.replace(firstLine, "$logLine\n$firstLine")
        smaliFile.writeText(content)
    }

    fun findSmaliFile(className: String): File? {
        val dirs = smaliDir?.parentFile?.listFiles()
            ?.filter { it.name.startsWith("smali") }
            ?: emptyList()

        for (dir in dirs) {
            val path = File(dir, "$className.smali")
            if (path.exists()) return path
        }
        return null
    }

    private fun extractMethodBlock(content: String, methodName: String): String? {
        val regex = Regex("\\.method[^)]*$methodName[^)]*\\([^)]*\\)[^}]*\\.end method")
        return regex.find(content)?.value
    }

    fun setReturnValue(content: String, methodName: String, value: String): String {
        val methodBlock = extractMethodBlock(content, methodName) ?: return content
        val newBlock = methodBlock.replace(Regex("const/4\\s+v0,\\s*0x[0-9a-f]+"), "const/4 v0, $value")
            .replace(Regex("const/16\\s+v0,\\s*0x[0-9a-f]+"), "const/16 v0, $value")
            .replace(Regex("const\\s+v0,\\s*-?\\d+"), "const v0, $value")
        return content.replace(methodBlock, newBlock)
    }
}
