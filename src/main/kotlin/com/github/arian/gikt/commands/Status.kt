package com.github.arian.gikt.commands

class Status(ctx: CommandContext) : AbstractCommand(ctx) {
    override fun run() {
        val loadedIndex = repository.index.load()

        repository.workspace.listFiles()
            .filterNot { loadedIndex.tracked(it) }
            .sorted()
            .forEach {
                println("?? $it")
            }

        exitProcess(0)
    }
}
