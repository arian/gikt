package com.github.arian.gikt

import com.github.arian.gikt.database.ObjectId
import java.nio.file.Path

class Refs(pathname: Path) {
    class InvalidBranch(msg: String) : Exception(msg)

    private val headPath = pathname.resolve("HEAD")
    private val refsPath = pathname.resolve("refs")
    private val headsPath = refsPath.resolve("heads")

    fun updateHead(oid: ObjectId) {
        updateRefFile(headPath, oid)
    }

    fun readHead(): ObjectId? =
        headPath
            .takeIf { it.exists() }
            ?.readText()
            ?.let { ObjectId(it.trim()) }

    fun createBranch(branchName: String) {

        if (INVALID_NAME.containsMatchIn(branchName)) {
            throw InvalidBranch("'$branchName' is not a valid branch name.")
        }

        val path = headsPath.resolve(branchName)

        if (path.exists()) {
            throw InvalidBranch("A branch named '$branchName' already exists.")
        }

        readHead()?.also { updateRefFile(path, it) }
            ?: throw IllegalStateException("Couldn't read HEAD")
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

    companion object {
        val INVALID_NAME = Regex(
            """
              ^\.
            | \/\.
            | \.\.
            | \/$
            | \.lock$
            | @\{
            | [\x00-\x20*:?\[\\^~\x7f]
            """.trimIndent(),
            RegexOption.COMMENTS
        )
    }
}
