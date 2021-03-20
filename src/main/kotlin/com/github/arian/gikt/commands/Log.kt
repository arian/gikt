package com.github.arian.gikt.commands

import com.github.arian.gikt.Refs.Ref.SymRef
import com.github.arian.gikt.RevList
import com.github.arian.gikt.commands.util.PrintDiff
import com.github.arian.gikt.commands.util.Style
import com.github.arian.gikt.database.Commit
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.database.TreeDiffMapValue
import com.github.arian.gikt.database.TreeEntry
import com.github.arian.gikt.utf8
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.optional
import kotlinx.cli.vararg

class Log(ctx: CommandContext, name: String) : AbstractCommand(ctx, name) {

    private val options = Options(cli)
    private val printDiffOptions = PrintDiff.Options(cli, default = false)
    private val printDiff = PrintDiff(fmt = ::fmt)

    override fun run() {
        val reverseRefs = repository.refs.reverseRefs()
        val currentRef = repository.refs.currentRef()
        val start = RevList.parseStartPoints(repository, options.start)

        val revList = RevList(repository, start)

        val items = if (printDiffOptions.patch) {
            revList.itemsWithPatches()
        } else {
            revList.items()
        }

        items
            .takeWhile { shouldContinuePrinting() }
            .forEachIndexed { index, revListItem ->
                println(
                    showCommit(
                        refs = reverseRefs.getOrDefault(revListItem.oid, emptyList()),
                        currentRef = currentRef,
                        revListItem,
                        index
                    )
                )
            }
    }

    private fun showCommit(
        refs: List<SymRef>,
        currentRef: SymRef,
        item: RevList.Item,
        index: Int
    ): String {
        return when (options.format) {
            Format.MEDIUM -> formatMedium(refs, currentRef, item.commit, index) + showPatch(item, blankline = "\n")
            Format.ONELINE -> formatOneline(refs, currentRef, item.commit) + showPatch(item)
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
        return if (options.isAbbrevCommit) {
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

        return when (options.decorate) {
            Decoration.AUTO -> {
                if (ctx.isatty) {
                    decorate(isShort = true)
                } else {
                    ""
                }
            }
            Decoration.NO -> ""
            Decoration.SHORT -> decorate(isShort = true)
            Decoration.FULL -> decorate(isShort = false)
        }
    }

    private fun showPatch(item: RevList.Item, blankline: String = ""): String {
        if (item !is RevList.Item.CommitWithPatch || item.commit.parents.size > 1) {
            return ""
        }

        fun fromNothing(path: String): PrintDiff.Target =
            PrintDiff.Target.fromNothing(path)

        fun fromRepo(entry: TreeEntry): PrintDiff.Target =
            PrintDiff.Target.fromHead(entry, repository.loadObject(entry.oid))

        val diffTree = item.patch

        val diff = diffTree
            .keys
            .sortedBy { it.toString() }
            .mapNotNull { diffTree[it] }
            .joinToString(separator = "\n") {
                when (it) {
                    is TreeDiffMapValue.Change -> printDiff.diff(fromRepo(it.old), fromRepo(it.new))
                    is TreeDiffMapValue.Addition -> printDiff.diff(fromNothing(it.path.toString()), fromRepo(it.new))
                    is TreeDiffMapValue.Deletion -> printDiff.diff(fromRepo(it.old), fromNothing(it.path.toString()))
                }
            }

        return "$blankline\n$diff"
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

    private class Options(cli: Cli) {

        val start by cli
            .argument(
                ArgType.String,
                fullName = "start"
            )
            .optional()
            .vararg()

        private val abbrevCommitOption by cli.option(
            ArgType.Boolean,
            fullName = "abbrev-commit",
            description = "Instead of showing the full 40-byte hexadecimal commit object name, " +
                "show only a partial prefix."
        )

        private val noAbbrevCommit by cli.option(
            ArgType.Boolean,
            fullName = "no-abbrev-commit",
            description = "Show the full 40-byte hexadecimal commit object name."
        )

        private val formatOption by cli.option(
            ArgType.Choice<Format>(),
            fullName = "format",
            description = "Pretty-print the contents of the commit logs in a given format."
        ).default(Format.MEDIUM)

        private val oneline by cli.option(
            ArgType.Boolean,
            fullName = "oneline",
            description = """This is a shorthand for "--format=oneline --abbrev-commit" used together"""
        )

        val isAbbrevCommit: Boolean
            get() =
                ((abbrevCommitOption == true || oneline == true) && noAbbrevCommit != false)

        val format: Format
            get() =
                when (oneline) {
                    true -> Format.ONELINE
                    else -> formatOption
                }

        private val decorateOption by cli.option(
            ArgType.Choice<Decoration>(),
            fullName = "decorate",
            description = """Print out the ref names of any commits that are shown."""
        ).default(Decoration.AUTO)

        private val noDecorate by cli.option(
            ArgType.Boolean,
            fullName = "no-decorate"
        )

        val decorate
            get() = when (noDecorate) {
                true -> Decoration.NO
                else -> decorateOption
            }
    }
}
