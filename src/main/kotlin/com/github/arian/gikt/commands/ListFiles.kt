package com.github.arian.gikt.commands

import com.github.arian.gikt.Repository

class ListFiles(ctx: CommandContext) : AbstractCommand(ctx) {
    override fun run() {
        val repository = Repository(ctx.dir)
        repository.index.load().forEach { println(it.key) }
    }
}
