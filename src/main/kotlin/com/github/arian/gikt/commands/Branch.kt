package com.github.arian.gikt.commands

import com.github.arian.gikt.Refs
import com.github.arian.gikt.Revision
import com.github.arian.gikt.commands.util.Style
import com.github.arian.gikt.database.Commit
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.optional

class Branch(ctx: CommandContext, name: String) : AbstractCommand(ctx, name) {

    private val branch: String? by argument(ArgType.String, fullName = "branch").optional()
    private val startPoint: String? by argument(ArgType.String, fullName = "start-point").optional()
    private val verbose: Boolean by option(ArgType.Boolean, shortName = "v").default(false)

    override fun run() {
        when (val b = branch) {
            null -> listBranches()
            else -> createBranch(b, startPoint)
        }
    }

    private fun listBranches() {
        val current = repository.refs.currentRef()
        val branches = repository.refs
            .listBranches()
            .sortedBy { it.longName }
        val maxWidth = branches.map { it.shortName.length }.maxOrNull() ?: 0

        branches.forEach { ref ->
            val info = formatRef(ref, current)
            val extendedBranchInfo = extendedBranchInfo(ref, maxWidth)
            println(info + extendedBranchInfo)
        }
    }

    private fun formatRef(ref: Refs.Ref.SymRef, current: Refs.Ref.SymRef): String {
        return if (ref == current) {
            "* ${fmt(Style.GREEN, ref.shortName)}"
        } else {
            "  ${ref.shortName}"
        }
    }

    private fun extendedBranchInfo(ref: Refs.Ref.SymRef, maxWidth: Int): String {
        if (!verbose) {
            return ""
        }

        val oid = ref.oid ?: return ""
        val commit = repository.loadObject(oid) as? Commit ?: return ""
        val short = commit.oid.short
        val space = " ".repeat(maxWidth - ref.shortName.length)

        return "$space $short ${commit.title}"
    }

    private fun createBranch(branchName: String, startPoint: String?) {
        try {
            val startOid = startPoint
                ?.let { Revision(repository, it).resolve() }
                ?: repository.refs.readHead()
                ?: throw IllegalStateException("Couldn't read HEAD")

            repository.refs.createBranch(branchName, startOid)
        } catch (e: Revision.InvalidObject) {
            e.errors.forEach { hint ->
                ctx.stderr.println("error: ${hint.message}")
                hint.hints.forEach { line -> ctx.stderr.println("hint: $line") }
            }
            ctx.stderr.println("fatal: ${e.message}")
            exitProcess(128)
        } catch (e: Refs.InvalidBranch) {
            ctx.stderr.println("fatal: ${e.message}")
            exitProcess(128)
        }
    }
}
