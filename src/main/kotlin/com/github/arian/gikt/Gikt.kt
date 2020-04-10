package com.github.arian.gikt

import com.github.arian.gikt.commands.Command
import com.github.arian.gikt.commands.CommandContext
import com.github.arian.gikt.commands.Environment
import java.io.OutputStream
import java.nio.file.FileSystems
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Clock
import java.util.zip.Deflater
import java.util.zip.DeflaterInputStream
import kotlin.system.exitProcess

fun getPwd(): Path = FileSystems.getDefault().getPath(".").toAbsolutePath().normalize()

fun ByteArray.sha1(): ByteArray {
    val data = this
    return MessageDigest.getInstance("SHA-1").run {
        update(data)
        digest()
    }
}

fun ByteArray.deflateInto(out: OutputStream) {
    val def = DeflaterInputStream(inputStream(), Deflater())
    out.use { def.copyTo(it) }
}

fun main(args: Array<String>) {

    val env: Environment = { System.getenv(it) }

    val ctx = CommandContext(
        dir = getPwd(),
        env = env,
        args = args.drop(1),
        stdin = System.`in`,
        stdout = System.out,
        stderr = System.err,
        clock = Clock.systemDefaultZone()
    )

    val cmd = Command.execute(args[0], ctx)
    exitProcess(cmd.status)
}
