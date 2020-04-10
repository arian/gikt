package com.github.arian.gikt.commands

import java.io.InputStream
import java.io.PrintStream
import java.nio.file.Path
import java.time.Clock

typealias Environment = (key: String) -> String?

data class CommandContext(
    val dir: Path,
    val env: Environment,
    val args: List<String>,
    val stdin: InputStream,
    val stdout: PrintStream,
    val stderr: PrintStream,
    val clock: Clock
)

abstract class AbstractCommand(val ctx: CommandContext) {
    var status: Int = 0

    internal abstract fun run()

    fun execute() {
        try {
            run()
        } catch (exit: Command.Exit) {
            status = exit.code
        }
    }

    fun exitProcess(code: Int = 0): Nothing {
        status = code
        throw Command.Exit(code)
    }

    fun println(msg: String) =
        ctx.stdout.println(msg)
}

object Command {

    private val commands = mapOf<String, (CommandContext) -> AbstractCommand>(
        "init" to { ctx: CommandContext -> Init(ctx) },
        "add" to { ctx: CommandContext -> Add(ctx) },
        "commit" to { ctx: CommandContext -> Commit(ctx) },
        "ls-files" to { ctx: CommandContext -> ListFiles(ctx) },
        "help" to { ctx: CommandContext -> Help(ctx) }
    )

    fun execute(name: String, ctx: CommandContext): AbstractCommand {

        val command = commands[name]?.invoke(ctx)
            ?: throw Unknown("'$name' is not a gikt command.")

        command.execute()

        return command
    }

    class Unknown(msg: String) : Exception(msg)

    class Exit(val code: Int) : Exception("Process exit with $code")
}
