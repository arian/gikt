package com.github.arian.gikt.commands

import com.github.arian.gikt.Repository
import com.github.arian.gikt.database.Commit
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.database.Tree
import com.github.arian.gikt.relativeTo
import java.nio.file.Path

class ShowHead(ctx: CommandContext) : AbstractCommand(ctx) {
    override fun run() {
        val repository = Repository(ctx.dir)

        val head = repository.refs.readHead()
        if (head != null) {
            when (val commit = repository.database.load(rel(ctx.dir), head)) {
                is Commit -> showTree(commit.tree, rel(ctx.dir))
            }
        }
    }

    private fun rel(path: Path) = path.relativeTo(path)

    private fun showTree(oid: ObjectId, prefix: Path) {
        when (val tree = repository.database.load(rel(prefix), oid)) {
            is Tree -> {
                tree.list().forEach { entry ->
                    val path = prefix.resolve(entry.name)
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
