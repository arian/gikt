package com.github.arian.gikt.commands

import com.github.arian.gikt.Refs
import com.github.arian.gikt.mkdirp

class Init(ctx: CommandContext, name: String) : AbstractCommand(ctx, name) {

    companion object {
        private const val DEFAULT_BRANCH = "main"
    }

    override fun run() {
        val gitPath = ctx.dir.resolve(".git")

        listOf("objects", "refs/heads").forEach {
            val path = gitPath.resolve(it)
            try {
                path.mkdirp()
            } catch (e: Exception) {
                ctx.stderr.println("fatal: ${e.message}")
                exitProcess(1)
            }
        }

        val refs = Refs(gitPath)
        refs.updateHead("ref: refs/heads/$DEFAULT_BRANCH")

        ctx.stdout.println("Initialized empty Gikt repository in ${ctx.dir}")
    }
}
