package com.github.arian.gikt

import com.github.arian.gikt.database.ObjectId
import java.nio.file.NoSuchFileException
import java.nio.file.Path

class Refs(private val pathname: Path) {

    class InvalidBranch(msg: String) : Exception(msg)

    private val headPath = pathname.resolve("HEAD")
    private val refsPath = pathname.resolve("refs")
    private val headsPath = refsPath.resolve("heads")

    companion object {
        private val SYMREF = "^ref: (.+)$".toRegex()
    }

    sealed class Ref {
        abstract val oid: ObjectId?
        data class SymRef(val refs: Refs, val path: Path) : Ref() {
            override val oid get() = refs.readSymRef(path)
            val isHead get() = path == refs.headPath
        }
        data class Oid(override val oid: ObjectId) : Ref()
    }

    fun updateHead(oid: ObjectId) {
        updateRefFile(headPath, oid)
    }

    fun setHead(revision: String, oid: ObjectId) {
        val path = headsPath.resolve(revision)
        if (path.exists()) {
            val relative = path.relativeTo(headPath.parent)
            updateRefFile(headPath, "ref: $relative")
        } else {
            updateRefFile(headPath, oid)
        }
    }

    fun readHead(): ObjectId? =
        readSymRef(headPath)

    fun readRef(name: String): ObjectId? =
        pathForName(name)?.let {
            readSymRef(it)
        }

    fun currentRef(source: Path = headPath): Ref.SymRef =
        when (val ref = readOidOrSymRef(source)) {
            is Ref.SymRef -> currentRef(ref.path)
            is Ref.Oid, null -> Ref.SymRef(this, source)
        }

    private fun readOidOrSymRef(path: Path): Ref? =
        try {
            val data = path.readText().trim()
            SYMREF.find(data)
                ?.destructured
                ?.let { (ref) -> Ref.SymRef(this, path.parent.resolve(ref)) }
                ?: Ref.Oid(ObjectId(data))
        } catch (e: NoSuchFileException) {
            null
        }

    private fun readSymRef(path: Path): ObjectId? =
        when (val ref = readOidOrSymRef(path)) {
            is Ref.SymRef -> readSymRef(ref.path)
            is Ref.Oid -> ref.oid
            null -> null
        }

    private fun pathForName(name: String): Path? =
        listOf(pathname, refsPath, headsPath)
            .asSequence()
            .map { it.resolve(name) }
            .find { it.exists() }

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

    private fun updateRefFile(path: Path, oid: ObjectId) =
        updateRefFile(path, oid.hex)

    private fun updateRefFile(path: Path, ref: String) {
        fun update() = Lockfile(path).holdForUpdate {
            it.write(ref)
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
