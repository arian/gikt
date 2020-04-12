package com.github.arian.gikt.commands

import com.github.arian.gikt.FileStat
import com.github.arian.gikt.index.Index
import java.nio.file.Path

class Status(ctx: CommandContext) : AbstractCommand(ctx) {

    override fun run() {
        val loadedIndex = repository.index.load()

        scanWorkspace(loadedIndex)
            .sorted()
            .forEach { println("?? $it") }

        exitProcess(0)
    }

    private fun scanWorkspace(index: Index.Loaded, prefix: Path? = null): Set<String> {
        val ls = prefix
            ?.let { repository.workspace.listDir(it) }
            ?: repository.workspace.listDir()

        return ls
            .flatMap { (it, stat) ->
                when {
                    index.tracked(it) -> {
                        when (stat.directory) {
                            true -> scanWorkspace(index, it)
                            false -> emptySet()
                        }
                    }
                    trackableFile(index, it, stat) -> {
                        setOf(
                            when (stat.directory) {
                                true -> "$it/"
                                false -> "$it"
                            }
                        )
                    }
                    else -> emptySet()
                }
            }
            .toSet()
    }

    private fun trackableFile(index: Index.Loaded, path: Path, stat: FileStat): Boolean {
        if (stat.file) {
            return !index.tracked(path)
        }

        val items = repository.workspace.listDir(path)
        val files = items.filter { (_, stat) -> stat.file }
        val dirs = items.filter { (_, stat) -> stat.directory }

        return (files + dirs).any { (itemPath, itemStat) -> trackableFile(index, itemPath, itemStat) }
    }
}
