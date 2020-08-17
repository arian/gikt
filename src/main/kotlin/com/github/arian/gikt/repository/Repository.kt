package com.github.arian.gikt.repository

import com.github.arian.gikt.Refs
import com.github.arian.gikt.Workspace
import com.github.arian.gikt.database.Database
import com.github.arian.gikt.database.GiktObject
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.database.Tree
import com.github.arian.gikt.database.TreeDiffMap
import com.github.arian.gikt.index.Index
import com.github.arian.gikt.relativeTo
import java.io.IOException
import java.nio.file.Path
import com.github.arian.gikt.database.Entry as DatabaseEntry

class Repository(private val rootPath: Path) {

    private val gitPath = rootPath.resolve(".git")
    private val dbPath = gitPath.resolve("objects")
    private val indexPath = gitPath.resolve("index")

    val relativeRoot: Path get() = relativePath(rootPath)

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
        try {
            database.load(oid, prefix = rootPath)
        } catch (e: IllegalStateException) {
            throw BadObject(e, "bad object ${oid.hex}")
        } catch (e: IOException) {
            throw BadObject(e, "bad object ${oid.hex}")
        }

    fun migration(treeDiff: TreeDiffMap): Migration =
        Migration(this, treeDiff)

    class BadObject(e: Exception, msg: String) : Exception(msg, e)
}
