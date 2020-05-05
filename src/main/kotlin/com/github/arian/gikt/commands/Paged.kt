package com.github.arian.gikt.commands

class Paged(ctx: CommandContext) : AbstractCommand(ctx) {
    override fun run() = (1..200).forEach { println("hello $it") }
}
