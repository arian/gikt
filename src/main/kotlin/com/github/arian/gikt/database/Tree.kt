package com.github.arian.gikt.database

import com.github.arian.gikt.Entry
import com.github.arian.gikt.Mode
import com.github.arian.gikt.relativeTo
import java.nio.charset.Charset
import java.nio.file.Path

private val nullByteArray = ByteArray(1).also { it[0] = 0 }

interface TreeEntry {
    val name: Path
    val mode: Mode
    val oid: ObjectId
}

class Tree(
    override val name: Path,
    private val entries: MutableMap<String, TreeEntry> = mutableMapOf()
) : GiktObject(), TreeEntry {

    override fun toString(): String {
        return String(data, Charset.defaultCharset())
    }

    override val type = "tree"
    override val mode = Mode.TREE
    override val data: ByteArray by lazy {
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

    fun addEntry(parents: Parents?, entry: TreeEntry) {
        if (parents == null || parents.isEmpty()) {
            entries[entry.name.relativeTo(name).toString()] = entry
        } else {
            val dirname = name.resolve(parents.first())
            val dirString = dirname.relativeTo(name).toString()
            val tree = entries.getOrElse(dirString) { Tree(dirname) }
            if (tree is Tree) {
                tree.addEntry(parents.tail(), entry)
                entries[dirString] = tree
            }
        }
    }

    fun traverse(fn: (Tree) -> Unit) {
        entries.values.mapNotNull { it as? Tree }.forEach { it.traverse(fn) }
        fn(this)
    }

    fun list(): List<String> =
        entries
            .values
            .sortedBy { it.name }
            .map { it.name.relativeTo(name).toString() }

    operator fun get(key: String): TreeEntry? = entries[name.resolve(key).relativeTo(name).toString()]

    fun getTree(key: String): Tree? = entries[name.resolve(key).relativeTo(name).toString()] as? Tree

    companion object {
        fun build(path: Path, paths: List<Entry>): Tree {

            val entries = paths.sortedBy { it.name.toString() }
            val root = Tree(path.relativeTo(path))

            entries
                .forEach { e ->
                    val p = e.name
                    val r =
                        if (p.isAbsolute) p.relativeTo(path)
                        else p
                    val names = (0 until r.nameCount).map { r.getName(it) }
                    val parents = names.dropLast(1)
                    root.addEntry(Parents(parents), e)
                }

            return root
        }
    }

    data class Parents(val ps: List<Path> = emptyList()) {
        fun first(): Path = ps.first()
        fun tail() = Parents(ps.drop(1))
        fun isEmpty() = ps.isEmpty()
    }

}
