package com.github.arian.gikt.commands

import com.github.arian.gikt.repository.Repository

class ListFiles(ctx: CommandContext, name: String) : AbstractCommand(ctx, name) {
    override fun run() {
        val repository = Repository(ctx.dir)
        repository.index.load().forEach { println(it.key) }
    }
}
