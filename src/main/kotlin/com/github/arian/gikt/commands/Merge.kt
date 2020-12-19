package com.github.arian.gikt.commands

import com.github.arian.gikt.Revision
import com.github.arian.gikt.commands.util.WriteCommit
import com.github.arian.gikt.merge.Inputs
import com.github.arian.gikt.merge.Resolve
import kotlinx.cli.ArgType

class Merge(ctx: CommandContext, name: String) : AbstractCommand(ctx, name) {

    private val revision by cli.argument(ArgType.String)
    private val writeCommit = WriteCommit(ctx, repository)

    override fun run(): Nothing {

        val inputs = try {
            Inputs(repository, leftName = Revision.HEAD, rightName = revision)
        } catch (e: Revision.InvalidObject) {
            ctx.stderr.println("merge: $revision - not something we can merge")
            exitProcess(1)
        }

        when {
            inputs.alreadyMerged() -> handleMergedAncestor()
            inputs.fastForward() -> handleFastForward(inputs)
            else -> {
                resolveMerge(inputs)
                commitMerge(inputs)
                exitProcess(0)
            }
        }
    }

    private fun resolveMerge(inputs: Inputs) {
        repository.index.loadForUpdate {
            Resolve(repository, inputs).execute(this)
            writeUpdates()
        }
    }

    private fun commitMerge(inputs: Inputs) {
        val message = ctx.stdin.readAllBytes()
        if (message.isEmpty()) {
            ctx.stderr.println("gikt: empty commit message")
            exitProcess(1)
        }

        val index = repository.index.load()
        val parents = listOf(inputs.leftOid, inputs.rightOid)
        writeCommit.writeCommit(index, parents, message)
    }

    private fun handleMergedAncestor(): Nothing {
        println("Already up to date.")
        exitProcess(0)
    }

    private fun handleFastForward(inputs: Inputs): Nothing {
        val a = inputs.leftOid.short
        val b = inputs.rightOid.short

        println("Updating $a..$b")
        println("Fast-forward")

        repository.index.loadForUpdate {
            val treeDiff = repository.database.treeDiff(inputs.leftOid, inputs.rightOid)
            repository.migration(treeDiff).applyChanges(this)
            writeUpdates()
        }
        repository.refs.updateHead(inputs.rightOid)

        exitProcess(0)
    }
}
