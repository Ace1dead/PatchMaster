package com.patchmaster.model

sealed class ModAction {
    data class ManifestEdit(
        val path: String,
        val value: String,
        val action: ActionType = ActionType.SET
    ) : ModAction()

    data class SmaliEdit(
        val file: String,
        val find: String,
        val replace: String,
        val method: String? = null
    ) : ModAction()

    data class FileReplace(
        val from: String,
        val to: String
    ) : ModAction()

    data class ResourceEdit(
        val resource: String,
        val value: String,
        val type: String = "string"
    ) : ModAction()

    data class PermissionAdd(val permission: String) : ModAction()
    data class PermissionRemove(val permission: String) : ModAction()
    data class ComponentEnable(val component: String) : ModAction()
    data class ComponentDisable(val component: String) : ModAction()
    data class DexAdd(val dexPath: String) : ModAction()
    data class NativeLibAdd(val libPath: String) : ModAction()
    data class ShellExec(val command: String) : ModAction()
    data class NopMethod(val className: String, val methodName: String) : ModAction()
}

enum class ActionType { SET, ADD, REMOVE, APPEND, PREPEND }

data class ModScript(
    val name: String,
    val description: String,
    val targetPackage: String? = null,
    val actions: List<ModAction> = emptyList(),
    val metadata: Map<String, String> = emptyMap()
)
