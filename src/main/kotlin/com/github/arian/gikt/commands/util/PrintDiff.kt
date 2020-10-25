package com.github.arian.gikt.commands.util

import com.github.arian.gikt.Diff
import com.github.arian.gikt.Mode
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.utf8
import java.nio.file.Path
import java.util.Objects

class PrintDiff(
    private val println: (String) -> Unit,
    private val fmt: (Style, String) -> String
) {

    internal data class Target(
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

    private fun header(string: String) =
        println(fmt(Style.BOLD, string))

    internal fun printDiff(a: Target, b: Target) {
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

    companion object {
        internal const val NULL_OID = "0000000000000000000000000000000000000000"
        internal const val NULL_PATH = "/dev/null"
    }
}
