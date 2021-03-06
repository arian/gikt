package com.github.arian.gikt.commands

import com.github.arian.gikt.Mode
import com.github.arian.gikt.copyTo
import com.github.arian.gikt.createDirectory
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.delete
import com.github.arian.gikt.deleteRecursively
import com.github.arian.gikt.exists
import com.github.arian.gikt.isDirectory
import com.github.arian.gikt.makeExecutable
import com.github.arian.gikt.makeUnExecutable
import com.github.arian.gikt.makeUnreadable
import com.github.arian.gikt.mkdirp
import com.github.arian.gikt.readText
import com.github.arian.gikt.repository.Repository
import com.github.arian.gikt.stat
import com.github.arian.gikt.touch
import com.github.arian.gikt.write
import com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.PrintStream
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class CommandHelper : Closeable {

    private val root: Path
    private val fs = MemoryFileSystemBuilder
        .newLinux()
        .build()

    private val defaultEnv = mapOf(
        "GIT_AUTHOR_NAME" to "Arian",
        "GIT_AUTHOR_EMAIL" to "arian@example.com"
    )

    init {
        root = fs.getPath("gitk-repo").createDirectory()
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

    fun readFileMode(name: String): Mode =
        root.resolve(name).stat().let(Mode.Companion::fromStat)

    fun copy(source: String, target: String): Path =
        root.resolve(source).copyTo(root.resolve(target))

    fun makeUnreadable(name: String): Path =
        root.resolve(name).makeUnreadable()

    fun makeExecutable(name: String): Path =
        root.resolve(name).makeExecutable()

    fun touch(name: String): Path =
        root.resolve(name).touch()

    fun exists(name: String): Boolean =
        root.resolve(name).exists()

    fun delete(name: String): Path =
        root.resolve(name).let {
            when {
                it.isDirectory() -> it.deleteRecursively()
                else -> it.delete()
            }
        }

    fun resetIndex() {
        delete(".git/index")
        cmd("add", ".")
    }

    fun init() {
        cmd("init")
    }

    fun commit(msg: String, timeOffset: Long = 0): ObjectId {
        val execution = cmd("commit", env = defaultEnv, stdin = msg, timeOffset = timeOffset)
        assertEquals(0, execution.status)
        return requireNotNull(repository.refs.readHead())
    }

    fun commitFile(
        name: String,
        contents: String? = null,
        msg: String = "commit",
        timeOffset: Long = 0
    ): ObjectId {
        if (contents == null) {
            touch(name)
        } else {
            writeFile(name, contents)
        }
        cmd("add", ".")
        commit(msg, timeOffset = timeOffset)
        return requireNotNull(repository.refs.readHead())
    }

    fun cmd(
        name: String,
        vararg args: String,
        env: Map<String, String> = defaultEnv,
        stdin: String? = null,
        timeOffset: Long = 0L
    ): CommandTestExecution {

        val stderr = ByteArrayOutputStream()
        val stdout = ByteArrayOutputStream()

        val zoneId = ZoneId.of("Europe/Amsterdam")
        val now = Instant.parse("2019-08-14T10:08:22.00Z")
        val clock = Clock.fixed(now.plusSeconds(timeOffset), zoneId)

        val ctx = CommandContext(
            dir = root,
            args = args.toList(),
            stderr = PrintStream(stderr),
            stdout = PrintStream(stdout),
            stdin = ByteArrayInputStream(stdin?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)),
            env = { env[it] },
            isatty = false,
            clock = clock
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
