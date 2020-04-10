package com.github.arian.gikt.commands

import com.github.arian.gikt.mkdirp

class Init(ctx: CommandContext) : AbstractCommand(ctx) {
    override fun run() {
        val gitPath = ctx.dir.resolve(".git")

        listOf("objects", "refs").forEach {
            val path = gitPath.resolve(it)
            try {
                path.mkdirp()
            } catch (e: Exception) {
                ctx.stderr.println("fatal: ${e.message}")
                exitProcess(1)
            }
        }

        ctx.stdout.println("Initialized empty Gikt repository in ${ctx.dir}")
    }
}
