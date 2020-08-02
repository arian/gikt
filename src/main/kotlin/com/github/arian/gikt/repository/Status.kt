package com.github.arian.gikt.repository

import com.github.arian.gikt.FileStat
import com.github.arian.gikt.database.Commit
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.database.Tree
import com.github.arian.gikt.database.TreeEntry
import com.github.arian.gikt.index.Entry
import com.github.arian.gikt.index.Index
import java.nio.file.Path
import java.util.SortedMap

class Status(private val repository: Repository) {

    private val inspector = Inspector(repository)

    enum class ChangeType(val short: String) {
        MODIFIED("M"),
        ADDED("A"),
        DELETED("D")
    }

    interface Change : Comparable<Change> {
        val key: String
        val changeType: ChangeType
    }

    sealed class WorkspaceChange(
        override val key: String,
        override val changeType: ChangeType
    ) : Change {
        data class Modified(val entry: Entry) : WorkspaceChange(entry.key, ChangeType.MODIFIED)
        data class Deleted(val entry: Entry) : WorkspaceChange(entry.key, ChangeType.DELETED)

        override fun compareTo(other: Change) = key.compareTo(other.key)
    }

    sealed class IndexChange(
        override val key: String,
        override val changeType: ChangeType
    ) : Change {

        data class Added(val entry: Entry) : IndexChange(entry.key, ChangeType.ADDED)

        data class Modified(
            val entry: Entry,
            val treeEntry: TreeEntry
        ) : IndexChange(entry.key, ChangeType.MODIFIED)

        data class Deleted(
            val treeEntry: TreeEntry
        ) : IndexChange(treeEntry.name.toString(), ChangeType.DELETED)

        override fun compareTo(other: Change) = key.compareTo(other.key)
    }

    data class Changes internal constructor(
        private val workspace: SortedMap<String, WorkspaceChange> = sortedMapOf(),
        private val index: SortedMap<String, IndexChange> = sortedMapOf()
    ) {

        operator fun plus(other: Changes) =
            copy(
                workspace = (workspace + other.workspace).toSortedMap(),
                index = (index + other.index).toSortedMap()
            )

        companion object {
            internal fun workspace(change: WorkspaceChange) = Changes(workspace = sortedMapOf(change.key to change))
            internal fun index(change: IndexChange) = Changes(index = sortedMapOf(change.key to change))
            internal fun empty() = Changes()
        }

        fun all(): Set<String> = (workspace.keys + index.keys).toSortedSet()
        fun workspaceChanges(): Set<WorkspaceChange> = workspace.values.toSortedSet()
        fun indexChanges(): Set<IndexChange> = index.values.toSortedSet()
        fun indexChange(key: String) = index[key]
        fun workspaceChange(key: String) = workspace[key]
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
        return checkIndexEntries(index, workspaceScan, headTree)
    }

    private fun scanWorkspace(index: Index.Loaded, prefix: Path? = null): Scan {
        val ls = prefix
            ?.let { repository.workspace.listDir(it) }
            ?: repository.workspace.listDir()

        return ls
            .map { (it, stat) -> checkPath(index, it, stat) }
            .fold(Scan()) { acc, scan -> acc + scan }
    }

    private fun checkPath(index: Index.Loaded, path: Path, stat: FileStat): Scan =
        when {
            index.tracked(path) -> {
                when (stat.directory) {
                    true -> scanWorkspace(index, path)
                    false -> Scan(stats = mapOf("$path" to stat))
                }
            }
            inspector.trackableFile(index, path, stat) -> {
                val name = when (stat.directory) {
                    true -> "$path/"
                    false -> "$path"
                }
                Scan(untracked = setOf(name))
            }
            else -> Scan()
        }

    private fun checkIndexEntries(
        index: Index.Loaded,
        scan: Scan,
        headTree: Map<String, TreeEntry>
    ): Scan {
        val workspaceChanges = index.toList()
            .mapNotNull { checkIndexEntryAgainstWorkspace(index, scan, it) }

        val headChanges = index.toList()
            .mapNotNull { checkIndexEntryAgainstHeadTree(headTree, it) }

        val indexDeleted = headTree
            .filterNot { index.tracked(it.key) }
            .map { Changes.index(IndexChange.Deleted(it.value)) }

        val changes = workspaceChanges + headChanges + indexDeleted

        if (changes.isEmpty()) {
            return scan
        }

        return scan.copy(changes = changes.reduce { acc, it -> acc + it })
    }

    private fun checkIndexEntryAgainstWorkspace(
        index: Index.Loaded,
        scan: Scan,
        entry: Entry
    ): Changes? {
        val stat = scan.stats[entry.key] ?: return Changes.workspace(WorkspaceChange.Deleted(entry))

        return when (inspector.compareIndexToWorkspace(entry, stat)) {
            is Inspector.WorkspaceChange.Modified ->
                Changes.workspace(WorkspaceChange.Modified(entry))
            is Inspector.WorkspaceChange.Untracked ->
                null
            null -> {
                if (index is Index.Updater) {
                    index.updateEntryStat(entry.key, stat)
                }
                null
            }
        }
    }

    private fun loadHeadTree(): Map<String, TreeEntry> {
        val head = repository.refs.readHead() ?: return emptyMap()
        val rel = repository.relativeRoot
        val commit = repository.database.load(head, rel) as? Commit ?: return emptyMap()
        return readTree(commit.tree, rel)
    }

    private fun readTree(oid: ObjectId, prefix: Path): Map<String, TreeEntry> =
        when (val tree = repository.database.load(oid, prefix)) {
            is Tree -> tree
                .list()
                .map { entry ->
                    when {
                        entry.isTree() -> readTree(entry.oid, entry.name)
                        else -> mapOf(entry.name.toString() to entry)
                    }
                }
                .fold(emptyMap()) { a, b -> a + b }
            else -> emptyMap()
        }

    private fun checkIndexEntryAgainstHeadTree(tree: Map<String, TreeEntry>, entry: Entry): Changes? {
        val treeEntry = tree[entry.key]
        return when (val status = inspector.compareTreeToIndex(treeEntry, entry)) {
            is Inspector.IndexChange.Added ->
                Changes.index(IndexChange.Added(entry))
            is Inspector.IndexChange.Modified ->
                Changes.index(IndexChange.Modified(entry, status.treeEntry))
            null ->
                null
        }
    }
}
