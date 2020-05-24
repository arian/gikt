package com.github.arian.gikt.database

import com.github.arian.gikt.deflateInto
import com.github.arian.gikt.exists
import com.github.arian.gikt.inflate
import com.github.arian.gikt.listFiles
import com.github.arian.gikt.mkdirp
import com.github.arian.gikt.outputStream
import com.github.arian.gikt.readBytes
import com.github.arian.gikt.renameTo
import java.io.IOException
import java.nio.file.Path

private val tempChars = ('a'..'z') + ('A'..'Z') + ('0'..'9')

class Database(private val pathname: Path) {

    private var objects: Map<ObjectId, GiktObject> = emptyMap()

    fun store(obj: GiktObject) {
        writeObject(obj.oid, obj.content)
    }

    private fun writeObject(oid: ObjectId, content: ByteArray) {
        val objPath = objectPath(oid)
        if (objPath.exists()) {
            return
        }

        val dirName = objPath.resolve("..").normalize()
        val tmpPath = dirName.resolve(generateTempName())

        if (!dirName.exists()) {
            dirName.mkdirp()
        }

        content.deflateInto(tmpPath.outputStream())
        tmpPath.renameTo(objPath)
    }

    private fun objectPath(oid: ObjectId): Path =
        objectPath(oid.hex)

    private fun objectPath(sha1: String): Path {
        return pathname
            .resolve(sha1.take(2))
            .resolve(sha1.drop(2))
    }

    private fun generateTempName() = "temp_obj_${tempChars.shuffled().joinToString("")}"

    fun load(oid: ObjectId, prefix: Path): GiktObject {
        val objPath = objectPath(oid)
        val content = objPath.readBytes().inflate()
        val obj = GiktObject.parse(prefix, content)
        objects = objects + (oid to obj)
        return obj
    }

    fun prefixMatch(name: String): List<ObjectId> {
        if (name.length < 4) {
            return emptyList()
        }

        val dir = objectPath(name).parent
        val dirname = dir.fileName.toString()

        return try {
            dir.listFiles()
                .map { "$dirname${it.fileName}" }
                .filter { it.startsWith(name) }
                .map { ObjectId(it) }
        } catch (e: IOException) {
            emptyList()
        }
    }

    fun treeDiff(a: ObjectId, b: ObjectId): TreeDiffMap {
        return TreeDiff(pathname.fileSystem, this).compareOids(a, b)
    }
}
