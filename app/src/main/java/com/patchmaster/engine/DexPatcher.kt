package com.patchmaster.engine

import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DexPatcher {
    private var dexBytes: ByteArray = ByteArray(0)
    private var buffer: ByteBuffer = ByteBuffer.wrap(dexBytes).apply { order(ByteOrder.LITTLE_ENDIAN) }

    // DEX header offsets
    private val HEADER_SIZE = 0x70
    private val STRING_IDS_OFF = 0x38
    private val STRING_IDS_SIZE = 0x3C
    private val TYPE_IDS_OFF = 0x40
    private val TYPE_IDS_SIZE = 0x44
    private val PROTO_IDS_OFF = 0x48
    private val FIELD_IDS_OFF = 0x50
    private val METHOD_IDS_OFF = 0x58
    private val CLASS_DEFS_OFF = 0x60
    private val DATA_OFF = 0x68

    fun load(dexFile: File): Boolean {
        return try {
            dexBytes = dexFile.readBytes()
            buffer = ByteBuffer.wrap(dexBytes)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            validateHeader()
        } catch (e: Exception) { false }
    }

    fun load(bytes: ByteArray): Boolean {
        dexBytes = bytes
        buffer = ByteBuffer.wrap(dexBytes)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        return validateHeader()
    }

    fun save(outputFile: File): Boolean {
        return try {
            outputFile.writeBytes(dexBytes)
            true
        } catch (e: Exception) { false }
    }

    fun getBytes(): ByteArray = dexBytes

    private fun validateHeader(): Boolean {
        if (dexBytes.size < HEADER_SIZE) return false
        val magic = dexBytes.take(8).toByteArray()
        return magic[0] == 0x64.toByte() && magic[1] == 0x65.toByte() && magic[2] == 0x78.toByte() // "dex"
    }

    private fun readUInt(offset: Int): Int {
        if (offset + 4 > dexBytes.size) return 0
        return (dexBytes[offset].toInt() and 0xFF) or
                ((dexBytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((dexBytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((dexBytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun writeUInt(offset: Int, value: Int) {
        if (offset + 4 > dexBytes.size) return
        dexBytes[offset] = (value and 0xFF).toByte()
        dexBytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
        dexBytes[offset + 2] = ((value shr 16) and 0xFF).toByte()
        dexBytes[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun readUShort(offset: Int): Int {
        if (offset + 2 > dexBytes.size) return 0
        return (dexBytes[offset].toInt() and 0xFF) or
                ((dexBytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    fun getStringCount(): Int = readUInt(STRING_IDS_SIZE)

    fun getStringOffset(index: Int): Int {
        val stringIdsOff = readUInt(STRING_IDS_OFF)
        return readUInt(stringIdsOff + index * 4)
    }

    fun getString(index: Int): String? {
        val count = getStringCount()
        if (index < 0 || index >= count) return null
        val strOff = getStringOffset(index)
        // ULEB128 size
        var pos = strOff
        var size = 0
        var shift = 0
        while (pos < dexBytes.size) {
            val b = dexBytes[pos].toInt() and 0xFF
            size = size or ((b and 0x7F) shl shift)
            shift += 7
            pos++
            if (b and 0x80 == 0) break
        }
        return try {
            String(dexBytes, pos, size, Charsets.UTF_16LE)
        } catch (e: Exception) { null }
    }

    fun findStringIndex(target: String): Int {
        val count = getStringCount()
        for (i in 0 until count) {
            val s = getString(i) ?: continue
            if (s == target) return i
        }
        return -1
    }

    fun findStringContaining(substring: String): List<Pair<Int, String>> {
        val count = getStringCount()
        val results = mutableListOf<Pair<Int, String>>()
        val lower = substring.lowercase()
        for (i in 0 until count) {
            val s = getString(i) ?: continue
            if (s.lowercase().contains(lower)) {
                results.add(i to s)
            }
        }
        return results
    }

    fun findMethodRefs(methodName: String): List<Int> {
        val methodIdsOff = readUInt(METHOD_IDS_OFF)
        val methodCount = (readUInt(CLASS_DEFS_OFF) - methodIdsOff) / 8
        val results = mutableListOf<Int>()

        for (i in 0 until methodCount.coerceAtMost(50000)) {
            val off = methodIdsOff + i * 8
            val nameIdx = readUInt(off + 4)
            val name = getString(nameIdx) ?: continue
            if (name == methodName) results.add(i)
        }
        return results
    }

    fun nopMethodBody(classDescriptor: String, methodName: String): Boolean {
        val classIdx = findStringIndex(classDescriptor)
        if (classIdx < 0) return false

        val methodIdsOff = readUInt(METHOD_IDS_OFF)
        val classDefsOff = readUInt(CLASS_DEFS_OFF)
        val methodCount = (classDefsOff - methodIdsOff) / 8

        for (i in 0 until methodCount.coerceAtMost(50000)) {
            val off = methodIdsOff + i * 8
            val classIdxOff = readUShort(off + 2)
            if (classIdxOff != classIdx) continue
            val nameIdx = readUInt(off + 4)
            val name = getString(nameIdx) ?: continue
            if (name != methodName) continue

            // Found the method - now find its code
            val codeOff = findCodeOffset(i)
            if (codeOff < 0) continue

            // NOP the code: replace instructions with nop (0x0000)
            val insnsSize = readUShort(codeOff + 8)
            for (j in 0 until insnsSize) {
                val insOff = codeOff + 16 + j * 2
                if (insOff + 2 <= dexBytes.size) {
                    dexBytes[insOff] = 0
                    dexBytes[insOff + 1] = 0
                }
            }
            return true
        }
        return false
    }

    fun forceBooleanReturn(classDescriptor: String, methodName: String, returnValue: Boolean): Boolean {
        val classIdx = findStringIndex(classDescriptor)
        if (classIdx < 0) return false

        val methodIdsOff = readUInt(METHOD_IDS_OFF)
        val classDefsOff = readUInt(CLASS_DEFS_OFF)
        val methodCount = (classDefsOff - methodIdsOff) / 8

        for (i in 0 until methodCount.coerceAtMost(50000)) {
            val off = methodIdsOff + i * 8
            val classIdxOff = readUShort(off + 2)
            if (classIdxOff != classIdx) continue
            val nameIdx = readUInt(off + 4)
            val name = getString(nameIdx) ?: continue
            if (name != methodName) continue

            val codeOff = findCodeOffset(i)
            if (codeOff < 0) continue

            val insnsSize = readUShort(codeOff + 8)
            val registers = readUShort(codeOff + 4)

            // Replace first 3 instructions:
            // const/4 v0, <value>
            // return v0
            // NOP fill the rest
            val insStart = codeOff + 16

            if (insStart + 6 > dexBytes.size) continue

            // const/4 v0, value (0x12 0xV0 where V0 = (value << 4) | register)
            val v0 = if (returnValue) 0x01 else 0x00
            dexBytes[insStart] = 0x12 // const/4 opcode
            dexBytes[insStart + 1] = (v0 shl 4).toByte() // v0 = value

            // return v0 (0x0F 0x00)
            dexBytes[insStart + 2] = 0x0F.toByte()
            dexBytes[insStart + 3] = 0x00

            // NOP remaining
            for (j in 2 until insnsSize) {
                val off2 = insStart + j * 2
                if (off2 + 2 <= dexBytes.size) {
                    dexBytes[off2] = 0
                    dexBytes[off2 + 1] = 0
                }
            }

            // Update checksum and signature
            updateChecksum()
            return true
        }
        return false
    }

    fun isLicenseCheckMethod(classDescriptor: String, methodName: String): Boolean {
        val keywords = listOf("license", "premium", "pro", "purchas", "subscrib", "verify", "unlock", "paid", "vip")
        val lower = methodName.lowercase()
        return keywords.any { lower.contains(it) }
    }

    fun patchAllLicenseChecks(): Int {
        val methodIdsOff = readUInt(METHOD_IDS_OFF)
        val classDefsOff = readUInt(CLASS_DEFS_OFF)
        val methodCount = (classDefsOff - methodIdsOff) / 8
        var patched = 0

        for (i in 0 until methodCount.coerceAtMost(50000)) {
            val off = methodIdsOff + i * 8
            val nameIdx = readUInt(off + 4)
            val name = getString(nameIdx) ?: continue
            val classRef = getString(readUShort(off + 2)) ?: ""

            if (isLicenseCheckMethod(classRef, name)) {
                if (forceBooleanReturn(classRef, name, true)) {
                    patched++
                }
            }
        }
        return patched
    }

    fun updateChecksum() {
        // DEX checksum is at offset 8 (4 bytes, Adler32)
        // Zero out the checksum and signature first
        for (i in 8 until 20) {
            if (i < dexBytes.size) dexBytes[i] = 0
        }
        // Compute Adler32 on bytes 12..end
        var a = 1
        var b = 0
        for (i in 12 until dexBytes.size) {
            a = (a + (dexBytes[i].toInt() and 0xFF)) % 65521
            b = (b + a) % 65521
        }
        val checksum = (b shl 16) or a
        writeUInt(8, checksum)

        // SHA-1 signature at offset 12 (20 bytes)
        try {
            val md = java.security.MessageDigest.getInstance("SHA-1")
            md.update(dexBytes, 32, dexBytes.size - 32) // hash from offset 32
            val hash = md.digest()
            System.arraycopy(hash, 0, dexBytes, 12, 20)
        } catch (e: Exception) {}
    }

    private fun findCodeOffset(methodIdx: Int): Int {
        // This is a simplified search - real DEX parsing would walk the class_defs
        val classDefsOff = readUInt(CLASS_DEFS_OFF)
        val classDefCount = readUInt(0x64)
        if (classDefCount == 0) return -1

        val dataOff = readUInt(DATA_OFF)
        var cursor = dataOff

        while (cursor < dexBytes.size - 8) {
            val id = readUShort(cursor)
            val size = readUShort(cursor + 2)
            if (id == 0x0002 && size > 0) { // code_item
                // Check if this code belongs to our method
                val codeOff = cursor
                val triesSize = readUShort(codeOff + 6)
                val insnsSize = readUShort(codeOff + 8)
                val insnsOff = codeOff + 16

                // Check surrounding debug info for method reference
                // This is a heuristic - scan for method_idx in nearby debug data
                for (scan in maxOf(0, cursor - 64) until cursor) {
                    if (scan + 2 <= dexBytes.size) {
                        val ref = readUShort(scan)
                        if (ref == methodIdx && readUShort(scan + 2) == 0x1000) {
                            return codeOff
                        }
                    }
                }
                cursor = insnsOff + insnsSize * 2
                // Pad to 4 bytes
                if (cursor % 4 != 0) cursor += 4 - (cursor % 4)
                cursor += triesSize * 8 // Skip try items
            } else {
                cursor += 4
            }
            if (cursor >= dexBytes.size || id == 0) break
        }
        return -1
    }

    companion object {
        const val OP_NOP = 0x00
        const val OP_RETURN_VOID = 0x0E
        const val OP_RETURN = 0x0F
        const val OP_CONST_4 = 0x12
        const val OP_CONST_16 = 0x13
        const val OP_CONST = 0x14
        const val OP_CONST_STRING = 0x1A
        const val OP_INVOKE_STATIC = 0x71
        const val OP_INVOKE_VIRTUAL = 0x6E
        const val OP_INVOKE_DIRECT = 0x70
        const val OP_INVOKE_SUPER = 0x6F
        const val OP_SGET = 0x60
        const val OP_SPUT = 0x61
        const val OP_IGET = 0x52
        const val OP_IPUT = 0x53
        const val OP_IF_EQZ = 0x38
        const val OP_IF_NEZ = 0x39
        const val OP_GOTO = 0x28
        const val OP_MOVE_RESULT = 0x0A
    }
}
