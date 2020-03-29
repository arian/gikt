package com.github.arian.gikt

import com.github.arian.gikt.database.ObjectId
import java.nio.file.Path

class Refs(pathname: Path) {

    private val headPath = pathname.resolve("HEAD")

    fun updateHead(oid: ObjectId) {
        val lockfile = Lockfile(headPath)

        val success = lockfile.holdForUpdate {
            it.write(oid.hex)
            it.write("\n")
            it.commit()
        }

        if (!success) {
            throw LockDenied("Could not acquire lock on file: $headPath")
        }
    }

    fun readHead(): ObjectId? =
        headPath
            .takeIf { it.exists() }
            ?.readText()
            ?.let { ObjectId(it.trim()) }

    class LockDenied(m: String) : Exception(m)
}
