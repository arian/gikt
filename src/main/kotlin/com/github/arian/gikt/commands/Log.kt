package com.github.arian.gikt.commands

import com.github.arian.gikt.Refs.Ref.SymRef
import com.github.arian.gikt.commands.util.Style
import com.github.arian.gikt.database.Commit
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.utf8
import kotlinx.cli.ArgType
import kotlinx.cli.default

class Log(ctx: CommandContext, name: String) : AbstractCommand(ctx, name) {

    private val options = Options()

    override fun run() {
        val reverseRefs = repository.refs.reverseRefs()
        val currentRef = repository.refs.currentRef()

        eachCommit()
            .takeWhile { shouldContinuePrinting() }
            .forEachIndexed { index, commit ->
                println(
                    showCommit(
                        refs = reverseRefs.getOrDefault(commit.oid, emptyList()),
                        currentRef = currentRef,
                        commit,
                        index
                    )
                )
            }
    }

    private fun eachCommit(): Sequence<Commit> {
        val head = repository.refs.readHead()
        return generateSequence(loadCommit(head)) { loadCommit(it.parent) }
    }

    private fun loadCommit(oid: ObjectId?): Commit? =
        oid?.let { repository.loadObject(it) as? Commit }

    private fun showCommit(
        refs: List<SymRef>,
        currentRef: SymRef,
        commit: Commit,
        index: Int
    ): String {
        return when (options.formatOption) {
            Format.MEDIUM -> formatMedium(refs, currentRef, commit, index)
            Format.ONELINE -> formatOneline(refs, currentRef, commit)
        }
    }

    private fun formatMedium(
        refs: List<SymRef>,
        currentRef: SymRef,
        commit: Commit,
        index: Int
    ): String {
        val author = commit.author

        val space = if (index != 0) {
            "\n"
        } else {
            ""
        }

        val log =
            """
            |${fmt(Style.YELLOW, "commit ${abbrev(commit.oid) + decorate(refs, currentRef)}")}
            |Author: ${author.name} <${author.email}>
            |Date:   ${author.readableTime}
            |
            |${commit.message.utf8().trimEnd().prependIndent()}
            """.trimMargin()

        return space + log
    }

    private fun formatOneline(
        refs: List<SymRef>,
        currentRef: SymRef,
        commit: Commit
    ): String {
        val id = fmt(Style.YELLOW, abbrev(commit.oid)) + decorate(refs, currentRef)
        return "$id ${commit.title}"
    }

    private fun abbrev(oid: ObjectId): String {
        return if (options.isAbbrevCommitOption) {
            oid.short
        } else {
            oid.hex
        }
    }

    private fun decorate(refs: List<SymRef>, currentRef: SymRef): String {
        fun refColor(ref: SymRef) =
            when {
                ref.isHead -> listOf(Style.BOLD, Style.CYAN)
                else -> listOf(Style.BOLD, Style.GREEN)
            }

        fun List<SymRef>.sortCurrentRefFirst() =
            filter { it == currentRef } + filterNot { it == currentRef }

        fun List<SymRef>.refsToString(isShort: Boolean, head: SymRef?): String =
            joinToString(separator = fmt(Style.YELLOW, ", ")) { ref ->
                val name = if (isShort) {
                    ref.shortName
                } else {
                    ref.longName
                }

                val styled = fmt(refColor(ref), name)

                if (head != null && ref == currentRef) {
                    fmt(refColor(head), "${head.shortName} -> $styled")
                } else {
                    styled
                }
            }

        fun decorate(isShort: Boolean): String {
            if (refs.isEmpty()) {
                return ""
            }

            val (heads, notHeadRefs) = refs.partition { it.isHead && !currentRef.isHead }
            val head = heads.firstOrNull()

            val names = notHeadRefs
                .sortCurrentRefFirst()
                .refsToString(isShort = isShort, head = head)

            return fmt(Style.YELLOW, " (") + names + fmt(Style.YELLOW, ")")
        }

        return when (options.decorateOption) {
            Decoration.AUTO -> {
                if (ctx.isatty) {
                    decorate(isShort = true)
                } else {
                    ""
                }
            }
            Decoration.NO -> return ""
            Decoration.SHORT -> decorate(isShort = true)
            Decoration.FULL -> decorate(isShort = false)
        }
    }

    private enum class Format {
        MEDIUM,
        ONELINE
    }

    private enum class Decoration {
        SHORT,
        FULL,
        AUTO,
        NO
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
            description =
                """This is a shorthand for "--format=oneline --abbrev-commit" used together"""
        )

        val isAbbrevCommitOption: Boolean
            get() =
                ((abbrevCommit == true || oneline == true) && noAbbrevCommit != false)

        val formatOption: Format
            get() =
                when (oneline) {
                    true -> Format.ONELINE
                    else -> format
                }

        private val decorate by option(
            ArgType.Choice<Decoration>(),
            description =
                """Print out the ref names of any commits that are shown."""
        ).default(Decoration.AUTO)

        private val noDecorate by option(
            ArgType.Boolean
        )

        val decorateOption
            get() = when (noDecorate) {
                true -> Decoration.NO
                else -> decorate
            }
    }
}
