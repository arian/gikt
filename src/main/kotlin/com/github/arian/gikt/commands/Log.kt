package com.github.arian.gikt.commands

import com.github.arian.gikt.commands.util.Style
import com.github.arian.gikt.database.Commit
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.utf8

class Log(ctx: CommandContext, name: String) : AbstractCommand(ctx, name) {

    override fun run() {
        eachCommit()
            .takeWhile { shouldContinuePrinting() }
            .forEachIndexed { index, commit -> showCommit(commit, index) }
    }

    private fun eachCommit(): Sequence<Commit> {
        val head = repository.refs.readHead()
        return generateSequence(loadCommit(head)) { loadCommit(it.parent) }
    }

    private fun loadCommit(oid: ObjectId?): Commit? =
        oid?.let { repository.loadObject(it) as? Commit }

    private fun showCommit(commit: Commit, index: Int) {
        if (index != 0) {
            println("")
        }

        val author = commit.author

        println(fmt(Style.YELLOW, "commit ${commit.oid}"))
        println(
            """
            |Author: ${author.name} <${author.email}>
            |Date:   ${author.readableTime}
            |
            |${commit.message.utf8().trimEnd().prependIndent()}
            """.trimMargin()
        )
    }
}
