package com.github.arian.gikt.commands

import com.github.arian.gikt.Mode
import com.github.arian.gikt.commands.util.PrintDiff
import com.github.arian.gikt.commands.util.PrintDiff.Companion.NULL_PATH
import com.github.arian.gikt.commands.util.PrintDiff.Target
import com.github.arian.gikt.database.Blob
import com.github.arian.gikt.database.TreeEntry
import com.github.arian.gikt.index.Entry
import com.github.arian.gikt.index.Index
import com.github.arian.gikt.repository.Status
import kotlinx.cli.ArgType
import kotlinx.cli.default

class Diff(ctx: CommandContext, name: String) : AbstractCommand(ctx, name) {

    private val cached: Boolean by cli.option(ArgType.Boolean).default(false)
    private val staged: Boolean by cli.option(ArgType.Boolean).default(false)

    private val printDiffOptions = PrintDiff.Options(cli, default = true)
    private val printDiff = PrintDiff(fmt = ::fmt)

    private val conflictOptions = ConflictOptions(cli)

    private fun printDiff(a: Target, b: Target) {
        println(printDiff.diff(a, b))
    }

    override fun run() {

        val index = repository.index.load()
        val scan = repository.status().scan(index)

        when (cached || staged) {
            true -> diffHeadIndex(scan)
            else -> diffIndexWorkspace(index, scan)
        }

        exitProcess(0)
    }

    private fun diffIndexWorkspace(index: Index.Loaded, scan: Status.Scan) {
        if (!printDiffOptions.patch) {
            return
        }
        scan.conflictsAndWorkspaceChanges().forEach { key ->
            when (val change = scan.changes.workspaceChange(key)) {
                null -> printConflictDiff(index, scan, key)
                else -> printWorkspaceDiff(scan, change)
            }
        }
    }

    private fun printConflictDiff(index: Index.Loaded, scan: Status.Scan, path: String) {
        println("* Unmerged path $path")
        val stage = conflictOptions.side.stage
        val target = fromIndex(index, path, stage) ?: return
        printDiff(target, fromFile(path, scan))
    }

    private fun printWorkspaceDiff(scan: Status.Scan, change: Status.WorkspaceChange) {
        when (change) {
            is Status.WorkspaceChange.Modified -> printDiff(fromIndex(change.entry), fromFile(change.key, scan))
            is Status.WorkspaceChange.Deleted -> printDiff(fromIndex(change.entry), fromNothing(NULL_PATH))
        }
    }

    private fun diffHeadIndex(scan: Status.Scan) {
        if (!printDiffOptions.patch) {
            return
        }
        scan.changes.indexChanges().forEach { change ->
            when (change) {
                is Status.IndexChange.Added -> printDiff(fromNothing(change.key), fromIndex(change.entry))
                is Status.IndexChange.Modified -> printDiff(fromHead(change.treeEntry), fromIndex(change.entry))
                is Status.IndexChange.Deleted -> printDiff(fromHead(change.treeEntry), fromNothing(change.key))
            }
        }
    }

    private fun fromIndex(entry: Entry): Target {
        val blob = repository.loadObject(entry.oid)
        return Target(entry.name, entry.oid, entry.mode, blob.data)
    }

    private fun fromIndex(index: Index.Loaded, path: String, stage: Byte): Target? =
        index[path, stage]?.let { fromIndex(it) }

    private fun fromFile(path: String, scan: Status.Scan): Target {
        val blob = Blob(repository.workspace.readFile(path))
        val mode = scan.stats[path]?.let { Mode.fromStat(it) } ?: Mode.REGULAR
        return Target(path, blob.oid, mode, blob.data)
    }

    private fun fromNothing(path: String): Target =
        Target.fromNothing(path)

    private fun fromHead(entry: TreeEntry): Target =
        Target.fromHead(entry, repository.loadObject(entry.oid))

    internal class ConflictOptions(cli: Cli) {
        private val base by cli.option(
            ArgType.Boolean,
            fullName = "base",
            shortName = "1"
        )
        private val ours by cli.option(
            ArgType.Boolean,
            fullName = "ours",
            shortName = "2"
        )
        private val theirs by cli.option(
            ArgType.Boolean,
            fullName = "theirs",
            shortName = "3"
        )

        enum class Side(val stage: Byte) {
            BASE(1),
            OURS(2),
            THEIRS(3),
        }

        val side: Side
            get() = when {
                base == true -> Side.BASE
                ours == true -> Side.OURS
                theirs == true -> Side.THEIRS
                else -> TODO("Combined diff not implemented yet")
            }
    }
}
