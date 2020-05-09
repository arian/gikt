package com.github.arian.gikt.commands

import com.github.arian.gikt.delete
import com.github.arian.gikt.deleteRecursively
import com.github.arian.gikt.isDirectory
import com.github.arian.gikt.makeExecutable
import com.github.arian.gikt.makeUnExecutable
import com.github.arian.gikt.makeUnreadable
import com.github.arian.gikt.mkdirp
import com.github.arian.gikt.readText
import com.github.arian.gikt.repository.Repository
import com.github.arian.gikt.touch
import com.github.arian.gikt.write
import com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock

class CommandHelper : Closeable {

    private val root: Path
    private val fs = MemoryFileSystemBuilder
        .newLinux()
        .build()

    init {
        root = fs.getPath("gitk-repo")
        Files.createDirectory(root)
    }

    val repository by lazy { Repository(root) }

    override fun close() {
        fs.close()
    }

    fun mkdir(name: String): Path =
        root.resolve(name).mkdirp()

    fun writeFile(name: String, contents: String): Path =
        root.resolve(name).apply {
            parent?.mkdirp()
            write(contents)
            makeUnExecutable()
        }

    fun readFile(name: String): String =
        root.resolve(name).readText()

    fun makeUnreadable(name: String): Path =
        root.resolve(name).makeUnreadable()

    fun makeExecutable(name: String): Path =
        root.resolve(name).makeExecutable()

    fun touch(name: String): Path =
        root.resolve(name).touch()

    fun delete(name: String): Path =
        root.resolve(name).let {
            when {
                it.isDirectory() -> it.deleteRecursively()
                else -> it.delete()
            }
        }

    fun init() {
        cmd("init")
    }

    fun commit(msg: String) {
        val env = mapOf(
            "GIT_AUTHOR_NAME" to "Arian",
            "GIT_AUTHOR_EMAIL" to "arian@example.com"
        )
        cmd("commit", env = env, stdin = msg)
    }

    fun cmd(
        name: String,
        vararg args: String,
        env: Map<String, String> = emptyMap(),
        stdin: String? = null
    ): CommandTestExecution {

        val stderr = ByteArrayOutputStream()
        val stdout = ByteArrayOutputStream()

        val ctx = CommandContext(
            dir = root,
            args = args.toList(),
            stderr = PrintStream(stderr),
            stdout = PrintStream(stdout),
            stdin = ByteArrayInputStream(stdin?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)),
            env = { env[it] },
            isatty = false,
            clock = Clock.systemDefaultZone()
        )

        val execution = Commands.execute(name, ctx)

        return CommandTestExecution(
            status = execution.status,
            stdout = stdout.toString(Charsets.UTF_8),
            stderr = stderr.toString(Charsets.UTF_8)
        )
    }

    data class CommandTestExecution(
        val status: Int,
        val stderr: String,
        val stdout: String
    )
}
