package com.github.arian.gikt.commands

import kotlinx.cli.ArgType
import kotlinx.cli.default
import java.time.Instant

/**
 * A test sub command to test if the program is correctly piped into the pager or into a bash pipeline.
 *
 * ```
 * gikt paged --times 100000
 * ```
 *
 * Should open the pager, but performantly, it would block the [AbstractCommand.println], until that process
 * needs more data to show.
 *
 * When piping into another program, [AbstractCommand.shouldContinuePrinting] checks if the other program is still
 * alive. If not, it can also stop printing.
 *
 * ```
 * gikt paged --times 100000 | head
 * ```
 *
 * That would only run the loop 10 times.
 */
class Paged(ctx: CommandContext, name: String) : AbstractCommand(ctx, name) {

    private val times by option(ArgType.Int).default(200)

    override fun run() {
        var x = 0
        for (it in 0..times) {
            if (shouldContinuePrinting()) {
                println("${Instant.now()} - ${it.toString().padStart(10)}")
                x = it
            } else {
                break
            }
        }
        ctx.stderr.println("reached until $x")
    }
}
