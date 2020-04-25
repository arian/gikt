package com.github.arian.gikt.commands

import com.github.arian.gikt.Mode
import com.github.arian.gikt.database.Blob
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.database.TreeEntry
import com.github.arian.gikt.index.Entry
import com.github.arian.gikt.repository.Status
import java.nio.file.Path

class Diff(ctx: CommandContext) : AbstractCommand(ctx) {

    private data class Target(val path: String, val oid: ObjectId, val mode: Mode? = null)

    override fun run() {

        val index = repository.index.load()
        val scan = repository.status().scan(index)

        when (ctx.args.firstOrNull()) {
            "--cached" -> diffHeadIndex(scan)
            else -> diffIndexWorkspace(scan)
        }

        exitProcess(0)
    }

    private fun diffIndexWorkspace(scan: Status.Scan) {
        scan.changes.workspaceChanges().forEach { change ->
            when (change) {
                is Status.WorkspaceChange.Modified -> printDiff(fromIndex(change.entry), fromFile(change.key, scan))
                is Status.WorkspaceChange.Deleted -> printDiff(fromIndex(change.entry), fromNothing("/dev/null"))
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

    private fun fromIndex(entry: Entry): Target =
        Target(entry.key, entry.oid, entry.mode)

    private fun fromFile(path: String, scan: Status.Scan): Target {
        val blob = Blob(repository.workspace.readFile(path))
        val mode = scan.stats[path]?.let { Mode.fromStat(it) } ?: Mode.REGULAR
        return Target(path, blob.oid, mode)
    }

    private fun fromNothing(path: String): Target {
        return Target(path = path, oid = ObjectId(ByteArray(20) { 0.toByte() }))
    }

    private fun fromHead(entry: TreeEntry): Target {
        return Target(entry.name.toString(), entry.oid, entry.mode)
    }

    private fun printDiff(a: Target, b: Target) {
        if (a.oid == b.oid && a.mode == b.mode) {
            return
        }

        val aFull = a.copy(path = Path.of("a").resolve(a.path).toString())
        val bFull = b.copy(path = Path.of("b").resolve(b.path).toString())

        println("diff --git ${aFull.path} ${bFull.path}")
        printDiffMode(aFull, bFull)
        printDiffContent(aFull, bFull)
    }

    private fun printDiffMode(a: Target, b: Target) {
        if (a.mode == null && b.mode != null) {
            println("new file mode ${b.mode.mode}")
        } else if (a.mode != null && b.mode != null && a.mode != b.mode) {
            println("old mode ${a.mode.mode}")
            println("new mode ${b.mode.mode}")
        } else if (b.mode == null && a.mode != null) {
            println("deleted file mode ${a.mode.mode}")
        }
    }

    private fun printDiffContent(a: Target, b: Target) {
        if (a.oid == b.oid) {
            return
        }

        val oidRangeBegin = "index ${a.oid.short}..${b.oid.short}"
        val oidRange = when {
            a.mode != null && a.mode == b.mode -> "$oidRangeBegin ${a.mode.mode}"
            else -> oidRangeBegin
        }

        println(oidRange)
        println("--- ${a.path}")
        println("+++ ${b.path}")
    }
}
