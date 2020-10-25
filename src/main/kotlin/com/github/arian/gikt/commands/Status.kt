package com.github.arian.gikt.commands

import com.github.arian.gikt.commands.util.Style
import com.github.arian.gikt.repository.Status
import kotlinx.cli.ArgType
import kotlinx.cli.default

class Status(ctx: CommandContext, name: String) : AbstractCommand(ctx, name) {

    private val porcelain: Boolean by cli.option(ArgType.Boolean).default(false)
    private val format: String
        get() = when (porcelain) {
            true -> "porcelain"
            false -> "long"
        }

    override fun run() {
        val scan = repository.index.loadForUpdate {
            repository.status().scan(this).also { writeUpdates() }
        }

        when (format) {
            "porcelain" -> printResults(scan)
            else -> printLongFormat(scan)
        }

        exitProcess(0)
    }

    private fun printResults(scan: Status.Scan) {
        (scan.changes.all())
            .forEach { println("${statusFor(scan, it)} $it") }

        scan.untracked
            .toSortedSet()
            .forEach { println("?? $it") }
    }

    private fun statusFor(scan: Status.Scan, key: String): String {
        val left = scan.changes.indexChange(key)?.changeType?.short ?: " "
        val right = scan.changes.workspaceChange(key)?.changeType?.short ?: " "
        return "$left$right"
    }

    private fun printLongFormat(scan: Status.Scan) {
        printChanges(
            "Changes to be committed",
            scan.changes.indexChanges(),
            Style.GREEN,
            type = { longStatus(it.changeType).padEnd(12) },
            name = { it.key }
        )

        printChanges(
            "Changes not staged for commit",
            scan.changes.workspaceChanges(),
            Style.RED,
            type = { longStatus(it.changeType).padEnd(12) },
            name = { it.key }
        )

        printChanges(
            "Untracked files",
            scan.untracked,
            Style.RED,
            type = { "" },
            name = { it }
        )

        printCommitStatus(scan)
    }

    private fun longStatus(status: Status.ChangeType?): String {
        return when (status) {
            Status.ChangeType.ADDED -> "new file:"
            Status.ChangeType.DELETED -> "deleted:"
            Status.ChangeType.MODIFIED -> "modified:"
            null -> ""
        }
    }

    private fun <T> printChanges(
        message: String,
        changeset: Set<T>,
        style: Style,
        type: (T) -> String,
        name: (T) -> String
    ) {

        if (changeset.isEmpty()) {
            return
        }

        println("$message:")
        println("")

        changeset.forEach {
            val status = type(it)
            println("\t" + fmt(style, status + name(it)))
        }

        println("")
    }

    private fun printCommitStatus(scan: Status.Scan) {
        when {
            scan.changes.indexChanges().isNotEmpty() -> return
            scan.changes.workspaceChanges().isNotEmpty() -> println("no changes added to commit")
            scan.untracked.isNotEmpty() -> println("nothing added to commit but untracked files present")
            else -> println("nothing to commit, working tree clean")
        }
    }
}
