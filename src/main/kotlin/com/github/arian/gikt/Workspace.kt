package com.github.arian.gikt

import java.nio.file.AccessDeniedException
import java.nio.file.AccessMode
import java.nio.file.Path

class Workspace(private val rootPath: Path) {

    private val ignore = listOf(".git", ".idea", "out", "gikt.iml", "gikt.jar", "gikt-test.jar")

    private val rootIgnores = ignores(rootPath)

    private fun ignores(path: Path) = ignore.map { path.resolve(it) }.toSet()

    fun listFiles(path: Path = rootPath): Set<Path> {
        return listFilesIntern(path.normalize())
    }

    private fun listFilesIntern(p: Path): Set<Path> {
        val ignore = ignores(p) + rootIgnores
        val relative = p.relativeTo(rootPath)
        return when {
            ignore.contains(p) ->
                emptySet()
            p.isDirectory() ->
                (p.listFiles().toSet() - ignore).flatMap { listFiles(it) }.toSet()
            p.exists() ->
                setOf(relative)
            else ->
                throw MissingFile("pathspec '$relative' did not match any files")
        }
    }

    fun listDir() = listDirIntern(rootPath)

    fun listDir(path: Path) = listDirIntern(absolutePath(path))

    private fun listDirIntern(path: Path): Map<Path, FileStat> {
        val ignore = ignores(path) + rootIgnores
        val entries = path.listFiles().toSet() - ignore

        return entries
            .map {
                val p = it.relativeTo(rootPath)
                p to statFile(p)
            }
            .toMap()
    }

    fun readFile(it: String): ByteArray = readFileChecked(absolutePath(it))
    fun readFile(it: Path): ByteArray = readFileChecked(absolutePath(it))

    private fun readFileChecked(it: Path): ByteArray = try {
        it.checkAccess(AccessMode.READ).readBytes()
    } catch (e: AccessDeniedException) {
        throw NoPermission("open('${it.relativeTo(rootPath)}'): Permission denied")
    }

    fun statFile(it: Path): FileStat = statFileChecked(absolutePath(it))

    private fun statFileChecked(it: Path): FileStat = try {
        it.checkAccess(AccessMode.READ).stat()
    } catch (e: AccessDeniedException) {
        throw NoPermission("stat('${it.relativeTo(rootPath)}'): Permission denied")
    }

    private fun absolutePath(it: Path): Path = rootPath.resolve(it)
    private fun absolutePath(it: String): Path = rootPath.resolve(it)

    class MissingFile(msg: String) : Exception(msg)
    class NoPermission(msg: String) : Exception(msg)
}
