package com.github.arian.gikt.index

import com.github.arian.gikt.FileStat
import com.github.arian.gikt.Lockfile
import com.github.arian.gikt.Mode
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.inputStream
import com.github.arian.gikt.parentPaths
import com.github.arian.gikt.utf8
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.SortedSet
import kotlin.math.min

private const val MAX_PATH_SIZE = 0xFFF
private const val ENTRY_BLOCK = 8
private const val ENTRY_MIN_SIZE = 64

private const val CHECKSUM_SIZE = 20
private const val VERSION = 2
private const val SIGNATURE = "DIRC"

private fun Int.to32Bit(): ByteArray =
    ByteBuffer.allocate(4).also { it.putInt(this) }.array()

private fun Long.to32Bit(): ByteArray =
    ByteBuffer.allocate(4).also { it.putInt(toInt()) }.array()

private fun Int.to16Bit(): ByteArray =
    ByteBuffer.allocate(2).also { it.putShort(toShort()) }.array()

private fun ByteArray.from32Bit(): Int =
    if (size == 4) {
        get(0).toInt().and(0xFF).shl(24) +
            get(1).toInt().and(0xFF).shl(16) +
            get(2).toInt().and(0xFF).shl(8) +
            get(3).toInt().and(0xFF)
    } else {
        throw IllegalArgumentException("ByteArray should have a size of four to convert to 32 bit int")
    }

private fun ByteArray.from16Bit(): Short =
    if (size == 2) {
        (get(0).toInt().and(0xFF).shl(8) + get(1).toInt().and(0xFF)).toShort()
    } else {
        throw IllegalArgumentException("ByteArray should have a size of one to convert to 16 bit short")
    }

data class Entry(
    val key: String,
    val oid: ObjectId,
    val stat: FileStat
) {

    constructor(path: Path, oid: ObjectId, stat: FileStat) : this(path.toString(), oid, stat)

    val mode = Mode.fromStat(stat)
    private val pathBytes = key.toByteArray()
    private val flags = min(pathBytes.size, MAX_PATH_SIZE)

    val content: ByteArray by lazy {

        val bytes = arrayOf(
            stat.ctime.to32Bit(),
            stat.ctimeNS.to32Bit(),
            stat.mtime.to32Bit(),
            stat.mtimeNS.to32Bit(),
            stat.dev.to32Bit(),
            stat.ino.to32Bit(),
            mode.asInt.to32Bit(),
            stat.uid.to32Bit(),
            stat.gid.to32Bit(),
            stat.size.to32Bit(),
            oid.bytes,
            flags.to16Bit(),
            pathBytes + ByteArray(1) // terminate path bytes with 0 byte.
        ).reduce(ByteArray::plus)

        val size = bytes.size
        val mask = ENTRY_BLOCK - 1
        val padWith = ((size + mask) and mask.inv()) - size
        val padding = ByteArray(padWith)

        bytes + padding
    }

    companion object {
        fun parse(bytes: ByteArray): Entry {
            val ctime = bytes.copyOfRange(0, 4).from32Bit()
            val ctimeNS = bytes.copyOfRange(4, 8).from32Bit()
            val mtime = bytes.copyOfRange(8, 12).from32Bit()
            val mtimeNS = bytes.copyOfRange(12, 16).from32Bit()
            val dev = bytes.copyOfRange(16, 20).from32Bit()
            val ino = bytes.copyOfRange(20, 24).from32Bit()
            val mode = bytes.copyOfRange(24, 28).from32Bit()
            val uid = bytes.copyOfRange(28, 32).from32Bit()
            val gid = bytes.copyOfRange(32, 36).from32Bit()
            val size = bytes.copyOfRange(36, 40).from32Bit()
            val oidBytes = bytes.copyOfRange(40, 60)

            val flags = bytes.copyOfRange(60, 62).from16Bit()
            val pathBytes = bytes.copyOfRange(62, 62 + flags)

            val oid = ObjectId(oidBytes)
            val stat = FileStat(
                ctime = ctime.toLong(),
                ctimeNS = ctimeNS,
                mtime = mtime.toLong(),
                mtimeNS = mtimeNS,
                dev = dev.toLong(),
                ino = ino.toLong(),
                executable = mode == Mode.EXECUTABLE.asInt,
                uid = uid,
                gid = gid,
                size = size.toLong()
            )

            return Entry(pathBytes.utf8(), oid, stat)
        }
    }

    fun statMatch(stat: FileStat): Boolean {
        return mode == Mode.fromStat(stat) &&
            (this.stat.size == 0L || this.stat.size == stat.size)
    }

    fun timesMatch(stat: FileStat): Boolean {
        return this.stat.ctime == stat.ctime && this.stat.ctimeNS == stat.ctimeNS &&
            this.stat.mtime == stat.mtime && this.stat.mtimeNS == stat.mtimeNS
    }
}

class ChecksumWriter(private val writer: (ByteArray) -> Unit) {

    private val digest = MessageDigest.getInstance("SHA-1")

    fun write(data: ByteArray) {
        writer(data)
        digest.update(data)
    }

    fun writeChecksum() {
        writer(digest.digest())
    }
}

class ChecksumReader(file: Path) : Closeable {

    private val digest = MessageDigest.getInstance("SHA-1")
    private val inputStream = file.inputStream(StandardOpenOption.READ)

    fun read(size: Int): ByteArray {
        val data: ByteArray = inputStream.readNBytes(size)

        if (data.size != size) {
            throw EndOfFile("Unexpected end-of-file")
        }

        digest.update(data)
        return data
    }

