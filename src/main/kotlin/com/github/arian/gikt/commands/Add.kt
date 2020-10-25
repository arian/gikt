package com.github.arian.gikt.commands

import com.github.arian.gikt.Lockfile
import com.github.arian.gikt.Workspace
import com.github.arian.gikt.database.Blob
import com.github.arian.gikt.index.Index
import com.github.arian.gikt.repository.Repository
import kotlinx.cli.ArgType
import kotlinx.cli.vararg
import java.nio.file.Path

private val LOCKED_INDEX_MESSAGE =
    """
    Another gikt process seems to be running in this repository.
    Please make sure all processes are terminated then try again.
    If it still fails, a gikt process may have crashed in this
    repository earlier: remove the file manually to continue.
    """.trimIndent()

class Add(ctx: CommandContext, name: String) : AbstractCommand(ctx, name) {

    private val files: List<String> by cli.argument(ArgType.String).vararg()

    override fun run() {
        kotlin
            .runCatching { updateIndex() }
            .getOrElse { handleException(it) }

        exitProcess(0)
    }

    private fun updateIndex() {
        repository.index.loadForUpdate {
            kotlin
                .runCatching { updateIndexLocked(this) }
                .getOrElse { handleException(it, this) }
        }
    }

    private fun updateIndexLocked(index: Index.Updater) {
        expandedPaths(repository).forEach { addToIndex(index, it) }
        index.writeUpdates()
    }

    private fun expandedPaths(repository: Repository): List<Path> =
        files.flatMap {
            repository.workspace.listFiles(repository.resolvePath(it))
        }

    private fun addToIndex(index: Index.Updater, path: Path) {
        val data = repository.workspace.readFile(path)
        val stat = repository.workspace.statFile(path)

        val blob = Blob(data)
        repository.database.store(blob)
        index.add(path, blob.oid, stat)
    }

    private fun handleException(e: Throwable, index: Index.Updater? = null): Nothing {
        when (e) {
            is Workspace.MissingFile -> {
                ctx.stderr.println("fatal: ${e.message}")
                index?.rollback()
                exitProcess(128)
            }
            is Workspace.NoPermission -> {
                ctx.stderr.println("error: ${e.message}")
                ctx.stderr.println("fatal: adding files failed")
                index?.rollback()
                exitProcess(128)
            }
            is Lockfile.LockDenied -> {
                ctx.stderr.println("fatal: ${e.message}\n\n$LOCKED_INDEX_MESSAGE")
                exitProcess(128)
            }
            else -> throw e
        }
    }
}
