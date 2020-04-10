package com.github.arian.gikt.commands

import com.github.arian.gikt.Repository
import com.github.arian.gikt.mkdirp
import com.github.arian.gikt.write
import com.google.common.jimfs.Jimfs
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock

class CommandHelper {

    private val root: Path

    init {
        val fs = Jimfs.newFileSystem()
        root = fs.getPath("gitk-repo")
        Files.createDirectory(root)
    }

    val repository by lazy { Repository(root) }

    fun writeFile(name: String, contents: String) {
        val path = root.resolve(name)
        path.parent.mkdirp()
        path.write(contents)
    }

    fun init() {
        cmd("init")
    }

    fun cmd(
        name: String,
        vararg args: String,
        env: Map<String, String> = emptyMap(),
        stdin: ByteArray? = null
    ): CommandContext {
        val stderr = ByteArrayOutputStream()
        val stdout = ByteArrayOutputStream()

        val ctx = CommandContext(
            dir = root,
            args = args.toList(),
            stderr = PrintStream(stderr),
            stdout = PrintStream(stdout),
            stdin = ByteArrayInputStream(stdin ?: ByteArray(0)),
            env = { env[it] },
            clock = Clock.systemDefaultZone()
        )

        Command.execute(name, ctx)

        return ctx
    }
}
