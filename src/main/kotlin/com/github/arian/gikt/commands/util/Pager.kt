package com.github.arian.gikt.commands.util

import java.io.PrintStream
import java.nio.file.Path

class Pager(env: Map<String, String> = emptyMap()) {

    companion object {
        private const val PAGER_CMD = "less"
        private val PAGER_ENV = mapOf("LESS" to "FRX", "LV" to "-c")
    }

    private val e = PAGER_ENV + env
    private val cmd: String = e["GIT_PAGER"] ?: e["PAGER"] ?: PAGER_CMD

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
