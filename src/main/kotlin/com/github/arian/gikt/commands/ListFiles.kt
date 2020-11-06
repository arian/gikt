package com.github.arian.gikt.commands

class ListFiles(ctx: CommandContext, name: String) : AbstractCommand(ctx, name) {
    override fun run() {
        repository.index.load().forEach { println(it.key) }
    }
}
