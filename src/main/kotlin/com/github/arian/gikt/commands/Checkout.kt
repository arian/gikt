package com.github.arian.gikt.commands

import com.github.arian.gikt.Refs
import com.github.arian.gikt.Revision
import com.github.arian.gikt.database.Commit
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.database.TreeDiffMap
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.optional

class Checkout(ctx: CommandContext, name: String) : AbstractCommand(ctx, name) {

    private val target: String by argument(ArgType.String, fullName = "path").optional().default("HEAD")

    override fun run() {
        checkout(target)
    }

    private fun checkout(target: String): Nothing {
        try {
            val targetOid = Revision(repository, target).resolve()

            val currentRef = repository.refs.currentRef()
            val currentOid = currentRef.oid
                ?: throw UnbornHead("You are on a branch yet to be born")

            val code = repository.index.loadForUpdate {

                val treeDiff: TreeDiffMap = repository.database.treeDiff(currentOid, targetOid)
                val migration = repository.migration(treeDiff)
                val result = migration.applyChanges(this)

                if (result.errors.isEmpty()) {
                    writeUpdates()
                    repository.refs.setHead(target, targetOid)

                    val newRef = repository.refs.currentRef()

                    printPreviousHead(currentRef, currentOid, targetOid)
                    printDetachmentNotice(newRef, currentRef, target)
                    printNewHead(newRef, currentRef, target, targetOid)

                    return@loadForUpdate 0
                } else {
                    rollback()

                    result.errors.forEach { ctx.stderr.println("error: $it") }
                    ctx.stderr.println("Aborting")

                    return@loadForUpdate 1
                }
            }

            exitProcess(code)
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

    private fun printPreviousHead(currentRef: Refs.Ref.SymRef, currentOid: ObjectId, targetOid: ObjectId) {
        if (currentRef.isHead && currentOid != targetOid) {
            printHeadPosition("Previous HEAD position was", currentOid)
        }
    }

    private fun printHeadPosition(message: String, oid: ObjectId) {
        val commit = repository.loadObject(oid) as? Commit
            ?: throw IllegalStateException("Expected to load a Commit object for id $oid")
        val short = commit.oid.short
        ctx.stderr.println("$message $short ${commit.title}")
    }

    private fun printDetachmentNotice(newRef: Refs.Ref.SymRef, currentRef: Refs.Ref.SymRef, target: String) {
        if (newRef.isHead && !currentRef.isHead) {
            ctx.stderr.println("Note: checking out '$target'")
            ctx.stderr.println("")
            ctx.stderr.println(DETACHED_HEAD_MESSAGE)
            ctx.stderr.println("")
        }
    }

    private fun printNewHead(
        newRef: Refs.Ref.SymRef,
        currentRef: Refs.Ref.SymRef,
        target: String,
        targetOid: ObjectId
    ) {
        when {
            newRef.isHead -> printHeadPosition("HEAD is now at", targetOid)
            newRef == currentRef -> ctx.stderr.println("Already on '$target'")
            else -> ctx.stderr.println("Switched to branch '$target'")
        }
    }

    companion object {
        private val DETACHED_HEAD_MESSAGE =
            """
            |You are in 'detached HEAD' state. You can look around, make experimental
            |changes and commit them, and you can discard any commits you make in this
            |state without impacting any branches by performing another checkout.
            |
            |If you want to create a new branch to retain commits you create, you may
            |do so (now or later) by using the branch command. Example:
            |
            |  gikt branch <new-branch-name>
        """.trimMargin()
    }

    class UnbornHead(msg: String) : Exception(msg)
}
