package com.github.arian.gikt.database

import com.github.arian.gikt.PathFilter
import com.github.arian.gikt.relativeTo
import java.nio.file.Path

sealed class TreeDiffMapValue {

    abstract fun toHexPair(): Pair<ObjectId?, ObjectId?>
    abstract val path: Path

    data class Addition(val new: TreeEntry) : TreeDiffMapValue() {
        override fun toHexPair(): Pair<ObjectId?, ObjectId?> = null to new.oid
        override val path: Path = new.name
    }

    data class Change(val old: TreeEntry, val new: TreeEntry) : TreeDiffMapValue() {
        override fun toHexPair(): Pair<ObjectId?, ObjectId?> = old.oid to new.oid
        override val path: Path = new.name
    }

    data class Deletion(val old: TreeEntry) : TreeDiffMapValue() {
        override fun toHexPair(): Pair<ObjectId?, ObjectId?> = old.oid to null
        override val path: Path = old.name
    }
}

typealias TreeDiffMap = Map<Path, TreeDiffMapValue>

class TreeDiff(private val database: Database) {

    private class TreeGetter(val tree: Tree?, val filter: PathFilter) {
        operator fun get(key: Path): TreeEntry? = tree?.get(key.relativeTo(tree.name))
        fun entries(): Sequence<TreeEntry> = filter.eachEntry(tree?.list() ?: emptyList())
        fun contains(name: Path) = get(name) != null
    }

    fun compareOids(
        a: ObjectId?,
        b: ObjectId?,
        filter: PathFilter = PathFilter.any,
        prefix: Path? = null,
    ): TreeDiffMap {
        if (a == b) {
            return emptyMap()
        }

        val treeA = TreeGetter(a?.let { loadTree(it, prefix) }, filter)
        val treeB = TreeGetter(b?.let { loadTree(it, prefix) }, filter)

        return compareTrees(treeA, treeB)
    }

    private fun loadTree(oid: ObjectId, prefix: Path?): Tree? {
        return when (val obj = database.load(oid, prefix)) {
            is Commit -> loadTree(obj.tree, prefix)
            is Tree -> obj
            else -> null
        }
    }

    private fun compareTrees(a: TreeGetter, b: TreeGetter): TreeDiffMap {
        return detectDeletions(a, b) + detectAdditions(a, b)
    }

    private fun treeDiffs(entry: TreeEntry, other: TreeEntry?, filter: PathFilter): TreeDiffMap {
        val a = entry.treeOid
        val b = other.treeOid
        return if (a != null || b != null) {
            compareOids(a, b, filter.join(entry.key), entry.name)
        } else {
            emptyMap()
        }
    }

    private fun blobDiffs(entry: TreeEntry, other: TreeEntry?): TreeDiffMap {
        val a = entry.blobOid
        val b = other.blobOid
        return if (a != null || b != null) {
            if (other == null) {
                mapOf(entry.name to TreeDiffMapValue.Deletion(entry))
            } else {
                mapOf(entry.name to TreeDiffMapValue.Change(entry, other))
            }
        } else {
            emptyMap()
        }
    }

    private fun detectDeletions(a: TreeGetter, b: TreeGetter): TreeDiffMap {
        return a.entries()
            .combineMaps { entry ->
                when (val other = b[entry.name]) {
                    entry -> emptyMap()
                    else -> treeDiffs(entry, other, a.filter) + blobDiffs(entry, other)
                }
            }
    }

    private fun detectAdditions(a: TreeGetter, b: TreeGetter): TreeDiffMap {
        return b.entries()
            .combineMaps { entry ->
                when {
                    a.contains(entry.name) -> emptyMap()
                    entry.isTree() -> compareOids(null, entry.oid, b.filter.join(entry.key), entry.name)
                    else -> mapOf(entry.name to TreeDiffMapValue.Addition(entry))
                }
            }
    }

    private val TreeEntry?.treeOid: ObjectId?
        get() = this?.takeIf { it.isTree() }?.oid

    private val TreeEntry?.blobOid: ObjectId?
        get() = this?.takeUnless { it.isTree() }?.oid

    private fun <E, K, V> Sequence<E>.combineMaps(block: (E) -> Map<K, V>): Map<K, V> =
        fold(emptyMap()) { acc, map -> acc + block(map) }
}
