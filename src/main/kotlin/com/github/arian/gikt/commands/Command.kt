package com.github.arian.gikt.commands

import com.github.arian.gikt.commands.util.Pager
import com.github.arian.gikt.commands.util.Style
import com.github.arian.gikt.repository.Repository
import java.io.InputStream
import java.io.PrintStream
import java.nio.file.Path
import java.time.Clock

typealias Environment = (key: String) -> String?
typealias CommandFactory = (ctx: CommandContext) -> Command

data class CommandContext(
    val dir: Path,
    val env: Environment,
    val args: List<String>,
    val stdin: InputStream,
    val stdout: PrintStream,
    val stderr: PrintStream,
    val isatty: Boolean = false,
    val clock: Clock
)

data class CommandExecution(
    val ctx: CommandContext,
    val status: Int
)

interface Command {
    fun execute(): CommandExecution
}

abstract class AbstractCommand(val ctx: CommandContext) : Command {

    internal val repository by lazy { Repository(ctx.dir) }

    internal abstract fun run()

    override fun execute(): CommandExecution {
        val status = try {
            run()
            0
        } catch (exit: Commands.Exit) {
            exit.code
        }
        return CommandExecution(ctx, status)
    }

    fun exitProcess(code: Int = 0): Nothing {
        throw Commands.Exit(code)
    }

    fun println(msg: String) =
        ctx.stdout.println(msg)

    fun fmt(style: Style, string: String) =
        if (ctx.isatty) {
            style.format(string)
        } else {
            string
        }
}

class PagerCommand(private val ctx: CommandContext, private val subCmdFactory: CommandFactory) : Command {
    override fun execute(): CommandExecution =
        when (ctx.isatty) {
            true -> Pager().start { pagerOut -> subCmdFactory(ctx.copy(stdout = pagerOut)).execute() }
            false -> subCmdFactory(ctx).execute()
        }
}

object Commands {

    private val commands: Map<String, CommandFactory> = mapOf(
        "add" to cmd { Add(it) },
        "branch" to cmd { Branch(it) },
        "commit" to cmd { Commit(it) },
        "diff" to withPager { Diff(it) },
        "help" to cmd { Help(it) },
        "init" to cmd { Init(it) },
        "ls-files" to cmd { ListFiles(it) },
        "paged" to withPager { Paged(it) },
        "show-head" to cmd { ShowHead(it) },
        "status" to cmd { Status(it) }
    )

    private fun cmd(fn: CommandFactory): CommandFactory = fn

    private fun withPager(fn: CommandFactory): CommandFactory =
        { ctx -> PagerCommand(ctx, fn) }

    fun execute(name: String, ctx: CommandContext): CommandExecution =
        commands[name]
            ?.invoke(ctx)
            ?.execute()
            ?: CommandExecution(ctx, 1).also { ctx.stderr.println("'$name' is not a gikt command.") }

    class Exit(val code: Int) : Exception("Process exit with $code")
}
