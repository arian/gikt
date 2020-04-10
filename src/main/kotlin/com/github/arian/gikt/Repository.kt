package com.github.arian.gikt

import com.github.arian.gikt.database.Database
import com.github.arian.gikt.database.Entry as DatabaseEntry
import com.github.arian.gikt.database.Tree
import com.github.arian.gikt.index.Index
import java.nio.file.Path

class Repository(private val rootPath: Path) {

    val gitPath = rootPath.resolve(".git")
    val dbPath = gitPath.resolve("objects")
    val indexPath = gitPath.resolve("index")

    val workspace by lazy { Workspace(rootPath) }
    val database by lazy { Database(dbPath) }
    val index by lazy { Index(indexPath) }
    val refs by lazy { Refs(gitPath) }

    fun resolvePath(path: String): Path = rootPath.resolve(path)
    fun resolvePath(path: Path): Path = rootPath.resolve(path)
    fun relativePath(path: Path): Path = path.relativeTo(rootPath)

    fun buildTree(entries: List<DatabaseEntry>) = Tree.build(rootPath, entries)
}
