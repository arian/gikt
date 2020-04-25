package com.github.arian.gikt.commands

import com.github.arian.gikt.Mode
import com.github.arian.gikt.database.Blob
import com.github.arian.gikt.index.Entry
import com.github.arian.gikt.index.Index
import com.github.arian.gikt.repository.Status
import com.github.arian.gikt.repository.Status.Changes.ChangeType.DELETED
import com.github.arian.gikt.repository.Status.Changes.ChangeType.MODIFIED
import java.nio.file.Path

class Diff(ctx: CommandContext) : AbstractCommand(ctx) {
    override fun run() {

        val index = repository.index.load()
        val status = repository.status()
        val scan = status.scan(index)

        scan.changes.workspaceChanges().forEach { (path, type) ->
            when (type) {
                MODIFIED -> diffFileModified(index, scan, path)
                DELETED -> diffFileDeleted(index, scan, path)
                else -> {}
            }
        }

        exitProcess(0)
    }

    private fun diffFileModified(index: Index.Loaded, scan: Status.Scan, path: String) {
        val entry: Entry = index[path] ?: return
        val aOid = entry.oid
        val aMode = entry.mode
        val aPath = Path.of("a").resolve(path)

        val blob = Blob(repository.workspace.readFile(path))
        val bOid = blob.oid
        val bMode = Mode.fromStat(scan.stats[path] ?: return)
        val bPath = Path.of("b").resolve(path)

        println("diff --git $aPath $bPath")

        if (aMode != bMode) {
            println("old mode ${aMode.mode}")
            println("new mode ${bMode.mode}")
        }

        if (aOid == bOid) {
            return
        }

        val oidRangeBegin = "index ${aOid.short}..${bOid.short}"
        val oidRange = when (aMode) {
            bMode -> "$oidRangeBegin ${aMode.mode}"
            else -> oidRangeBegin
        }

        println(oidRange)
        println("--- $aPath")
        println("+++ $bPath")
    }

    private fun diffFileDeleted(index: Index.Loaded, scan: Status.Scan, path: String) {
        val entry: Entry = index[path] ?: return
        val aOid = entry.oid
        val aMode = entry.mode
        val aPath = Path.of("a").resolve(path)

        val bOid = "0000000"
        val bPath = "/dev/null"

        println("diff --git $aPath $bPath")
        println("deleted file mode ${aMode.mode}")
        println("index ${aOid.short}..$bOid")
        println("--- $aPath")
        println("+++ $bPath")
    }
}
