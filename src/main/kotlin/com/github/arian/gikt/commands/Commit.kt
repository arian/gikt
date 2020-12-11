package com.github.arian.gikt.commands

import com.github.arian.gikt.commands.util.WriteCommit

class Commit(ctx: CommandContext, name: String) : AbstractCommand(ctx, name) {

    private val writeCommit = WriteCommit(ctx, repository)

    override fun run() {

        val message: ByteArray = ctx.stdin.readAllBytes()

        if (message.isEmpty()) {
            ctx.stderr.println("gikt: empty commit message")
            exitProcess(1)
        }

        val index = repository.index.load()
        val status = repository.status().scan(index)

        if (status.changes.indexChanges().isEmpty()) {
            ctx.stderr.println(
                when {
                    status.changes.workspaceChanges().isNotEmpty() -> "no changes added to commit"
                    status.untracked.isNotEmpty() -> "nothing added to commit but untracked files present"
                    else -> "nothing to commit, working tree clean"
                }
            )
            exitProcess(1)
        }

        val parent = repository.refs.readHead()
        val commit = writeCommit.writeCommit(index, listOfNotNull(parent), message)
        val isRoot = parent?.let { "" } ?: "(root-commit) "
        println("[$isRoot${commit.oid.hex}] ${commit.title}")
        exitProcess(0)
    }
}
