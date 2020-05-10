package com.github.arian.gikt

import com.github.arian.gikt.database.ObjectId
import java.nio.file.Path

class Refs(private val pathname: Path) {

    class InvalidBranch(msg: String) : Exception(msg)

    private val headPath = pathname.resolve("HEAD")
    private val refsPath = pathname.resolve("refs")
    private val headsPath = refsPath.resolve("heads")

    fun updateHead(oid: ObjectId) {
        updateRefFile(headPath, oid)
    }

    fun readHead(): ObjectId? =
        readRefFile(headPath)

    private fun readRefFile(path: Path): ObjectId? =
        path
            .takeIf { it.exists() }
            ?.readText()
            ?.let { ObjectId(it.trim()) }

    private fun pathForName(name: String): Path? =
        listOf(pathname, refsPath, headsPath)
            .asSequence()
            .map { it.resolve(name) }
            .find { it.exists() }

    fun readRef(name: String): ObjectId? =
        pathForName(name)?.let {
            readRefFile(it)
        }

    fun createBranch(branchName: String, startOid: ObjectId) {

        if (!Revision.validRef(branchName)) {
            throw InvalidBranch("'$branchName' is not a valid branch name.")
        }

        val path = headsPath.resolve(branchName)

        if (path.exists()) {
            throw InvalidBranch("A branch named '$branchName' already exists.")
        }

        updateRefFile(path, startOid)
    }

    private fun updateRefFile(path: Path, oid: ObjectId) {
        fun update() = Lockfile(path).holdForUpdate {
            it.write(oid.hex)
            it.write("\n")
            it.commit()
        }
        try {
            update()
        } catch (e: Lockfile.MissingParent) {
            path.parent.mkdirp()
            update()
        }
    }
}
