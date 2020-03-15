package com.github.arian.gikt

import java.nio.file.Files
import java.nio.file.Path

class Workspace(private val rootPath: Path) {

    private val ignore = listOf(".git", ".idea", "out", "gikt.iml")

    private fun ignores(path: Path) = ignore.map { path.resolve(it) }.toSet()

    fun listFiles(path: Path = rootPath): Set<Path> {
        val filenames = (path.listFiles().toSet()) - ignores(path)

        return filenames
            .flatMap {
                if (Files.isDirectory(it)) {
                    listFiles(it)
                } else {
                    listOf(it)
                }
            }
            .toSet()
    }

    fun readFile(it: Path): ByteArray = it.readBytes()

    fun statFile(it: Path) =
        FileStat(
            executable = Files.isExecutable(it)
        )

}
