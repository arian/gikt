package com.github.arian.gikt.commands.util

import com.github.arian.gikt.Diff
import com.github.arian.gikt.Mode
import com.github.arian.gikt.commands.Cli
import com.github.arian.gikt.database.GiktObject
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.database.TreeEntry
import com.github.arian.gikt.utf8
import kotlinx.cli.ArgType
import java.nio.file.Path
import java.util.Objects

class PrintDiff(
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

        companion object {
            fun fromNothing(path: String): Target {
                return Target(
                    path = path,
                    oid = ObjectId(ByteArray(20) { 0.toByte() }),
                    data = null
                )
            }

            fun fromHead(entry: TreeEntry, blob: GiktObject): Target {
                return Target(entry.name.toString(), entry.oid, entry.mode, blob.data)
            }
        }
    }

    internal class Options(cli: Cli, private val default: Boolean) {
        private val patchOption by cli.option(
            ArgType.Boolean,
            fullName = "patch",
            shortName = "p"
        )

        private val noPatchOption by cli.option(
            ArgType.Boolean,
            fullName = "no-patch",
            shortName = "s"
        )

        val patch: Boolean
            get() = when {
                noPatchOption == true -> false
                patchOption == true -> true
                else -> default
            }
    }

    internal fun diff(a: Target, b: Target): String {
        if (a.oid == b.oid && a.mode == b.mode) {
            return ""
        }

        val aFull = a.copy(path = Path.of("a").resolve(a.path).toString())
        val bFull = b.copy(path = Path.of("b").resolve(b.path).toString())

        val h = header("diff --git ${aFull.path} ${bFull.path}")
        val mode = printDiffMode(aFull, bFull)
        val content = printDiffContent(aFull, bFull)

        return "$h$mode$content"
    }

    private fun header(string: String) =
        "${fmt(Style.BOLD, string)}\n"

    private fun printDiffMode(a: Target, b: Target): String {
        return when {
            a.mode == null && b.mode != null ->
                header("new file mode ${b.mode.mode}")
            a.mode != null && b.mode != null && a.mode != b.mode ->
                header("old mode ${a.mode.mode}") + header("new mode ${b.mode.mode}")
            b.mode == null && a.mode != null ->
                header("deleted file mode ${a.mode.mode}")
            else -> ""
        }
    }

    private fun printDiffContent(a: Target, b: Target): String {
        if (a.oid == b.oid) {
            return ""
        }

        val oidRangeBegin = "index ${a.oid.short}..${b.oid.short}"
        val oidRange = when {
            a.mode != null && a.mode == b.mode -> "$oidRangeBegin ${a.mode.mode}"
            else -> oidRangeBegin
        }

        val head =
            header(oidRange) +
                header("--- ${a.path}") +
                header("+++ ${b.path}")

        val hunks = Diff.diffHunks(a.data?.utf8(), b.data?.utf8())
        return head + hunks.joinToString(separator = "") { printDiffHunk(it) }
    }

    private fun printDiffHunk(hunk: Diff.Hunk): String {
        val head = fmt(Style.CYAN, hunk.header)
        val edits = hunk.edits.joinToString(separator = "\n") {
            when (it) {
                is Diff.Edit.Eql -> it.toString()
                is Diff.Edit.Ins -> fmt(Style.GREEN, it.toString())
                is Diff.Edit.Del -> fmt(Style.RED, it.toString())
            }
        }
        return "$head\n$edits"
    }

    companion object {
        internal const val NULL_PATH = "/dev/null"
    }
}
