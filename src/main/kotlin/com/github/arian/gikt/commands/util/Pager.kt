package com.github.arian.gikt.commands.util

import com.github.arian.gikt.commands.Environment
import java.io.PrintStream
import java.nio.file.Path

class Pager(env: Environment) {

    companion object {
        private const val PAGER_CMD = "less"
    }

    private val e = mapOf(
        "LESS" to (env("LESS") ?: env("GIKT_LESS") ?: "FRX"),
        "LV" to (env("LV") ?: "-c")
    )

    private val cmd: String = env("GIT_PAGER") ?: env("PAGER") ?: PAGER_CMD

    fun <T> start(dir: Path, writeTo: (PrintStream) -> T): T {
        val process = ProcessBuilder(cmd)
            .run {
                redirectOutput(ProcessBuilder.Redirect.INHERIT)
                redirectError(ProcessBuilder.Redirect.INHERIT)
                environment().putAll(e)
                directory(dir.toFile())
            }
            .start()

        val pw = PrintStream(process.outputStream)
        val result = writeTo(pw)
        pw.close()
        process.waitFor()
        return result
    }
}
