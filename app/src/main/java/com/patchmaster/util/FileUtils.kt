package com.patchmaster.util

import java.io.*
import java.util.jar.JarFile
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object FileUtils {
    fun extractDexFiles(apkPath: String, outputDir: File): List<File> {
        val files = mutableListOf<File>()
        try {
            val jar = JarFile(File(apkPath))
            jar.entries().asSequence()
                .filter { it.name.startsWith("classes") && it.name.endsWith(".dex") }
                .forEach { entry ->
                    val f = File(outputDir, entry.name)
                    jar.getInputStream(entry).use { inp -> f.outputStream().use { it.write(inp.readBytes()) } }
                    files.add(f)
                }
            jar.close()
        } catch (e: Exception) { e.printStackTrace() }
        return files
    }

    fun extractFileFromApk(apkPath: String, entryName: String): ByteArray? {
        return try {
            val jar = JarFile(File(apkPath))
            val entry = jar.getEntry(entryName) ?: return null
            val bytes = jar.getInputStream(entry).readBytes()
            jar.close()
            bytes
        } catch (e: Exception) { null }
    }

    fun listApkEntries(apkPath: String): List<String> {
        return try {
            val jar = JarFile(File(apkPath))
            val entries = jar.entries().asSequence().filter { !it.isDirectory }.map { it.name }.toList()
            jar.close()
            entries
        } catch (e: Exception) { emptyList() }
    }

    fun copyStream(`in`: InputStream, out: OutputStream, bufferSize: Int = 8192) {
        val buffer = ByteArray(bufferSize)
        var read: Int
        while (`in`.read(buffer).also { read = it } >= 0) {
            out.write(buffer, 0, read)
        }
    }

    fun deleteRecursive(file: File) {
        if (file.isDirectory) file.listFiles()?.forEach { deleteRecursive(it) }
        file.delete()
    }

    fun humanReadableSize(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB")
        var size = bytes.toDouble()
        var unitIdx = 0
        while (size >= 1024 && unitIdx < units.size - 1) {
            size /= 1024
            unitIdx++
        }
        return "%.1f %s".format(size, units[unitIdx])
    }
}

object ZipUtils {
    fun zipDirectory(sourceDir: File, outputZip: File) {
        ZipOutputStream(FileOutputStream(outputZip)).use { zos ->
            sourceDir.walkTopDown().filter { it.isFile }.forEach { file ->
                val entryName = file.relativeTo(sourceDir).path
                zos.putNextEntry(ZipEntry(entryName))
                zos.write(file.readBytes())
                zos.closeEntry()
            }
        }
    }

    fun unzip(zipFile: File, outputDir: File) {
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val file = File(outputDir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    file.outputStream().use { zis.copyTo(it) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
