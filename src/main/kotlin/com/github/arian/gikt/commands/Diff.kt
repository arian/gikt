package com.github.arian.gikt.commands

import com.github.arian.gikt.Diff
import com.github.arian.gikt.Mode
import com.github.arian.gikt.Style
import com.github.arian.gikt.database.Blob
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.database.TreeEntry
import com.github.arian.gikt.index.Entry
import com.github.arian.gikt.repository.Status
import com.github.arian.gikt.utf8
import java.nio.file.Path
import java.util.Objects

class Diff(ctx: CommandContext) : AbstractCommand(ctx) {

    private data class Target(
        val path: String,
        val oid: ObjectId,
        val mode: Mode? = null,
        val data: ByteArray?
    ) {
        override fun equals(other: Any?) = when (other) {
            is Target -> other.oid == oid && other.path == path
            else -> false
        }

        override fun hashCode(): Int = Objects.hash(path, oid)
    }

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

    private fun header(string: String) =
        println(fmt(Style.BOLD, string))

    private fun printDiff(a: Target, b: Target) {
        if (a.oid == b.oid && a.mode == b.mode) {
            return
        }

        val aFull = a.copy(path = Path.of("a").resolve(a.path).toString())
        val bFull = b.copy(path = Path.of("b").resolve(b.path).toString())

        header("diff --git ${aFull.path} ${bFull.path}")
        printDiffMode(aFull, bFull)
        printDiffContent(aFull, bFull)
    }

    private fun printDiffMode(a: Target, b: Target) {
        if (a.mode == null && b.mode != null) {
            header("new file mode ${b.mode.mode}")
        } else if (a.mode != null && b.mode != null && a.mode != b.mode) {
            header("old mode ${a.mode.mode}")
            header("new mode ${b.mode.mode}")
        } else if (b.mode == null && a.mode != null) {
            header("deleted file mode ${a.mode.mode}")
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

        header(oidRange)
        header("--- ${a.path}")
        header("+++ ${b.path}")

        val hunks = Diff.diffHunks(a.data?.utf8(), b.data?.utf8())
        hunks.forEach { printDiffHunk(it) }
    }

    private fun printDiffHunk(hunk: Diff.Hunk) {
        println(fmt(Style.CYAN, hunk.header))
        hunk.edits.forEach {
            when (it) {
                is Diff.Edit.Eql -> println(it.toString())
                is Diff.Edit.Ins -> println(fmt(Style.GREEN, it.toString()))
                is Diff.Edit.Del -> println(fmt(Style.RED, it.toString()))
            }
        }
    }
}
