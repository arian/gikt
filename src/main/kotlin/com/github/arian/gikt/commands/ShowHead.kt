package com.github.arian.gikt.commands

import com.github.arian.gikt.database.Commit
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.database.Tree
import com.github.arian.gikt.repository.Repository
import java.nio.file.Path

class ShowHead(ctx: CommandContext) : AbstractCommand(ctx) {
    override fun run() {
        val repository = Repository(ctx.dir)

        val head = repository.refs.readHead()
        if (head != null) {
            val rel = repository.relativeRoot
            when (val commit = repository.database.load(head, rel)) {
                is Commit -> showTree(commit.tree, rel)
            }
        }
    }

    private fun showTree(oid: ObjectId, prefix: Path) {
        when (val tree = repository.database.load(oid, prefix)) {
            is Tree -> {
                tree.list().forEach { entry ->
                    val path = entry.name
                    if (entry.isTree()) {
                        showTree(entry.oid, path)
                    } else {
                        val mode = entry.mode.mode
                        println("$mode ${entry.oid.hex} $path")
                    }
                }
            }
        }
    }
}
