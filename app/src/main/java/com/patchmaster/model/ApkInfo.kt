package com.patchmaster.model

data class ApkInfo(
    val packageName: String = "",
    val versionName: String = "",
    val versionCode: Int = 0,
    val minSdk: Int = 0,
    val targetSdk: Int = 0,
    val label: String = "",
    val iconPath: String = "",
    val permissions: List<String> = emptyList(),
    val activities: List<String> = emptyList(),
    val services: List<String> = emptyList(),
    val receivers: List<String> = emptyList(),
    val providers: List<String> = emptyList(),
    val signatureDigest: String = "",
    val isDebuggable: Boolean = false,
    val nativeLibs: List<String> = emptyList(),
    val dexCount: Int = 0,
    val fileSize: Long = 0L,
    val filePath: String = "",
    val decompiledPath: String = ""
)
