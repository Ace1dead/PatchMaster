package com.patchmaster.engine

import com.patchmaster.model.ModAction
import java.io.File

object ResourcePacker {
    private val supportedTypes = setOf("string", "color", "dimen", "integer", "bool", "array")

    fun editResource(decompiledDir: File, action: ModAction.ResourceEdit) {
        val typeDir = action.type
        val resDir = File(decompiledDir, "res")
        if (!resDir.exists()) return

        when (typeDir) {
            "string" -> editStringsXml(resDir, action.resource, action.value)
            "color" -> editColorsXml(resDir, action.resource, action.value)
            "bool" -> editBoolsXml(resDir, action.resource, action.value)
            "integer" -> editIntegersXml(resDir, action.resource, action.value)
            "dimen" -> editDimensXml(resDir, action.resource, action.value)
            "array" -> editArraysXml(resDir, action.resource, action.value)
            else -> editGenericResource(resDir, typeDir, action.resource, action.value)
        }
    }

    fun addNetworkSecurityConfig(resDir: File) {
        val xmlDir = File(resDir, "xml")
        xmlDir.mkdirs()
        val configFile = File(xmlDir, "network_security_config.xml")
        if (!configFile.exists()) {
            configFile.writeText("""<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="true">
        <trust-anchors>
            <certificates src="system" />
            <certificates src="user" />
        </trust-anchors>
    </base-config>
    <debug-overrides>
        <trust-anchors>
            <certificates src="user" />
        </trust-anchors>
    </debug-overrides>
</network-security-config>
""")
        }
    }

    fun replaceStringArray(resDir: File, arrayName: String, values: List<String>) {
        val stringsFiles = resDir.walkTopDown().filter { it.name == "strings.xml" }.toList()
        for (file in stringsFiles) {
            var content = file.readText()
            val arrayRegex = Regex("<string-array\\s+name=\"$arrayName\">(.*?)</string-array>", RegexOption.DOT_MATCHES_ALL)
            val newArray = buildString {
                appendLine("<string-array name=\"$arrayName\">")
                values.forEach { appendLine("    <item>$it</item>") }
                append("</string-array>")
            }
            content = content.replace(arrayRegex, newArray)
            file.writeText(content)
        }
    }

    fun addBooleanFlag(resDir: File, name: String, value: Boolean) {
        val boolsFiles = resDir.walkTopDown().filter { it.name == "bools.xml" }.toList()
        if (boolsFiles.isEmpty()) {
            val defaultBools = File(resDir, "values/bools.xml")
            defaultBools.parentFile.mkdirs()
            defaultBools.writeText("""<?xml version="1.0" encoding="utf-8"?>
<resources>
    <bool name="$name">$value</bool>
</resources>
""")
            return
        }
        for (file in boolsFiles) {
            var content = file.readText()
            if (name in content) {
                content = content.replace(Regex("<bool\\s+name=\"$name\">[^<]*</bool>"), "<bool name=\"$name\">$value</bool>")
            } else {
                content = content.replace("</resources>", "    <bool name=\"$name\">$value</bool>\n</resources>")
            }
            file.writeText(content)
        }
    }

    private fun editStringsXml(resDir: File, name: String, value: String) {
        val stringsFiles = resDir.walkTopDown().filter { it.name == "strings.xml" }.toList()
        if (stringsFiles.isEmpty()) {
            val defaultStrings = File(resDir, "values/strings.xml")
            defaultStrings.parentFile.mkdirs()
            defaultStrings.writeText("""<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="$name">$value</string>
</resources>
""")
            return
        }
        for (file in stringsFiles) {
            var content = file.readText()
            if (name in content) {
                content = content.replace(Regex("<string\\s+name=\"$name\">[^<]*</string>"), "<string name=\"$name\">$value</string>")
            } else {
                content = content.replace("</resources>", "    <string name=\"$name\">$value</string>\n</resources>")
            }
            file.writeText(content)
        }
    }

    private fun editColorsXml(resDir: File, name: String, value: String) {
        val files = resDir.walkTopDown().filter { it.name == "colors.xml" }.toList()
        for (file in files) {
            var content = file.readText()
            content = if (name in content) {
                content.replace(Regex("<color\\s+name=\"$name\">[^<]*</color>"), "<color name=\"$name\">$value</color>")
            } else {
                content.replace("</resources>", "    <color name=\"$name\">$value</color>\n</resources>")
            }
            file.writeText(content)
        }
    }

    private fun editBoolsXml(resDir: File, name: String, value: String) {
        editGenericXml(resDir, "bools.xml", "bool", name, value)
    }

    private fun editIntegersXml(resDir: File, name: String, value: String) {
        editGenericXml(resDir, "integers.xml", "integer", name, value)
    }

    private fun editDimensXml(resDir: File, name: String, value: String) {
        editGenericXml(resDir, "dimens.xml", "dimen", name, value)
    }

    private fun editArraysXml(resDir: File, name: String, value: String) {
        val files = resDir.walkTopDown().filter { it.name == "arrays.xml" }.toList()
        for (file in files) {
            var content = file.readText()
            val regex = Regex("<string-array\\s+name=\"$name\">(.*?)</string-array>", RegexOption.DOT_MATCHES_ALL)
            if (regex.containsMatchIn(content)) {
                content = content.replace(regex) { match ->
                    val existingItems = match.groupValues[1]
                    "<string-array name=\"$name\">$existingItems    <item>$value</item>\n</string-array>"
                }
            } else {
                content = content.replace("</resources>", "    <string-array name=\"$name\">\n        <item>$value</item>\n    </string-array>\n</resources>")
            }
            file.writeText(content)
        }
    }

    private fun editGenericResource(resDir: File, typeDir: String, name: String, value: String) {
        val files = resDir.walkTopDown().filter { it.name.endsWith(".xml") && it.parent?.endsWith("/$typeDir") == true }.toList()
        for (file in files) {
            var content = file.readText()
            val tag = typeDir.dropLast(1)
            content = if (name in content) {
                content.replace(Regex("<$tag\\s+name=\"$name\">[^<]*</$tag>"), "<$tag name=\"$name\">$value</$tag>")
            } else {
                content.replace("</resources>", "    <$tag name=\"$name\">$value</$tag>\n</resources>")
            }
            file.writeText(content)
        }
    }

    private fun editGenericXml(resDir: File, fileName: String, tag: String, name: String, value: String) {
        val files = resDir.walkTopDown().filter { it.name == fileName }.toList()
        if (files.isEmpty()) {
            val defaultFile = File(resDir, "values/$fileName")
            defaultFile.parentFile.mkdirs()
            defaultFile.writeText("""<?xml version="1.0" encoding="utf-8"?>
<resources>
    <$tag name="$name">$value</$tag>
</resources>
""")
            return
        }
        for (file in files) {
            var content = file.readText()
            content = if (name in content) {
                content.replace(Regex("<$tag\\s+name=\"$name\">[^<]*</$tag>"), "<$tag name=\"$name\">$value</$tag>")
            } else {
                content.replace("</resources>", "    <$tag name=\"$name\">$value</$tag>\n</resources>")
            }
            file.writeText(content)
        }
    }
}
