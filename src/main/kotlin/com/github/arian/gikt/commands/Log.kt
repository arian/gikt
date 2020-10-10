package com.github.arian.gikt.commands

import com.github.arian.gikt.commands.util.Style
import com.github.arian.gikt.database.Commit
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.utf8
import kotlinx.cli.ArgType
import kotlinx.cli.default

class Log(ctx: CommandContext, name: String) : AbstractCommand(ctx, name) {

    private enum class Format {
        MEDIUM,
        ONELINE
    }

    private val options = Options()

    override fun run() {
        eachCommit()
            .takeWhile { shouldContinuePrinting() }
            .forEachIndexed { index, commit -> println(showCommit(commit, index)) }
    }

    private fun eachCommit(): Sequence<Commit> {
        val head = repository.refs.readHead()
        return generateSequence(loadCommit(head)) { loadCommit(it.parent) }
    }

    private fun loadCommit(oid: ObjectId?): Commit? =
        oid?.let { repository.loadObject(it) as? Commit }

    private fun showCommit(commit: Commit, index: Int): String {
        return when (options.formatOption) {
            Format.MEDIUM -> formatMedium(commit, index)
            Format.ONELINE -> formatOneline(commit)
        }
    }

    private fun formatMedium(commit: Commit, index: Int): String {
        val author = commit.author

        val space = if (index != 0) {
            "\n"
        } else {
            ""
        }

        val log =
            """
            |${fmt(Style.YELLOW, "commit ${abbrev(commit.oid)}")}
            |Author: ${author.name} <${author.email}>
            |Date:   ${author.readableTime}
            |
            |${commit.message.utf8().trimEnd().prependIndent()}
            """.trimMargin()

        return space + log
    }

    private fun formatOneline(commit: Commit): String {
        return "${fmt(Style.YELLOW, abbrev(commit.oid))} ${commit.title}"
    }

    private fun abbrev(oid: ObjectId): String {
        return if (options.isAbbrevCommitOption) {
            oid.short
        } else {
            oid.hex
        }
    }

    private inner class Options {
        private val abbrevCommit by option(
            ArgType.Boolean,
            fullName = "abbrev-commit",
            description = "Instead of showing the full 40-byte hexadecimal commit object name, " +
                "show only a partial prefix."
        )

        private val noAbbrevCommit by option(
            ArgType.Boolean,
            fullName = "no-abbrev-commit",
            description = "Show the full 40-byte hexadecimal commit object name."
        )

        private val format by option(
            ArgType.Choice<Format>(),
            description = "Pretty-print the contents of the commit logs in a given format."
        ).default(Format.MEDIUM)

        private val oneline by option(
            ArgType.Boolean,
            description = "This is a shorthand for \"--format=oneline --abbrev-commit\" used together"
        )

        val isAbbrevCommitOption: Boolean get() =
            ((abbrevCommit == true || oneline == true) && noAbbrevCommit != false)

        val formatOption: Format get() =
            when (oneline) {
                true -> Format.ONELINE
                else -> format
            }
    }
}
