package com.github.arian.gikt.database

import com.github.arian.gikt.Mode
import com.github.arian.gikt.relativeTo
import com.github.arian.gikt.utf8
import java.nio.charset.Charset
import java.nio.file.Path

private val nullByteArray = ByteArray(1).also { it[0] = 0 }

interface TreeEntry {
    val name: Path
    val mode: Mode
    val oid: ObjectId
    val key: Path

    fun isTree() = mode == Mode.TREE
}

data class ParsedTreeEntry(
    override val name: Path,
    override val key: Path,
    override val mode: Mode,
    override val oid: ObjectId
) : TreeEntry

class Tree(
    override val name: Path,
    private var entries: Map<String, TreeEntry> = mapOf()
) : GiktObject(), TreeEntry {

    init {
        require(!name.isAbsolute) { "The path of the Tree should be relative to the workspace root" }
    }

    override fun toString(): String {
        return String(data, Charset.defaultCharset())
    }

    override val key: Path by lazy { name.parent?.let { name.relativeTo(it) } ?: name }
    override val type = "tree"
    override val mode = Mode.TREE
    override val data: ByteArray by lazy {
        if (entries.values.isEmpty()) {
            return@lazy ByteArray(0)
        }
        entries
            .values
            .sortedBy { it.name }
            .flatMap { entry ->
                listOf(
                    entry.mode.mode.toByteArray() + ' '.toByte(), // .toByteArrayPaddedRight(size = 7, padding = ' '),
                    entry.name.relativeTo(name).toString().toByteArray(),
                    nullByteArray,
                    entry.oid.bytes
                )
            }
            .reduceRight(ByteArray::plus)
    }

    fun addEntry(parents: Parents? = null, entry: TreeEntry) {
        if (parents == null || parents.isEmpty()) {
            entries = entries + (entry.name.relativeTo(name).toString() to entry)
        } else {
            val dirname = name.resolve(parents.first())
            val dirString = dirname.relativeTo(name).toString()
            val tree = entries.getOrElse(dirString) { Tree(dirname) }
            if (tree is Tree) {
                tree.addEntry(parents.tail(), entry)
                entries = entries + (dirString to tree)
            }
        }
    }

    fun traverse(fn: (Tree) -> Unit) {
        entries.values.mapNotNull { it as? Tree }.forEach { it.traverse(fn) }
        fn(this)
    }

    fun list(): List<TreeEntry> =
        entries.values.toList()

    fun listNames(): List<String> =
        entries
            .values
            .sortedBy { it.name }
            .map { it.key.toString() }

    operator fun get(key: String): TreeEntry? =
        entries[name.resolve(key).relativeTo(name).toString()]

    operator fun get(name: Path): TreeEntry? =
        entries[name.toString()]

    fun getTree(key: String): Tree? =
        entries[name.resolve(key).relativeTo(name).toString()] as? Tree

    companion object {
        fun build(path: Path, paths: List<Entry>): Tree {
            val entries = paths.sortedBy { it.name.toString() }
            return Tree(path.relativeTo(path)).apply {
                entries.forEach { addEntry(Parents(it.parents), it) }
            }
        }

        fun parse(prefix: Path, bytes: ByteArray): Tree {
            val entries = mutableMapOf<String, TreeEntry>()

            var pos = 0
            do {
                val modeBytes = bytes.drop(pos).takeWhile { it != ' '.toByte() }.toByteArray()
                val mode = Mode.parse(modeBytes.utf8()) ?: break
                pos += modeBytes.size + 1

                val nameBytes = bytes.drop(pos).takeWhile { it != 0.toByte() }.toByteArray()
                val nameString = nameBytes.utf8()
                pos += nameBytes.size + 1

                val oid = bytes.sliceArray(pos until pos + 20)
                pos += 20

                val name = prefix.resolve(nameString)
                entries[nameString] = ParsedTreeEntry(
                    name = name,
                    key = name.relativeTo(prefix),
                    mode = mode,
                    oid = ObjectId(oid)
                )
            } while (pos < bytes.size)

            return Tree(prefix, entries.toMap())
        }
    }

    data class Parents(val ps: List<Path> = emptyList()) {
        fun first(): Path = ps.first()
        fun tail() = Parents(ps.drop(1))
        fun isEmpty() = ps.isEmpty()
    }
}
