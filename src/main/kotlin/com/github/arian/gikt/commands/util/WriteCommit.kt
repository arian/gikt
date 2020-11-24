package com.github.arian.gikt.commands.util

import com.github.arian.gikt.commands.CommandContext
import com.github.arian.gikt.database.Author
import com.github.arian.gikt.database.Commit
import com.github.arian.gikt.database.Entry
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.database.Tree
import com.github.arian.gikt.repository.Repository
import java.time.Instant

class WriteCommit(
    private val ctx: CommandContext,
    private val repository: Repository,
) {

    fun writeCommit(parents: List<ObjectId>, message: ByteArray): Commit {
        val name = ctx.env("GIT_AUTHOR_NAME") ?: error("please set GIT_AUTHOR_NAME")
        val email = ctx.env("GIT_AUTHOR_EMAIL") ?: error("please set GIT_AUTHOR_EMAIL")
        val author = Author(name, email, Instant.now(ctx.clock).atZone(ctx.clock.zone))
        val tree = writeTree()

        return Commit(parents, tree.oid, author, message).also { commit ->
            repository.database.store(commit)
            repository.refs.updateHead(commit.oid)
        }
    }

    private fun writeTree(): Tree {
        val entries = repository.index.load().toList().map {
            val path = repository.relativePath(repository.resolvePath(it.key))
            Entry(path, it.stat, it.oid)
        }
        return repository.buildTree(entries).also { tree ->
            tree.traverse { repository.database.store(it) }
        }
    }
}
