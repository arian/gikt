package com.github.arian.gikt.commands

import com.github.arian.gikt.Repository
import com.github.arian.gikt.database.Author
import com.github.arian.gikt.database.Commit
import com.github.arian.gikt.database.Entry
import java.time.Instant

class Commit(ctx: CommandContext) : AbstractCommand(ctx) {
    override fun run() {
        val name = ctx.env("GIT_AUTHOR_NAME") ?: error("please set GIT_AUTHOR_NAME")
        val email = ctx.env("GIT_AUTHOR_EMAIL") ?: error("please set GIT_AUTHOR_EMAIL")
        val author = Author(
            name,
            email,
            Instant.now(ctx.clock).atZone(ctx.clock.zone)
        )
        val message: ByteArray = ctx.stdin.readAllBytes()
        val firstLine = message.toString(Charsets.UTF_8).split("\n").getOrNull(0) ?: ""

        if (firstLine.isBlank()) {
            ctx.stderr.println("gikt: empty commit message")
            exitProcess(1)
        }

        val repository = Repository(ctx.dir)

        val entries = repository.index.load().toList().map {
            val path = repository.relativePath(repository.resolvePath(it.key))
            Entry(path, it.stat, it.oid)
        }

        val root = repository.buildTree(entries)
        root.traverse { repository.database.store(it) }

        val parent = repository.refs.readHead()
        val commit = Commit(parent, root.oid, author, message)
        repository.database.store(commit)
        repository.refs.updateHead(commit.oid)

        val isRoot = parent?.let { "" } ?: "(root-commit) "
        println("[$isRoot${commit.oid.hex} $firstLine")
        exitProcess(0)
    }
}
