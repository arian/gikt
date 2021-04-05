package com.github.arian.gikt.index

import com.github.arian.gikt.FileStat
import com.github.arian.gikt.Lockfile
import com.github.arian.gikt.Mode
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.database.TreeEntry
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
    val key: Key,
    val oid: ObjectId,
    val stat: FileStat
) {

    constructor(path: Path, oid: ObjectId, stat: FileStat) : this(
        key = Key(path.toString(), stage = 0),
        oid = oid,
        stat = stat
    )

    val name: String = key.name
    val stage: Byte = key.stage
    val mode = Mode.fromStat(stat)

    val content: ByteArray by lazy {

        val pathBytes = name.toByteArray()
        val nameLength = pathBytes.size.coerceAtMost(MAX_PATH_SIZE)
        val flags = (nameLength + ((stage.toInt() and 0x3) shl 12)) and 0xFFFF

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

    data class Key(val name: String, val stage: Byte) : Comparable<Key> {
        override fun compareTo(other: Key): Int = keyComparator.compare(this, other)
    }

    companion object {

        private val keyComparator = compareBy<Key> { it.name }.thenBy { it.stage }

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
            val pathBytesLength = flags.toInt() and MAX_PATH_SIZE
            val pathBytes = bytes.copyOfRange(62, 62 + pathBytesLength)
            val stage = flags.toInt() shr 12 and 0x3

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

            return Entry(Key(pathBytes.utf8(), stage.toByte()), oid, stat)
        }

        fun createFromDb(item: TreeEntry, n: Int): Entry =
            Entry(
                key = Key(item.name.toString(), n.toByte()),
                stat = FileStat(
                    executable = item.mode.isExecutable(),
                    directory = item.mode.isTree(),
                ),
                oid = item.oid
            )
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

class Index(pathname: Path) {

    private val impl = IndexImpl(pathname)

    interface Loaded {
        operator fun get(key: String, stage: Byte = 0): Entry?
        fun forEach(fn: (Entry) -> Unit)
        fun toList(): List<Entry>
        fun tracked(key: String): Boolean
        fun tracked(key: Path): Boolean
        fun hasConflicts(): Boolean
    }

    private class LoadedImpl(private val indexImpl: IndexImpl) : Loaded {
        override operator fun get(key: String, stage: Byte): Entry? = indexImpl.get(key, stage)
        override fun forEach(fn: (Entry) -> Unit) = indexImpl.forEach(fn)
        override fun toList(): List<Entry> = indexImpl.toList()
        override fun tracked(key: String): Boolean = indexImpl.tracked(key)
        override fun tracked(key: Path): Boolean = indexImpl.tracked(key)
        override fun hasConflicts(): Boolean = indexImpl.hasConflicts()
    }

    fun load(): Loaded {
        impl.load()
        return LoadedImpl(impl)
    }

    interface Updater : Loaded {
        fun add(path: Path, oid: ObjectId, stat: FileStat)
        fun addConflictSet(path: Path, items: List<TreeEntry?>)
        fun remove(path: Path)
        fun updateEntryStat(key: String, stat: FileStat)
        fun writeUpdates()
        fun rollback()
    }

    private class UpdaterImpl(
        private val indexImpl: IndexImpl,
        private val lock: Lockfile.Ref
    ) : Loaded by LoadedImpl(indexImpl), Updater {
        override fun add(path: Path, oid: ObjectId, stat: FileStat) = indexImpl.add(path, oid, stat)
        override fun addConflictSet(path: Path, items: List<TreeEntry?>) = indexImpl.addConflictSet(path, items)
        override fun remove(path: Path) = indexImpl.remove(path)
        override fun updateEntryStat(key: String, stat: FileStat) = indexImpl.updateEntryStat(key, stat)
        override fun writeUpdates() = indexImpl.writeUpdates(lock)
        override fun rollback() = lock.rollback()
    }

    fun <T> loadForUpdate(action: Updater.() -> T): T {
        return impl.lockfile.holdForUpdate {
            impl.load()
            action(UpdaterImpl(impl, it))
        }
    }

    private class IndexImpl(private val pathname: Path) {

        private var entries: Map<Entry.Key, Entry> = emptyMap()
        private val keys: SortedSet<Entry.Key> = sortedSetOf()
        private var parents: Map<String, Set<String>> = emptyMap()
        val lockfile = Lockfile(pathname)
        private var changed = false

        fun get(key: String, stage: Byte = 0): Entry? =
            entries[Entry.Key(key, stage)]

        fun add(path: Path, oid: ObjectId, stat: FileStat) {
            require(!path.isAbsolute) { "Path should be relative to workspace path" }
            val entry = Entry(Entry.Key(path.toString(), stage = 0), oid, stat)
            discardConflicts(path)
            storeEntry(entry)
            changed = true
        }

        fun addConflictSet(path: Path, items: List<TreeEntry?>) {
            require(!path.isAbsolute) { "Path should be relative to workspace path" }

            removeEntry(Entry.Key(path.toString(), 0))

            items.forEachIndexed { n, item ->
                item ?: return@forEachIndexed
                val entry = Entry.createFromDb(item, n + 1)
                storeEntry(entry)
            }

            changed = true
        }

        private fun entryForPath(path: String, stage: Int = 0): Entry? =
            entries[Entry.Key(path, stage.toByte())]

        private fun discardConflicts(path: Path) {
            path.parentPaths().forEach { removeEntry(it.toString()) }
            parents[path.toString()]?.forEach { removeEntry(it) }
        }

        fun updateEntryStat(key: String, stat: FileStat) {
            entryForPath(key)?.also {
                entries = entries + (it.key to it.copy(stat = stat))
                changed = true
            }
        }

        fun toList(): List<Entry> =
            keys.map { requireNotNull(entries[it]) }.toList()

        fun forEach(fn: (Entry) -> Unit) =
            toList().forEach(fn)

        fun tracked(key: Path) =
            tracked(key.toString())

        fun tracked(key: String) =
            trackedFile(key) || parents.containsKey(key)

        private fun trackedFile(path: String) =
            (0..3).any { stage -> entries.containsKey(Entry.Key(path, stage.toByte())) }

        fun hasConflicts() =
            entries.values.any { it.stage > 0 }

        fun writeUpdates(lock: Lockfile.Ref) {
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

        fun load() {
            clear()
            openIndexFile(pathname)?.use {
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

            Path.of(entry.name).parentPaths().forEach {
                val dir = it.toString()
                val value = parents.getOrDefault(dir, emptySet()) + entry.name
                parents = parents + (dir to value)
            }
        }

        private fun removeEntry(key: String) {
            (0..3).forEach { stage -> removeEntry(Entry.Key(key, stage.toByte())) }
        }

        private fun removeEntry(key: Entry.Key) {
            val entry = entries[key] ?: return

            keys.remove(entry.key)
            entries = entries - entry.key

            Path.of(entry.key.name).parentPaths().forEach {
                val dir = it.toString()
                val value = parents[dir]
                if (value != null) {
                    parents = if (value.isNotEmpty()) {
                        parents + (dir to (value - entry.key.name))
                    } else {
                        parents - dir
                    }
                }
            }
        }

        fun remove(path: Path) {
            val name = path.toString()
            parents[name]?.forEach { child -> removeEntry(child) }
            removeEntry(name)
            changed = true
        }
    }
}
