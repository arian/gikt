package com.github.arian.gikt.commands

import com.github.arian.gikt.Lockfile
import com.github.arian.gikt.Repository
import com.github.arian.gikt.Workspace
import com.github.arian.gikt.database.Blob

class Add(ctx: CommandContext) : AbstractCommand(ctx) {

    override fun run() {
        val repository = Repository(ctx.dir)

        try {
            repository.index.loadForUpdate { lock -> updateIndex(repository, lock) }
        } catch (e: Lockfile.LockDenied) {
            ctx.stderr.println(
                """
                    fatal: ${e.message}

                    Another gikt process seems to be running in this repository.
                    Please make sure all processes are terminated then try again.
                    If it still fails, a gikt process may have crashed in this
                    repository earlier: remove the file manually to continue.
                """.trimIndent()
            )
            exitProcess(128)
        }

        exitProcess(0)
    }

    private fun updateIndex(repository: Repository, lock: Lockfile.Ref) {
        val paths = try {
            ctx.args
                .flatMap {
                    val path = repository.resolvePath(it)
                    repository.workspace.listFiles(path)
                }
        } catch (e: Workspace.MissingFile) {
            ctx.stderr.println("fatal: ${e.message}")
            lock.rollback()
            exitProcess(128)
        }

        try {
            paths.forEach { path ->
                val data = repository.workspace.readFile(path)
                val stat = repository.workspace.statFile(path)

                val blob = Blob(data)
                repository.database.store(blob)
                repository.index.add(path, blob.oid, stat)
            }
        } catch (e: Workspace.NoPermission) {
            ctx.stderr.println("error: ${e.message}")
            ctx.stderr.println("fatal: adding files failed")
            lock.rollback()
            exitProcess(128)
        }

        repository.index.writeUpdates(lock)
    }
}
