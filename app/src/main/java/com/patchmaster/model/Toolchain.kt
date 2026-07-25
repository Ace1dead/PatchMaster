package com.patchmaster.model

data class ToolInfo(
    val name: String,
    val version: String = "",
    val path: String = "",
    val isAvailable: Boolean = false,
    val type: ToolType = ToolType.NATIVE
)

enum class ToolType {
    NATIVE,    // ARM64 Linux binary
    JAVA_JAR,  // Runs via dalvikvm
    SCRIPT     // Shell script
}
