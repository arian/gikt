package com.github.arian.gikt.index

import com.github.arian.gikt.FileStat
import com.github.arian.gikt.Lockfile
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.relativeTo
import java.nio.ByteBuffer
import java.nio.file.Path
import java.security.MessageDigest
import java.util.*
import kotlin.math.min

private const val MAX_PATH_SIZE = 0xFFF
private const val REGULAR_MODE = 33188 // 0100644
private const val EXECUTABLE_MODE = 33261 // 0100744
private const val ENTRY_BLOCK = 8

private fun Int.to32Bit(): ByteArray  =
    ByteBuffer.allocate(4).also { it.putInt(this) }.array()

private fun Long.to32Bit(): ByteArray =
    ByteBuffer.allocate(4).also { it.putInt(toInt()) }.array()

private fun Int.to16Bit(): ByteArray =
    ByteBuffer.allocate(2).also { it.putShort(toShort()) }.array()

class Entry(
    path: Path,
    private val oid: ObjectId,
    private val stat: FileStat
) {

    private val mode = when (stat.executable) {
        true -> EXECUTABLE_MODE
        false -> REGULAR_MODE
    }

    private val pathBytes = path.toString().toByteArray()
    private val flags = min(pathBytes.size, MAX_PATH_SIZE)

    val key: String = path.toString()

    val content: ByteArray by lazy {

        val bytes = arrayOf(
            stat.ctime.to32Bit(),
            stat.ctimeNS.to32Bit(),
            stat.mtime.to32Bit(),
            stat.mtimeNS.to32Bit(),
            stat.dev.to32Bit(),
            stat.ino.to32Bit(),
            mode.to32Bit(),
            stat.uid.to32Bit(),
            stat.gid.to32Bit(),
            stat.size.to32Bit(),
            oid.bytes,
            flags.to16Bit(),
            pathBytes
        ).reduce(ByteArray::plus)

        val padding = ByteArray(bytes.size % ENTRY_BLOCK)

        bytes + padding
    }

}

class Index(private val workspacePath: Path, pathname: Path) {

    private var digest: MessageDigest? = null
    private var entries: Map<String, Entry> = emptyMap()
    private val keys: SortedSet<String> = sortedSetOf()
    private val lockfile = Lockfile(pathname)

    fun add(path: Path, oid: ObjectId, stat: FileStat) {
        val p = path.relativeTo(workspacePath)
        val entry = Entry(p, oid, stat)
        entries = entries + (entry.key to entry)
        keys.add(entry.key)
    }

    private fun forEach(fn: (Entry) -> Unit) =
        keys.forEach { fn(requireNotNull(entries[it])) }

    fun writeUpdates(): Boolean {
        if (!lockfile.holdForUpdate()) {
            return false
        }

        beginWrite()

        val header = "DIRC".toByteArray() + 2.to32Bit() + entries.size.to32Bit()
        write(header)

        forEach { write(it.content) }

        finishWrite()

        return true
    }

    private fun beginWrite() {
        digest = MessageDigest.getInstance("SHA-1")
    }

    private fun write(data: ByteArray) {
        lockfile.write(data)
        requireNotNull(digest).update(data)
    }

    private fun finishWrite() {
        lockfile.write(requireNotNull(digest).digest())
        lockfile.commit()
    }
}
