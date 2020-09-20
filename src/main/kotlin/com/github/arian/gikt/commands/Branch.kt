package com.github.arian.gikt.commands

import com.github.arian.gikt.Refs
import com.github.arian.gikt.Revision
import kotlinx.cli.ArgType
import kotlinx.cli.optional

class Branch(ctx: CommandContext, name: String) : AbstractCommand(ctx, name) {

    private val branch: String? by argument(ArgType.String, fullName = "branch").optional()
    private val startPoint: String? by argument(ArgType.String, fullName = "start-point").optional()

    override fun run() {
        when (val b = branch) {
            null -> listBranches()
            else -> createBranch(b, startPoint)
        }
    }

    private fun listBranches(): Nothing {
        exitProcess(0)
    }

    private fun createBranch(branchName: String, startPoint: String?): Nothing {
        try {
            val startOid = startPoint
                ?.let { Revision(repository, it).resolve() }
                ?: repository.refs.readHead()
                ?: throw IllegalStateException("Couldn't read HEAD")

            this.repository.refs.createBranch(branchName, startOid)

            exitProcess(0)
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
