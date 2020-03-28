package com.github.arian.gikt

import java.nio.file.Path

class Workspace(private val rootPath: Path) {

    private val ignore = listOf(".git", ".idea", "out", "gikt.iml")

    private val rootIgnores = ignores(rootPath)

    private fun ignores(path: Path) = ignore.map { path.resolve(it) }.toSet()

    fun listFiles(path: Path = rootPath): Set<Path> {
        return listFilesIntern(path.normalize())
    }

    private fun listFilesIntern(p: Path): Set<Path> {
        val ignore = ignores(p) + rootIgnores
        if (ignore.contains(p)) {
            return emptySet()
        }
        return if (p.isDirectory()) {
            (p.listFiles().toSet() - ignore)
                .flatMap { listFiles(it) }
                .toSet()
        } else {
            setOf(p.relativeTo(rootPath))
        }
    }

    fun readFile(it: Path): ByteArray = absolutePath(it).readBytes()

    fun statFile(it: Path): FileStat = absolutePath(it).stat()

    fun absolutePath(it: Path): Path = rootPath.resolve(it)
}
