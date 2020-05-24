package com.github.arian.gikt.database

import com.github.arian.gikt.relativeTo
import java.nio.file.FileSystem
import java.nio.file.Path

private typealias TreeDiffMapValue = Pair<TreeEntry?, TreeEntry?>
typealias TreeDiffMap = Map<Path, TreeDiffMapValue>

class TreeDiff(private val fs: FileSystem, private val database: Database) {

    private class TreeGetter(val tree: Tree?) {
        operator fun get(key: Path): TreeEntry? = tree?.get(key.relativeTo(tree.name))
        fun list(): List<TreeEntry> = tree?.list() ?: emptyList()
        fun contains(name: Path) = get(name) != null
    }

    fun compareOids(a: ObjectId?, b: ObjectId?, prefix: Path? = null): TreeDiffMap {
        if (a == b) {
            return emptyMap()
        }

        val treeA = TreeGetter(a?.let { loadTree(it, prefix) })
        val treeB = TreeGetter(b?.let { loadTree(it, prefix) })
        return compareTrees(treeA, treeB)
    }

    private fun loadTree(oid: ObjectId, prefix: Path?): Tree? {
        return when (val obj = database.load(oid, prefix ?: fs.getPath(""))) {
            is Commit -> loadTree(obj.tree, prefix)
            is Tree -> obj
            else -> null
        }
    }

    private fun compareTrees(a: TreeGetter, b: TreeGetter): TreeDiffMap {
        return detectDeletions(a, b) + detectAdditions(a, b)
    }

    private fun treeDiffs(entry: TreeEntry, other: TreeEntry?): TreeDiffMap {
        val a = entry.treeOid
        val b = other.treeOid
        return if (a != null || b != null) {
            compareOids(a, b, entry.name)
        } else {
            emptyMap()
        }
    }

    private fun blobDiffs(entry: TreeEntry, other: TreeEntry?): TreeDiffMap {
        val a = entry.blobOid
        val b = other.blobOid
        return if (a != null || b != null) {
            mapOf(entry.name to (entry to other))
        } else {
            emptyMap()
        }
    }

    private fun detectDeletions(a: TreeGetter, b: TreeGetter): TreeDiffMap {
        return a.list()
            .map { entry ->
                when (val other = b[entry.name]) {
                    entry -> emptyMap()
                    else -> treeDiffs(entry, other) + blobDiffs(entry, other)
                }
            }
            .combineMaps()
    }

    private fun detectAdditions(a: TreeGetter, b: TreeGetter): TreeDiffMap {
        return b.list()
            .map { entry ->
                when {
                    a.contains(entry.name) -> emptyMap()
                    entry.isTree() -> compareOids(null, entry.oid, entry.name)
                    else -> mapOf(entry.name to Pair(null, entry))
                }
            }
            .combineMaps()
    }

    private val TreeEntry?.treeOid: ObjectId?
        get() = this?.takeIf { it.isTree() }?.oid

    private val TreeEntry?.blobOid: ObjectId?
        get() = this?.takeUnless { it.isTree() }?.oid

    private fun <K, V> List<Map<K, V>>.combineMaps(): Map<K, V> =
        fold(emptyMap()) { acc, map -> acc + map }
}
