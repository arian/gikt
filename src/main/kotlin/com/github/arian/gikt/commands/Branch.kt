package com.github.arian.gikt.commands

import com.github.arian.gikt.Refs

class Branch(ctx: CommandContext) : AbstractCommand(ctx) {
    override fun run() {

        when (val first = ctx.args.firstOrNull()) {
            null -> listBranches()
            else -> createBranch(first)
        }

        exitProcess(0)
    }

    private fun listBranches() {
    }

    private fun createBranch(branchName: String) {
        try {
            this.repository.refs.createBranch(branchName)
        } catch (e: Refs.InvalidBranch) {
            ctx.stderr.println("fatal: ${e.message}")
            exitProcess(128)
        }
    }
}
