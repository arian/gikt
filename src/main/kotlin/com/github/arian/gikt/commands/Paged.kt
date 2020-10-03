package com.github.arian.gikt.commands

import kotlinx.cli.ArgType
import kotlinx.cli.default

class Paged(ctx: CommandContext, name: String) : AbstractCommand(ctx, name) {

    private val times by option(ArgType.Int).default(200)

    override fun run() = (1..times).forEach { println("hello $it") }
}
