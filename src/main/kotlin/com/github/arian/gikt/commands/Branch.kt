package com.github.arian.gikt.commands

import com.github.arian.gikt.Refs
import com.github.arian.gikt.Revision

class Branch(ctx: CommandContext) : AbstractCommand(ctx) {
    override fun run() {

        when (val first = ctx.args.firstOrNull()) {
            null -> listBranches()
            else -> createBranch(first, ctx.args.getOrNull(1))
        }

        exitProcess(0)
    }

    private fun listBranches() {
    }

    private fun createBranch(branchName: String, startPoint: String?) {
        try {
            val startOid = startPoint
                ?.let { Revision(repository, it).resolve() }
                ?: repository.refs.readHead()
                ?: throw IllegalStateException("Couldn't read HEAD")

            this.repository.refs.createBranch(branchName, startOid)
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
