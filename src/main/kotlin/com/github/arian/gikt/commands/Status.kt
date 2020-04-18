package com.github.arian.gikt.commands

import com.github.arian.gikt.FileStat
import com.github.arian.gikt.Repository
import com.github.arian.gikt.database.Blob
import com.github.arian.gikt.database.Commit
import com.github.arian.gikt.database.Mode
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.database.Tree
import com.github.arian.gikt.database.TreeEntry
import com.github.arian.gikt.index.Entry
import com.github.arian.gikt.index.Index
import com.github.arian.gikt.relativeTo
import java.nio.file.Path

class Status(ctx: CommandContext) : AbstractCommand(ctx) {

    private data class Changes internal constructor(
        private val workspaceDeleted: Set<String> = emptySet(),
        private val workspaceModified: Set<String> = emptySet(),
        private val indexAdded: Set<String> = emptySet(),
        private val indexModified: Set<String> = emptySet(),
        private val indexDeleted: Set<String> = emptySet()
    ) {
        operator fun plus(other: Changes) =
            copy(
                workspaceDeleted = workspaceDeleted + other.workspaceDeleted,
                workspaceModified = workspaceModified + other.workspaceModified,
                indexAdded = indexAdded + other.indexAdded,
                indexModified = indexModified + other.indexModified,
                    indexDeleted = indexDeleted + other.indexDeleted
            )

        companion object {
            fun workspaceDeleted(key: String) = Changes(workspaceDeleted = setOf(key))
            fun workspaceModified(key: String) = Changes(workspaceModified = setOf(key))
            fun indexAdded(key: String) = Changes(indexAdded = setOf(key))
            fun indexModified(key: String) = Changes(indexModified = setOf(key))
            fun indexDeleted(key: String) = Changes(indexDeleted = setOf(key))
            fun empty() = Changes()
        }

        fun all() = workspaceDeleted + workspaceModified + indexAdded + indexModified + indexDeleted
        fun isWorkspaceModified(key: String) = workspaceModified.contains(key)
        fun isWorkspaceDeleted(key: String) = workspaceDeleted.contains(key)
        fun isIndexAdded(key: String) = indexAdded.contains(key)
        fun isIndexModified(key: String) = indexModified.contains(key)
        fun isIndexDeleted(key: String) = indexDeleted.contains(key)
    }

    private data class Scan(
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

    override fun run() {
        val scan = repository.index
            .loadForUpdate {
                val workspaceScan = scanWorkspace(this)
                val headTree = loadHeadTree()
                checkIndexEntries(repository, this, workspaceScan, headTree)
                    .also { writeUpdates() }
            }

        printResults(scan)

        exitProcess(0)
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
        index: Index.Updater,
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
        index: Index.Updater,
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
            index.updateEntryStat(entry.key, stat)
            null
        } else {
            Changes.workspaceModified(entry.key)
        }
    }

    private fun loadHeadTree(): Map<String, TreeEntry> {
        val head = repository.refs.readHead() ?: return emptyMap()
        val rel = ctx.dir.relativeTo(ctx.dir)
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

    private fun printResults(scan: Scan) {

        (scan.changes.all())
            .sorted()
            .forEach { println("${statusFor(scan, it)} $it") }

        scan.untracked
            .sorted()
            .forEach { println("?? $it") }
    }

    private fun statusFor(scan: Scan, key: String): String {
        val left = when {
            scan.changes.isIndexAdded(key) -> "A"
            scan.changes.isIndexModified(key) -> "M"
            scan.changes.isIndexDeleted(key) -> "D"
            else -> " "
        }
        val right = when {
            scan.changes.isWorkspaceModified(key) -> "M"
            scan.changes.isWorkspaceDeleted(key) -> "D"
            else -> " "
        }
        return "$left$right"
    }
}
