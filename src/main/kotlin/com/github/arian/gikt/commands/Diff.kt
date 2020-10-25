package com.github.arian.gikt.commands

import com.github.arian.gikt.Mode
import com.github.arian.gikt.commands.util.PrintDiff
import com.github.arian.gikt.commands.util.PrintDiff.Companion.NULL_PATH
import com.github.arian.gikt.commands.util.PrintDiff.Target
import com.github.arian.gikt.database.Blob
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.database.TreeEntry
import com.github.arian.gikt.index.Entry
import com.github.arian.gikt.repository.Status
import kotlinx.cli.ArgType
import kotlinx.cli.default

class Diff(ctx: CommandContext, name: String) : AbstractCommand(ctx, name) {

    private val cached: Boolean by option(ArgType.Boolean).default(false)
    private val staged: Boolean by option(ArgType.Boolean).default(false)

    private val printDiff = PrintDiff(
        println = ::println,
        fmt = ::fmt
    )

    private fun printDiff(a: Target, b: Target) {
        printDiff.printDiff(a, b)
    }

    override fun run() {

        val index = repository.index.load()
        val scan = repository.status().scan(index)

        when (cached || staged) {
            true -> diffHeadIndex(scan)
            else -> diffIndexWorkspace(scan)
        }

        exitProcess(0)
    }

    private fun diffIndexWorkspace(scan: Status.Scan) {
        scan.changes.workspaceChanges().forEach { change ->
            when (change) {
                is Status.WorkspaceChange.Modified -> printDiff(fromIndex(change.entry), fromFile(change.key, scan))
                is Status.WorkspaceChange.Deleted -> printDiff(fromIndex(change.entry), fromNothing(NULL_PATH))
            }
        }
    }

    private fun diffHeadIndex(scan: Status.Scan) {
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
        return Target(entry.key, entry.oid, entry.mode, blob.data)
    }

    private fun fromFile(path: String, scan: Status.Scan): Target {
        val blob = Blob(repository.workspace.readFile(path))
        val mode = scan.stats[path]?.let { Mode.fromStat(it) } ?: Mode.REGULAR
        return Target(path, blob.oid, mode, blob.data)
    }

    private fun fromNothing(path: String): Target {
        return Target(
            path = path,
            oid = ObjectId(ByteArray(20) { 0.toByte() }),
            data = null
        )
    }

    private fun fromHead(entry: TreeEntry): Target {
        val blob = repository.loadObject(entry.oid)
        return Target(entry.name.toString(), entry.oid, entry.mode, blob.data)
    }
}
