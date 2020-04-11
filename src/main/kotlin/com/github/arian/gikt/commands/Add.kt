package com.github.arian.gikt.commands

import com.github.arian.gikt.Lockfile
import com.github.arian.gikt.Repository
import com.github.arian.gikt.Workspace
import com.github.arian.gikt.database.Blob
import java.nio.file.Path

private val LOCKED_INDEX_MESSAGE = """
    Another gikt process seems to be running in this repository.
    Please make sure all processes are terminated then try again.
    If it still fails, a gikt process may have crashed in this
    repository earlier: remove the file manually to continue.
    """.trimIndent()

class Add(ctx: CommandContext) : AbstractCommand(ctx) {

    override fun run() {
        kotlin
            .runCatching { updateIndex() }
            .getOrElse { handleException(it) }

        exitProcess(0)
    }

    private fun updateIndex() {
        repository.index.loadForUpdate { lock ->
            kotlin
                .runCatching { updateIndexLocked(lock) }
                .getOrElse { handleException(it, lock) }
        }
    }

    private fun updateIndexLocked(lock: Lockfile.Ref) {
        expandedPaths(repository).forEach { addToIndex(repository, it) }
        repository.index.writeUpdates(lock)
    }

    private fun expandedPaths(repository: Repository): List<Path> =
        ctx.args.flatMap {
            repository.workspace.listFiles(repository.resolvePath(it))
        }

    private fun addToIndex(repository: Repository, path: Path) {
        val data = repository.workspace.readFile(path)
        val stat = repository.workspace.statFile(path)

        val blob = Blob(data)
        repository.database.store(blob)
        repository.index.add(path, blob.oid, stat)
    }

    private fun handleException(e: Throwable, lock: Lockfile.Ref? = null): Nothing {
        when (e) {
            is Workspace.MissingFile -> {
                ctx.stderr.println("fatal: ${e.message}")
                lock?.rollback()
                exitProcess(128)
            }
            is Workspace.NoPermission -> {
                ctx.stderr.println("error: ${e.message}")
                ctx.stderr.println("fatal: adding files failed")
                lock?.rollback()
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
