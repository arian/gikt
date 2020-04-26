package com.github.arian.gikt.repository

import com.github.arian.gikt.Refs
import com.github.arian.gikt.Workspace
import com.github.arian.gikt.database.Database
import com.github.arian.gikt.database.Entry as DatabaseEntry
import com.github.arian.gikt.database.GiktObject
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.database.Tree
import com.github.arian.gikt.index.Index
import com.github.arian.gikt.relativeTo
import java.nio.file.Path

class Repository(val rootPath: Path) {

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

    fun status() = Status(this)

    fun loadObject(oid: ObjectId): GiktObject =
        database.load(rootPath, oid)
}
