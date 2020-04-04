package com.github.arian.gikt

import java.nio.file.AccessDeniedException
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
                throw MissingFile("pathspec '$relative' did noy match any files")
        }
    }

    fun readFile(it: Path): ByteArray = try {
        absolutePath(it).readBytes()
    } catch (e: AccessDeniedException) {
        throw NoPermission("open(${it.relativeTo(rootPath)}): Permission denied")
    }

    fun statFile(it: Path): FileStat = try {
        absolutePath(it).stat()
    } catch (e: AccessDeniedException) {
        throw NoPermission("stat(${it.relativeTo(rootPath)}): Permission denied")
    }

    fun absolutePath(it: Path): Path = rootPath.resolve(it)

    class MissingFile(msg: String) : Exception(msg)
    class NoPermission(msg: String) : Exception(msg)
}
