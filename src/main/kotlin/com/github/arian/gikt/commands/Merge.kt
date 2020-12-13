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

        val message = ctx.stdin.readAllBytes()
        if (message.isEmpty()) {
            ctx.stderr.println("gikt: empty commit message")
            exitProcess(1)
        }

        val inputs = try {
            Inputs(repository, leftName = Revision.HEAD, rightName = revision)
        } catch (e: Revision.InvalidObject) {
            ctx.stderr.println("merge: $revision - not something we can merge")
            exitProcess(1)
        }

        resolveMerge(inputs)
        commitMerge(inputs, message)

        exitProcess(0)
    }

    private fun resolveMerge(inputs: Inputs) {
        repository.index.loadForUpdate {
            Resolve(repository, inputs).execute(this)
            writeUpdates()
        }
    }

    private fun commitMerge(inputs: Inputs, message: ByteArray) {
        val index = repository.index.load()
        val parents = listOf(inputs.leftOid, inputs.rightOid)
        writeCommit.writeCommit(index, parents, message)
    }
}
