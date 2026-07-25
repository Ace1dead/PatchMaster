package com.patchmaster.engine

import com.patchmaster.model.ActionType
import com.patchmaster.model.ModAction
import java.io.File

object ManifestEditor {
    private const val NS_ANDROID = "http://schemas.android.com/apk/res/android"
    private const val NS_TOOLS = "http://schemas.android.com/tools"

    fun edit(manifestFile: File, action: ModAction.ManifestEdit) {
        if (!manifestFile.exists()) return
        var content = manifestFile.readText()

        when (action.action) {
            ActionType.SET -> {
                val regex = Regex("${action.path}=\"[^\"]*\"")
                if (regex.containsMatchIn(content)) {
                    content = content.replace(regex, "${action.path}=\"${action.value}\"")
                } else {
                    content = injectAttribute(content, action.path, action.value)
                }
            }
            ActionType.ADD -> {
                content = injectAttribute(content, action.path, action.value)
            }
            ActionType.REMOVE -> {
                content = content.replace(Regex("\\s+${action.path}=\"[^\"]*\""), "")
            }
            ActionType.APPEND -> {
                val regex = Regex("${action.path}=\"([^\"]*)\"")
                content = content.replace(regex) { match ->
                    val existing = match.groupValues[1]
                    "${action.path}=\"$existing${action.value}\""
                }
            }
            ActionType.PREPEND -> {
                val regex = Regex("${action.path}=\"([^\"]*)\"")
                content = content.replace(regex) { match ->
                    val existing = match.groupValues[1]
                    "${action.path}=\"${action.value}$existing\""
                }
            }
        }

        manifestFile.writeText(content)
    }

    fun addPermission(manifestFile: File, permission: String) {
        if (!manifestFile.exists()) return
        var content = manifestFile.readText()
        if ("uses-permission" in content && permission in content) return

        val line = "    <uses-permission android:name=\"$permission\" />"
        content = content.replace("</manifest>", "$line\n</manifest>")
        manifestFile.writeText(content)
    }

    fun removePermission(manifestFile: File, permission: String) {
        if (!manifestFile.exists()) return
        var content = manifestFile.readText()
        content = content.replace(
            Regex("<uses-permission[^>]*android:name=\"$permission\"[^>]*/>"),
            ""
        )
        manifestFile.writeText(content)
    }

    fun setComponentEnabled(manifestFile: File, componentName: String, enabled: Boolean) {
        if (!manifestFile.exists()) return
        var content = manifestFile.readText()
        val regex = Regex("<(activity|service|receiver|provider)[^>]*android:name=\"$componentName\"[^>]*>")
        content = content.replace(regex) { match ->
            if (enabled) {
                match.value.replace(Regex("android:enabled=\"false\""), "android:enabled=\"true\"")
            } else {
                if ("android:enabled" in match.value) {
                    match.value.replace(Regex("android:enabled=\"true\""), "android:enabled=\"false\"")
                } else {
                    match.value.replace("<", "<android:enabled=\"false\" ")
                }
            }
        }
        manifestFile.writeText(content)
    }

    fun addDebuggable(manifestFile: File) {
        edit(manifestFile, ModAction.ManifestEdit(
            path = "android:debuggable", value = "true", action = ActionType.ADD
        ))
    }

    fun allowBackup(manifestFile: File) {
        edit(manifestFile, ModAction.ManifestEdit(
            path = "android:allowBackup", value = "true", action = ActionType.ADD
        ))
    }

    fun addNetworkSecurityConfig(manifestFile: File, configFile: File?) {
        edit(manifestFile, ModAction.ManifestEdit(
            path = "android:networkSecurityConfig",
            value = "@xml/network_security_config",
            action = ActionType.ADD
        ))
    }

    fun addExtractNativeLibs(manifestFile: File) {
        edit(manifestFile, ModAction.ManifestEdit(
            path = "android:extractNativeLibs", value = "true", action = ActionType.ADD
        ))
    }

    private fun injectAttribute(xml: String, attr: String, value: String): String {
        val manifestTag = Regex("<manifest[^>]*>").find(xml)?.value
            ?: Regex("<application[^>]*>").find(xml)?.value
            ?: return xml

        return if (manifestTag in xml) {
            xml.replace(manifestTag, manifestTag.replace(">", " $attr=\"$value\">"))
        } else xml
    }
}
