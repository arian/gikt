package com.github.arian.gikt.commands

import com.github.arian.gikt.FileStat
import com.github.arian.gikt.Repository
import com.github.arian.gikt.database.Blob
import com.github.arian.gikt.index.Entry
import com.github.arian.gikt.index.Index
import java.nio.file.Path

class Status(ctx: CommandContext) : AbstractCommand(ctx) {

    private data class Changes(
        val workspaceDeleted: Set<String> = emptySet(),
        val workspaceModified: Set<String> = emptySet()
    ) {
        operator fun plus(other: Changes) =
            copy(
                workspaceDeleted = workspaceDeleted + other.workspaceDeleted,
                workspaceModified = workspaceModified + other.workspaceModified
            )

        companion object {
            fun deleted(key: String) = Changes(workspaceDeleted = setOf(key))
            fun modified(key: String) = Changes(workspaceModified = setOf(key))
        }
    }

    private data class Scan(
        val stats: Map<String, FileStat> = emptyMap(),
        val changes: Changes = Changes(),
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
                detectWorkspaceChanges(repository, this, scanWorkspace(this))
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

    private fun detectWorkspaceChanges(repository: Repository, index: Index.Updater, scan: Scan): Scan {
        val changes = index.toList()
            .mapNotNull { checkIndexEntry(repository, index, scan, it) }

        if (changes.isEmpty()) {
            return scan
        }

        return scan.copy(changes = changes.reduce { acc, it -> acc + it })
    }

    private fun checkIndexEntry(repository: Repository, index: Index.Updater, scan: Scan, entry: Entry): Changes? {
        val stat = scan.stats[entry.key] ?: return Changes.deleted(entry.key)

        if (!entry.statMatch(stat)) {
            return Changes.modified(entry.key)
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
            Changes.modified(entry.key)
        }
    }

    private fun printResults(scan: Scan) {

        (scan.changes.workspaceDeleted + scan.changes.workspaceModified)
            .sorted()
            .forEach { println("${statusFor(scan, it)} $it") }

        scan.untracked
            .sorted()
            .forEach { println("?? $it") }
    }

    private fun statusFor(scan: Scan, key: String): String {
        val changes = scan.changes

        return when {
            changes.workspaceModified.contains(key) -> " M"
            changes.workspaceDeleted.contains(key) -> " D"
            else -> "  "
        }
    }
}
