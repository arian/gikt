package com.github.arian.gikt

import java.io.OutputStream
import java.nio.file.AccessMode
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant
import java.util.stream.Collectors

/**
 * The entries in the directory.
 */
fun Path.listFiles(): List<Path> = Files.list(this).collect(Collectors.toList())

/**
 * For example: `Path.of("/abc/123/xyz").relativeTo(Path.of("/abc"))`
 * returns `Path.of("123/xyz")
 */
fun Path.relativeTo(other: Path): Path = other.relativize(this)

/**
 * A list of the names of the parent directories.
 * `Path.of("a/b/c")` would return `listOf(Path.of("a"), Path.of("b"))`
 */
fun Path.parents(): List<Path> =
    (0 until nameCount - 1).map { getName(it) }

/**
 * A list of the names of the parent directories.
 * `Path.of("a/b/c")` would return `listOf(Path.of("a"), Path.of("a/b"))`
 */
fun Path.parentPaths(): List<Path> =
    (1 until nameCount).map { subpath(0, it) }

fun Path.readBytes(): ByteArray = Files.readAllBytes(this)

fun Path.readText(): String = Files.newBufferedReader(this).use { it.readText() }

fun Path.write(text: String): Path = Files.writeString(this, text)

fun Path.write(text: String, vararg openOption: OpenOption): Path =
    Files.writeString(this, text, *openOption)

fun Path.write(text: ByteArray): Path = Files.write(this, text)

fun Path.write(text: ByteArray, vararg openOption: OpenOption): Path =
    Files.write(this, text, *openOption)

fun Path.exists(): Boolean = Files.exists(this)

fun Path.mkdirp(): Path = Files.createDirectories(this)

fun Path.outputStream(): OutputStream = Files.newOutputStream(this)

fun Path.renameTo(to: Path): Path = Files.move(this, to)

fun Path.delete(): Path = apply { Files.delete(this) }

fun Path.deleteRecursively(): Path = apply {
    Files.walk(this)
        .sorted(Comparator.reverseOrder())
        .forEach { Files.delete(it) }
}

fun Path.touch(): Path =
    try {
        Files.createFile(this)
    } catch (e: FileAlreadyExistsException) {
        Files.setLastModifiedTime(this, FileTime.from(Instant.now()))
    }

fun Path.checkAccess(accessMode: AccessMode) =
    also { it.fileSystem.provider().checkAccess(it, accessMode) }

fun Path.makeExecutable(): Path =
    Files.setPosixFilePermissions(
        this,
        Files.getPosixFilePermissions(this) + PosixFilePermissions.fromString("--x--x--x")
    )

fun Path.makeUnExecutable(): Path =
    Files.setPosixFilePermissions(
        this,
        Files.getPosixFilePermissions(this) - PosixFilePermissions.fromString("--x--x--x")
    )

fun Path.makeUnreadable(): Path =
    Files.setPosixFilePermissions(
        this,
        Files.getPosixFilePermissions(this) - PosixFilePermissions.fromString("r--r--r--")
    )

fun Path.stat(): FileStat = FileStat.of(this)

fun Path.isDirectory(): Boolean = Files.isDirectory(this)
