package com.github.arian.gikt

import com.github.arian.gikt.commands.Command
import com.github.arian.gikt.commands.CommandContext
import com.github.arian.gikt.commands.Environment
import java.nio.file.FileSystems
import java.time.Clock
import kotlin.system.exitProcess

fun main(args: Array<String>) {

    val pwd = FileSystems.getDefault()
        .getPath(".")
        .toAbsolutePath()
        .normalize()

    val env: Environment = { System.getenv(it) }

    val ctx = CommandContext(
        dir = pwd,
        env = env,
        args = args.drop(1),
        stdin = System.`in`,
        stdout = System.out,
        stderr = System.err,
        isatty = System.console() != null,
        clock = Clock.systemDefaultZone()
    )

    val cmd = Command.execute(args.getOrNull(0) ?: "help", ctx)
    exitProcess(cmd.status)
}
