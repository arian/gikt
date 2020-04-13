package com.github.arian.gikt.commands

import com.github.arian.gikt.FileStat
import com.github.arian.gikt.Repository
import com.github.arian.gikt.database.Blob
import com.github.arian.gikt.index.Entry
import com.github.arian.gikt.index.Index
import java.nio.file.Path

class Status(ctx: CommandContext) : AbstractCommand(ctx) {

    private data class Scan(
        val stats: Map<String, FileStat> = emptyMap(),
        val changed: Set<String> = emptySet(),
        val untracked: Set<String> = emptySet()
    )

    private fun List<Scan>.combine(): Scan =
        reduce { acc, scan ->
            acc.copy(
                stats = acc.stats + scan.stats,
                changed = acc.changed + scan.changed,
                untracked = acc.untracked + scan.untracked
            )
        }

    override fun run() {
        val scan = repository.index
            .loadForUpdate {
                detectWorkspaceChanges(repository, this, scanWorkspace(this))
                    .also { writeUpdates() }
            }

        scan.changed
            .sorted()
            .forEach { println(" M $it") }

        scan.untracked
            .sorted()
            .forEach { println("?? $it") }

        exitProcess(0)
    }

    private fun scanWorkspace(index: Index.Loaded, prefix: Path? = null): Scan {
        val ls = prefix
            ?.let { repository.workspace.listDir(it) }
            ?: repository.workspace.listDir()

        return ls.map { (it, stat) -> checkPath(index, it, stat) }.combine()
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

    private fun detectWorkspaceChanges(repository: Repository, index: Index.Updater, scan: Scan): Scan =
        scan.copy(
            changed = index.toList().mapNotNull { checkIndexEntry(repository, index, scan, it) }.toSet()
        )

    private fun checkIndexEntry(repository: Repository, index: Index.Updater, scan: Scan, entry: Entry): String? {
        val stat = scan.stats[entry.key] ?: return null
        if (!entry.statMatch(stat)) {
            return entry.key
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
            entry.key
        }
    }
}
