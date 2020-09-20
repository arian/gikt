package com.github.arian.gikt.commands

class Help(ctx: CommandContext, name: String) : AbstractCommand(ctx, name) {
    override fun run() {
        println("gikt: hello")
    }
}
