package com.github.arian.gikt.commands

import com.github.arian.gikt.commands.util.Pager
import com.github.arian.gikt.commands.util.Style
import com.github.arian.gikt.commands.util.format
import com.github.arian.gikt.repository.Repository
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.DefaultRequiredType
import kotlinx.cli.SingleArgument
import kotlinx.cli.SingleNullableOption
import java.io.InputStream
import java.io.PrintStream
import java.nio.file.Path
import java.time.Clock

typealias Environment = (key: String) -> String?
typealias CommandFactory = (ctx: CommandContext, name: String) -> Command

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

abstract class AbstractCommand(val ctx: CommandContext, val name: String) : Command {

    internal val repository by lazy { Repository(ctx.dir) }

    private val parser = ArgParser("gikt $name")

    fun <T : Any> option(
        type: ArgType<T>,
        fullName: String? = null,
        shortName: String? = null,
        description: String? = null,
    ): SingleNullableOption<T> =
        parser.option(type, fullName, shortName, description)

    fun <T : Any> argument(
        type: ArgType<T>,
        fullName: String? = null,
        description: String? = null,
    ): SingleArgument<T, DefaultRequiredType.Required> =
        parser.argument(type, fullName, description)

    internal abstract fun run()

    override fun execute(): CommandExecution {
        parser.parse(ctx.args.toTypedArray())
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

    fun fmt(styles: List<Style>, string: String) =
        if (ctx.isatty) {
            styles.format(string)
        } else {
            string
        }

    /**
     * This function checks if the [CommandContext.stdout] is still open and that it makes sense to write to it.
     * This is really helpful if the program is piped into another program, which doesn't need more data.
     *
     * And example of such a program is `head`:
     *
     * ```
     * $ gikt paged --times 10000000 | head
     * $ gikt log | grep "commit msg"
     * ```
     */
    fun shouldContinuePrinting(): Boolean =
        !ctx.stdout.checkError()
}

class PagerCommand(
    private val ctx: CommandContext,
    private val name: String,
    private val subCmdFactory: CommandFactory
) : Command {
    override fun execute(): CommandExecution =
        when (ctx.isatty) {
            true -> Pager().start(ctx.dir) { pagerOut -> subCmdFactory(ctx.copy(stdout = pagerOut), name).execute() }
            false -> subCmdFactory(ctx, name).execute()
        }
}

object Commands {

    private val commands: Map<String, CommandFactory> = mapOf(
        "add" to cmd { ctx, name -> Add(ctx, name) },
        "branch" to cmd { ctx, name -> Branch(ctx, name) },
        "checkout" to cmd { ctx, name -> Checkout(ctx, name) },
        "commit" to cmd { ctx, name -> Commit(ctx, name) },
        "diff" to withPager { ctx, name -> Diff(ctx, name) },
        "help" to cmd { ctx, name -> Help(ctx, name) },
        "init" to cmd { ctx, name -> Init(ctx, name) },
        "log" to withPager { ctx, name -> Log(ctx, name) },
        "ls-files" to cmd { ctx, name -> ListFiles(ctx, name) },
        "paged" to withPager { ctx, name -> Paged(ctx, name) },
        "show-head" to cmd { ctx, name -> ShowHead(ctx, name) },
        "status" to cmd { ctx, name -> Status(ctx, name) }
    )

    private fun cmd(fn: CommandFactory): CommandFactory = fn

    private fun withPager(fn: CommandFactory): CommandFactory =
        { ctx, name -> PagerCommand(ctx, name, fn) }

    fun execute(name: String, ctx: CommandContext): CommandExecution =
        commands[name]
            ?.invoke(ctx, name)
            ?.execute()
            ?: CommandExecution(ctx, 1).also { ctx.stderr.println("'$name' is not a gikt command.") }

    class Exit(val code: Int) : Exception("Process exit with $code")
}
