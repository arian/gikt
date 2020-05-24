package com.github.arian.gikt.commands

import com.github.arian.gikt.Revision
import com.github.arian.gikt.database.TreeDiffMap

class Checkout(ctx: CommandContext) : AbstractCommand(ctx) {
    override fun run() {
        val target = ctx.args.firstOrNull() ?: "HEAD"
        checkout(target)
    }

    private fun checkout(target: String): Nothing {
        try {
            val revision = Revision(repository, target).resolve()

            val currentOid = repository.refs.readHead()
                ?: throw UnbornHead("You are on a branch yet to be born")

            val treeDiff: TreeDiffMap = repository.database.treeDiff(currentOid, revision)
            val migration = repository.migration(treeDiff)
            migration.applyChanges()

            exitProcess(0)
        } catch (e: Revision.InvalidObject) {
            handleInvalidObject(e)
        } catch (e: UnbornHead) {
            ctx.stderr.println("fatal: ${e.message}")
            exitProcess(128)
        }
    }

    private fun handleInvalidObject(e: Revision.InvalidObject): Nothing {
        e.errors.forEach { err ->
            ctx.stderr.println("error: ${err.message}")
            err.hints.forEach { line -> ctx.stderr.println("hint: $line") }
        }
        ctx.stderr.println("error: ${e.message}")
        exitProcess(128)
    }

    class UnbornHead(msg: String) : Exception(msg)
}
