package com.github.arian.gikt.repository

import com.github.arian.gikt.FileStat
import com.github.arian.gikt.database.Blob
import com.github.arian.gikt.database.Commit
import com.github.arian.gikt.database.Mode
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.database.Tree
import com.github.arian.gikt.database.TreeEntry
import com.github.arian.gikt.index.Entry
import com.github.arian.gikt.index.Index
import java.nio.file.Path
import java.util.SortedMap
import java.util.SortedSet

class Status(private val repository: Repository) {

    data class Changes internal constructor(
        private val workspace: SortedMap<String, ChangeType> = sortedMapOf(),
        private val index: SortedMap<String, ChangeType> = sortedMapOf()
    ) {

        enum class ChangeType(val short: String) {
            MODIFIED("M"),
            ADDED("A"),
            DELETED("D")
        }

        operator fun plus(other: Changes) =
            copy(
                workspace = (workspace + other.workspace).toSortedMap(),
                index = (index + other.index).toSortedMap()
            )

        companion object {
            private fun single(pair: Pair<String, ChangeType>) = mapOf(pair).toSortedMap()

            fun workspaceDeleted(key: String) = Changes(workspace = single(key to ChangeType.DELETED))
            fun workspaceModified(key: String) = Changes(workspace = single(key to ChangeType.MODIFIED))
            fun indexAdded(key: String) = Changes(index = single(key to ChangeType.ADDED))
            fun indexModified(key: String) = Changes(index = single(key to ChangeType.MODIFIED))
            fun indexDeleted(key: String) = Changes(index = single(key to ChangeType.DELETED))
            fun empty() = Changes()
        }

        fun all(): SortedSet<String> = (workspaceChanges() + indexChanges()).toSortedSet()
        fun workspaceChanges() = workspace.keys
        fun indexChanges() = index.keys

        fun indexType(key: String) = index[key]
        fun workspaceType(key: String) = workspace[key]
    }

    data class Scan(
        val stats: Map<String, FileStat> = emptyMap(),
        val changes: Changes = Changes.empty(),
        val untracked: Set<String> = emptySet()
    ) {
        operator fun plus(scan: Scan) =
            copy(
                stats = stats + scan.stats,
                changes = changes + scan.changes,
                untracked = untracked + scan.untracked
            )
    }

    fun scan(index: Index.Loaded): Scan {
        val workspaceScan = scanWorkspace(index)
        val headTree = loadHeadTree()
        return checkIndexEntries(repository, index, workspaceScan, headTree)
    }

    private fun scanWorkspace(index: Index.Loaded, prefix: Path? = null): Scan {
        val ls = prefix
            ?.let { repository.workspace.listDir(it) }
            ?: repository.workspace.listDir()

        return ls
            .map { (it, stat) -> checkPath(index, it, stat) }
            .reduce { acc, scan -> acc + scan }
    }

    private fun checkPath(index: Index.Loaded, path: Path, stat: FileStat): Scan =
        when {
            index.tracked(path) -> {
                when (stat.directory) {
                    true -> scanWorkspace(index, path)
                    false -> Scan(stats = mapOf("$path" to stat))
                }
            }
            trackableFile(index, path, stat) -> {
                val name = when (stat.directory) {
                    true -> "$path/"
                    false -> "$path"
                }
                Scan(untracked = setOf(name))
            }
            else -> Scan()
        }

    private fun trackableFile(index: Index.Loaded, path: Path, stat: FileStat): Boolean {
        if (stat.file) {
            return !index.tracked(path)
        }

        val items = repository.workspace.listDir(path)
        val files = items.filter { (_, stat) -> stat.file }
        val dirs = items.filter { (_, stat) -> stat.directory }

        return (files + dirs).any { (itemPath, itemStat) -> trackableFile(index, itemPath, itemStat) }
    }

    private fun checkIndexEntries(
        repository: Repository,
        index: Index.Loaded,
        scan: Scan,
        headTree: Map<String, TreeEntry>
    ): Scan {
        val workspaceChanges = index.toList()
            .mapNotNull { checkIndexEntryAgainstWorkspace(repository, index, scan, it) }

        val headChanges = index.toList()
            .mapNotNull { checkIndexEntryAgainstHeadTree(headTree, it) }

        val indexDeleted = headTree
            .filterNot { index.tracked(it.key) }
            .map { Changes.indexDeleted(it.key) }

        val changes = workspaceChanges + headChanges + indexDeleted

        if (changes.isEmpty()) {
            return scan
        }

        return scan.copy(changes = changes.reduce { acc, it -> acc + it })
    }

    private fun checkIndexEntryAgainstWorkspace(
        repository: Repository,
        index: Index.Loaded,
        scan: Scan,
        entry: Entry
    ): Changes? {
        val stat = scan.stats[entry.key] ?: return Changes.workspaceDeleted(entry.key)

        if (!entry.statMatch(stat)) {
            return Changes.workspaceModified(entry.key)
        }

        if (entry.timesMatch(stat)) {
            return null
        }

        val data = repository.workspace.readFile(entry.key)
        val blob = Blob(data)

        return if (blob.oid == entry.oid) {
            if (index is Index.Updater) {
                index.updateEntryStat(entry.key, stat)
            }
            null
        } else {
            Changes.workspaceModified(entry.key)
        }
    }

    private fun loadHeadTree(): Map<String, TreeEntry> {
        val head = repository.refs.readHead() ?: return emptyMap()
        val rel = repository.relativePath(repository.rootPath)
        val commit = repository.database.load(rel, head) as? Commit ?: return emptyMap()
        return readTree(commit.tree, rel)
    }

    private fun readTree(oid: ObjectId, prefix: Path): Map<String, TreeEntry> =
        when (val tree = repository.database.load(prefix, oid)) {
            is Tree -> {
                tree
                    .list()
                    .map { entry ->
                        when {
                            entry.isTree() -> readTree(entry.oid, entry.name)
                            else -> mapOf(entry.name.toString() to entry)
                        }
                    }
                    .fold(emptyMap()) { a, b -> a + b }
            }
            else -> emptyMap()
        }

    private fun checkIndexEntryAgainstHeadTree(tree: Map<String, TreeEntry>, entry: Entry): Changes? {
        val treeEntry = tree[entry.key]
        return when {
            treeEntry == null ->
                Changes.indexAdded(entry.key)
            Mode.fromStat(entry.stat) != treeEntry.mode || entry.oid != treeEntry.oid ->
                Changes.indexModified(entry.key)
            else ->
                null
        }
    }
}
