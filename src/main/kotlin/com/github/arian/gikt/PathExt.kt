@file:OptIn(ExperimentalPathApi::class)

package com.github.arian.gikt

import java.io.InputStream
import java.io.OutputStream
import java.nio.file.AccessMode
import java.nio.file.CopyOption
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.deleteExisting
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.getPosixFilePermissions
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.moveTo
import kotlin.io.path.readAttributes
import kotlin.io.path.setPosixFilePermissions
import kotlin.io.path.writeBytes
import kotlin.io.path.copyTo as stdCopyTo
import kotlin.io.path.createDirectory as stdCreateDirectory
import kotlin.io.path.createTempDirectory as stdCreateTempDirectory
import kotlin.io.path.exists as stdExists
import kotlin.io.path.fileSize as stdFileSize
import kotlin.io.path.inputStream as stdInputStream
import kotlin.io.path.isDirectory as stdIsDirectory
import kotlin.io.path.isExecutable as stdIsExecutable
import kotlin.io.path.outputStream as stdOutputStream
import kotlin.io.path.readBytes as stdReadBytes
import kotlin.io.path.readText as stdReadText
import kotlin.io.path.relativeTo as stdRelativeTo

/**
 * The entries in the directory.
 */
fun Path.listFiles(): List<Path> = listDirectoryEntries()

/**
 * For example: `Path.of("/abc/123/xyz").relativeTo(Path.of("/abc"))`
 * returns `Path.of("123/xyz")
 */
fun Path.relativeTo(other: Path): Path = stdRelativeTo(other)

/**
 * A list of the names of the parent directories.
 * `Path.of("a/b/c")` would return `listOf(Path.of("a"), Path.of("b"))`
 */
fun Path.parents(): List<Path> =
    (0 until nameCount - 1).map { getName(it) }

/**
 * A list of the names of the parent directories and the filename.
 * `Path.of("a/b/c")` would return `listOf(Path.of("a"), Path.of("b"), Path.of("c")`
 */
fun Path.split(): List<Path> =
    (0 until nameCount).map { getName(it) }

/**
 * A list of the names of the parent directories.
 * `Path.of("a/b/c")` would return `listOf(Path.of("a"), Path.of("a/b"))`
 */
fun Path.parentPaths(): List<Path> {
    fun helper(p: Path?): List<Path> =
        when (p) {
            null -> emptyList()
            else -> listOf(p) + helper(p.parent)
        }
    return helper(parent).reversed()
}

fun Path.readBytes(): ByteArray = stdReadBytes()

fun Path.readText(): String = stdReadText()

fun Path.write(text: String): Path = Files.writeString(this, text)

fun Path.write(text: String, vararg openOption: OpenOption): Path =
    Files.writeString(this, text, *openOption)

fun Path.write(text: ByteArray): Path = also { writeBytes(text) }

fun Path.write(text: ByteArray, vararg openOption: OpenOption): Path =
    also { writeBytes(text, *openOption) }

fun Path.exists(): Boolean = stdExists()

fun Path.mkdirp(): Path = createDirectories()

fun Path.outputStream(vararg flags: OpenOption): OutputStream = stdOutputStream(*flags)

fun Path.inputStream(vararg flags: OpenOption): InputStream = stdInputStream(*flags)

fun Path.renameTo(to: Path, vararg opts: CopyOption): Path = moveTo(to, *opts)

fun Path.copyTo(to: Path, vararg opts: CopyOption): Path = stdCopyTo(to, *opts)

fun Path.delete(): Path = apply { deleteExisting() }

fun Path.deleteRecursively(): Path = apply {
    Files.walk(this)
        .sorted(Comparator.reverseOrder())
        .forEach { it.deleteExisting() }
}

fun Path.touch(): Path =
    try {
        createFile()
    } catch (e: FileAlreadyExistsException) {
        Files.setLastModifiedTime(this, FileTime.from(Instant.now()))
    }

fun createTempDirectory(prefix: String?) =
    stdCreateTempDirectory(prefix)

fun Path.createDirectory(): Path =
    stdCreateDirectory()

fun Path.checkAccess(accessMode: AccessMode) =
    also { it.fileSystem.provider().checkAccess(it, accessMode) }

private val executablePermissions = PosixFilePermissions.fromString("--x--x--x")
private val readablePermissions = PosixFilePermissions.fromString("r--r--r--")

fun Path.makeExecutable(): Path =
    setPosixFilePermissions(getPosixFilePermissions() + executablePermissions)

fun Path.makeUnExecutable(): Path =
    setPosixFilePermissions(getPosixFilePermissions() - executablePermissions)

fun Path.makeUnreadable(): Path =
    setPosixFilePermissions(getPosixFilePermissions() - readablePermissions)

fun Path.stat(): FileStat = FileStat.of(this)

fun Path.isDirectory(): Boolean = stdIsDirectory()

fun Path.isExecutable(): Boolean = stdIsExecutable()

fun Path.readUnixAttributes() = try {
    readAttributes("unix:dev,ino,uid,gid,mode")
} catch (e: Exception) {
    emptyMap()
}

fun Path.getLastModifiedInstant(): Instant =
    getLastModifiedTime().toInstant()

fun Path.fileSize(): Long = stdFileSize()
