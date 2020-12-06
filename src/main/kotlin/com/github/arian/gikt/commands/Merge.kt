package com.github.arian.gikt.commands

import com.github.arian.gikt.Revision
import com.github.arian.gikt.commands.util.WriteCommit
import com.github.arian.gikt.merge.Bases
import kotlinx.cli.ArgType

class Merge(ctx: CommandContext, name: String) : AbstractCommand(ctx, name) {

    private val revision by cli.argument(ArgType.String)
    private val writeCommit = WriteCommit(ctx, repository)

    override fun run(): Nothing {

        val message = ctx.stdin.readAllBytes()
        if (message.isEmpty()) {
            ctx.stderr.println("gikt: empty commit message")
            exitProcess(1)
        }

        val mergeOid = try {
            Revision(repository, revision).oid
        } catch (e: Revision.InvalidObject) {
            ctx.stderr.println("merge: $revision - not something we can merge")
            exitProcess(1)
        }

        val headOid = repository.refs.readHead()
            ?: run {
                println("fatal: not a git repository: .git")
                exitProcess(128)
            }

        val baseOid = Bases(repository.database, headOid, mergeOid).find().firstOrNull()

        repository.index.loadForUpdate {
            val treeDiff = repository.database.treeDiff(baseOid, mergeOid)
            val migration = repository.migration(treeDiff)
            migration.applyChanges(this)
            writeUpdates()
        }

        writeCommit.writeCommit(listOf(headOid, mergeOid), message)
        exitProcess(0)
    }
}
