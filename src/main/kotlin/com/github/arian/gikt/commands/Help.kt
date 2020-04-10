package com.github.arian.gikt.commands

class Help(ctx: CommandContext) : AbstractCommand(ctx) {
    override fun run() {
        println("gikt: hello")
    }
}
