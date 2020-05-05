package com.github.arian.gikt.commands.util

import com.github.arian.gikt.delete
import com.github.arian.gikt.outputStream
import java.io.Closeable
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path

class Pager(env: Map<String, String> = emptyMap()) {

    companion object {
        private const val PAGER_CMD = "less"
        private val PAGER_ENV = mapOf("LESS" to "FRX", "LV" to "-c")
    }

    private val e = PAGER_ENV + env
    private val cmd: String = e["GIT_PAGER"] ?: e["PAGER"] ?: PAGER_CMD

    fun <T> start(writeTo: (PrintStream) -> T): T {
        val tmpFile = Files.createTempFile("gikt", ".patch")
        return RunningPager(tmpFile).use { pager ->
            writeTo(pager.stdout).also {
                pager.start()
            }
        }
    }

    private inner class RunningPager(private val file: Path) : Closeable {
        internal val stdout = PrintStream(file.outputStream())
        private var process: Process? = null

        override fun close() {
            stdout.close()
            process?.destroy()
            file.delete()
        }

        internal fun start() {
            val t = Thread {
                ProcessBuilder(cmd, file.toAbsolutePath().toString())
                    .run {
                        environment().putAll(e)
                        redirectOutput(ProcessBuilder.Redirect.INHERIT)
                        redirectError(ProcessBuilder.Redirect.INHERIT)
                        redirectInput(ProcessBuilder.Redirect.INHERIT)
                        start()
                    }
                    .also { process = it }
                    .run { waitFor() }
            }
            t.run()
        }
    }
}
