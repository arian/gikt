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

        val parent = repository.refs.readHead()
        val commit = writeCommit.writeCommit(listOfNotNull(parent), message)

        val isRoot = parent?.let { "" } ?: "(root-commit) "
        println("[$isRoot${commit.oid.hex}] ${commit.title}")
        exitProcess(0)
    }
}