    fun verifyChecksum() {
        val sum: ByteArray = inputStream.readNBytes(CHECKSUM_SIZE)
        val hash: ByteArray = digest.digest()

        if (!hash.contentEquals(sum)) {
            throw IllegalStateException("Checksum does not match value stored on disk")
        }
    }

    override fun close() {
        inputStream.close()
    }

    class EndOfFile(msg: String) : Exception(msg)
}

class Index(private val pathname: Path) {

    private var entries: Map<String, Entry> = emptyMap()
    private val keys: SortedSet<String> = sortedSetOf()
    private var parents: Map<String, Set<String>> = emptyMap()
    private val lockfile = Lockfile(pathname)
    private var changed = false

    private fun add(path: Path, oid: ObjectId, stat: FileStat) {
        require(!path.isAbsolute) { "Path should be relative to workspace path" }
        val entry = Entry(path, oid, stat)
        discardConflicts(path)
        storeEntry(entry)
        changed = true
    }

    private fun discardConflicts(path: Path) {
        path.parentPaths().forEach { removeEntry(it.toString()) }
        parents[path.toString()]?.forEach { removeEntry(it) }
    }

    private fun updateEntryStat(key: String, stat: FileStat) {
        entries[key]?.also {
            entries = entries + (key to it.copy(stat = stat))
            changed = true
        }
    }

    private fun toList(): List<Entry> =
        keys.map { requireNotNull(entries[it]) }.toList()

    private fun forEach(fn: (Entry) -> Unit) =
        toList().forEach(fn)

    private fun tracked(it: Path) =
        tracked(it.toString())

    private fun tracked(key: String) =
        entries.containsKey(key) || parents.containsKey(key)

    private fun writeUpdates(lock: Lockfile.Ref) {
        if (!changed) {
            lock.rollback()
            return
        }

        val writer = ChecksumWriter { lock.write(it) }

        val header = SIGNATURE.toByteArray() + VERSION.to32Bit() + entries.size.to32Bit()
        writer.write(header)

        forEach { writer.write(it.content) }

        writer.writeChecksum()

        lock.commit()
    }

    class Updater internal constructor(private val index: Index, private val lock: Lockfile.Ref) : Loaded(index) {
        fun add(path: Path, oid: ObjectId, stat: FileStat) = index.add(path, oid, stat)
        fun remove(path: Path) = index.remove(path)
        fun updateEntryStat(key: String, stat: FileStat) = index.updateEntryStat(key, stat)
        fun writeUpdates() = index.writeUpdates(lock)
        fun rollback() = lock.rollback()
    }

    fun <T> loadForUpdate(action: Updater.() -> T): T {
        return lockfile.holdForUpdate {
            load(it.path)
            action(Updater(this, it))
        }
    }

    open class Loaded internal constructor(private val index: Index) {
        operator fun get(key: String): Entry? = index.entries[key]
        fun forEach(fn: (Entry) -> Unit) = index.forEach(fn)
        fun toList() = index.toList()
        fun tracked(it: String): Boolean = index.tracked(it)
        fun tracked(it: Path): Boolean = index.tracked(it)
    }

    fun load(): Loaded {
        load(pathname)
        return Loaded(this)
    }

    private fun load(lock: Path) {
        clear()
        openIndexFile(lock)?.use {
            val count = readHeader(it)
            readEntries(it, count)
            it.verifyChecksum()
        }
    }

    private fun clear() {
        entries = emptyMap()
        keys.clear()
        parents = emptyMap()
        changed = false
    }

    private fun openIndexFile(path: Path): ChecksumReader? {
        return try {
            ChecksumReader(path)
        } catch (e: NoSuchFileException) {
            null
        }
    }

    private fun readHeader(checksum: ChecksumReader): Int {

        val signature = checksum.read(4)
        if (!signature.contentEquals(SIGNATURE.toByteArray())) {
            throw IllegalStateException("Signature expected '$SIGNATURE' but found ${signature.utf8()}")
        }

        val version = checksum.read(4).from32Bit()
        if (version != VERSION) {
            throw IllegalStateException("Version expected '$VERSION' but found $version")
        }

        return checksum.read(4).from32Bit()
    }

    private fun readEntries(reader: ChecksumReader, count: Int) {
        repeat(count) {
            var entry = reader.read(ENTRY_MIN_SIZE)

            while (entry.last() != 0.toByte()) {
                entry += reader.read(ENTRY_BLOCK)
            }

            storeEntry(Entry.parse(entry))
        }
    }

    private fun storeEntry(entry: Entry) {
        keys.add(entry.key)
        entries = entries + (entry.key to entry)

        Path.of(entry.key).parentPaths().forEach {
            val dir = it.toString()
            val value = parents.getOrDefault(dir, emptySet()) + entry.key
            parents = parents + (dir to value)
        }
    }

    private fun removeEntry(key: String) {
        val entry = entries[key] ?: return

        keys.remove(entry.key)
        entries = entries - entry.key

        Path.of(entry.key).parentPaths().forEach {
            val dir = it.toString()
            val value = parents[dir]
            if (value != null) {
                parents = if (value.isNotEmpty()) {
                    parents + (dir to (value - entry.key))
                } else {
                    parents - dir
                }
            }
        }
    }

    private fun remove(path: Path) {
        val name = path.toString()
        parents[name]?.forEach { child -> removeEntry(child) }
        removeEntry(name)
        changed = true
    }
}
