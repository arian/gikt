package com.github.arian.gikt

import java.io.OutputStream
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermission
import java.time.Instant
import kotlin.streams.toList

/**
 * The entries in the directory.
 */
fun Path.listFiles() = Files.list(this).toList()

/**
 * For example: `Path.of("/abc/123/xyz").relativeTo(Path.of("/abc"))`
 * returns `Path.of("123/xyz")
 */
fun Path.relativeTo(other: Path): Path = other.relativize(this)

fun Path.readBytes(): ByteArray = Files.readAllBytes(this)

fun Path.readText(): String = Files.newBufferedReader(this).readText()

fun Path.write(text: String): Path = Files.writeString(this, text)

fun Path.exists(): Boolean = Files.exists(this)

fun Path.mkdirp(): Path = Files.createDirectories(this)

fun Path.outputStream(): OutputStream = Files.newOutputStream(this)

fun Path.renameTo(to: Path): Path = Files.move(this, to)

fun Path.deleteRecursively(): Unit = Files.walk(this)
    .sorted(Comparator.reverseOrder())
    .forEach { Files.delete(it) }

fun Path.touch(): Path =
    try {
        Files.createFile(this)
    } catch (e: FileAlreadyExistsException) {
        Files.setLastModifiedTime(this, FileTime.from(Instant.now()))
    }

fun Path.makeExecutable(): Path =
    Files.setPosixFilePermissions(
        this,
        Files.getPosixFilePermissions(this) + setOf(
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_EXECUTE,
            PosixFilePermission.OTHERS_EXECUTE
        )
    )

fun Path.mode(): Mode = when (Files.isExecutable(this)) {
    true -> Mode.EXECUTABLE
    else -> Mode.REGULAR
}

