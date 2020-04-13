package com.github.arian.gikt.database

import com.github.arian.gikt.deflateInto
import com.github.arian.gikt.exists
import com.github.arian.gikt.inflate
import com.github.arian.gikt.mkdirp
import com.github.arian.gikt.outputStream
import com.github.arian.gikt.readBytes
import com.github.arian.gikt.renameTo
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

    private fun objectPath(oid: ObjectId): Path {
        val sha1 = oid.hex
        return pathname
            .resolve(sha1.take(2))
            .resolve(sha1.drop(2))
    }

    private fun generateTempName() = "temp_obj_${tempChars.shuffled().joinToString("")}"

    fun load(root: Path, oid: ObjectId): GiktObject {
        val objPath = objectPath(oid)
        val content = objPath.readBytes().inflate()
        val obj = GiktObject.parse(root, content)
        objects = objects + (oid to obj)
        return obj
    }
}
