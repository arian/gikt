package com.github.arian.gikt

import com.github.arian.gikt.database.ObjectId
import java.nio.file.DirectoryNotEmptyException
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

        data class SymRef(private val refs: Refs, internal val path: Path) : Ref() {
            override val oid get() = refs.readSymRef(path)

            val isHead
                get() =
                    path == refs.headPath

            val shortName
                get(): String {
                    val prefix = listOf(refs.headsPath, refs.pathname).find { dir ->
                        path.parentPaths().any { it == dir }
                    }
                    return path.relativeTo(prefix ?: refs.pathname).toString()
                }

            val longName
                get(): String =
                    path.relativeTo(refs.pathname).toString()
        }

        data class Oid(override val oid: ObjectId) : Ref()
    }

    fun updateHead(oid: ObjectId) {
        updateSymRef(headPath, oid)
    }

    fun updateHead(ref: String) {
        updateRefFile(headPath, ref)
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

    fun readHeadOrThrow(): ObjectId =
        readHead()
            ?: throw Revision.InvalidObject("Not a valid object name: '${currentBranchName()}'")

    fun readRef(name: String): ObjectId? =
        pathForName(name)?.let {
            readSymRef(it)
        }

    private fun currentBranchName(): String {
        return when (val ref = readOidOrSymRef(headPath)) {
            is Ref.SymRef -> ref.shortName
            else -> throw IllegalStateException("Couldn't read 'HEAD'")
        }
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

    private fun updateSymRef(path: Path, oid: ObjectId) {
        Lockfile(path).holdForUpdate {
            when (val ref = readOidOrSymRef(path)) {
                is Ref.Oid, null -> it.writeRef(oid.hex)
                is Ref.SymRef -> {
                    try {
                        updateSymRef(ref.path, oid)
                    } finally {
                        it.rollback()
                    }
                }
            }
        }
    }

    private fun updateRefFile(path: Path, oid: ObjectId) =
        updateRefFile(path, oid.hex)

    private fun updateRefFile(path: Path, ref: String) {
        fun update() = Lockfile(path).holdForUpdate { it.writeRef(ref) }
        try {
            update()
        } catch (e: Lockfile.MissingParent) {
            path.parent.mkdirp()
            update()
        }
    }

    private fun Lockfile.Ref.writeRef(ref: String) {
        write(ref)
        write("\n")
        commit()
    }

    fun listBranches(): List<Ref.SymRef> {
        return listRefs(headsPath)
    }

    private fun listRefs(dirname: Path): List<Ref.SymRef> {
        return dirname
            .listFiles()
            .flatMap { name ->
                if (name.isDirectory()) {
                    listRefs(name)
                } else {
                    listOf(Ref.SymRef(this, name))
                }
            }
    }

    fun deleteBranch(branchName: String): ObjectId {
        val path = headsPath.resolve(branchName)

        return try {
            Lockfile(path).holdForUpdate {
                try {
                    val oid = requireNotNull(readSymRef(path))
                    path.delete()
                    oid
                } finally {
                    it.rollback()
                    deleteParentDirectories(path)
                }
            }
        } catch (e: Exception) {
            throw InvalidBranch("branch '$branchName' not found.")
        }
    }

    private fun deleteParentDirectories(path: Path) {
        path.parentPaths().reversed().forEach { dir ->
            if (dir == headsPath) {
                return
            }
            try {
                dir.delete()
            } catch (e: DirectoryNotEmptyException) {
                return
            }
        }
    }
}
