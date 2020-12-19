package com.github.arian.gikt.repository

import com.github.arian.gikt.FileStat
import com.github.arian.gikt.Mode
import com.github.arian.gikt.database.Blob
import com.github.arian.gikt.database.TreeEntry
import com.github.arian.gikt.index.Entry
import com.github.arian.gikt.index.Index
import java.nio.file.Path

class Inspector(private val repository: Repository) {

    sealed class WorkspaceChange {
        object Untracked : WorkspaceChange()
        data class Modified(val entry: Entry) : WorkspaceChange()
    }

    sealed class IndexChange {
        data class Added(val entry: Entry) : IndexChange()
        data class Modified(val entry: Entry, val treeEntry: TreeEntry) : IndexChange()
    }

    internal fun trackableFile(index: Index.Loaded, path: Path, stat: FileStat): Boolean {
        if (stat.file) {
            return !index.tracked(path)
        }

        val items = repository.workspace.listDir(path)
        val files = items.filter { (_, stat) -> stat.file }
        val dirs = items.filter { (_, stat) -> stat.directory }

        return (files + dirs).any { (itemPath, itemStat) -> trackableFile(index, itemPath, itemStat) }
    }

    internal fun compareIndexToWorkspace(indexEntry: Entry?, stat: FileStat): WorkspaceChange? =
        when {
            indexEntry == null -> WorkspaceChange.Untracked
            !indexEntry.statMatch(stat) -> WorkspaceChange.Modified(indexEntry)
            indexEntry.timesMatch(stat) -> null
            else -> {
                val data = repository.workspace.readFile(indexEntry.name)
                val blob = Blob(data)

                if (blob.oid == indexEntry.oid) {
                    null
                } else {
                    WorkspaceChange.Modified(indexEntry)
                }
            }
        }

    internal fun compareTreeToIndex(item: TreeEntry?, entry: Entry): IndexChange? =
        when {
            item == null ->
                IndexChange.Added(entry)
            Mode.fromStat(entry.stat) != item.mode || entry.oid != item.oid ->
                IndexChange.Modified(entry, item)
            else ->
                null
        }
}
