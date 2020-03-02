package com.github.arian.gikt

import com.github.arian.gikt.database.GiktObject
import com.github.arian.gikt.database.ObjectId
import java.nio.file.Path

private val tempChars = ('a'..'z') + ('A'..'Z') + ('0'..'9')

class Database(private val pathname: Path) {

    fun store(obj: GiktObject) {
        writeObject(obj.oid, obj.content)
    }

    private fun writeObject(oid: ObjectId, content: ByteArray) {
        val sha1 = oid.hex

        val objPath = pathname
            .resolve(sha1.take(2))
            .resolve(sha1.drop(2))

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

    private fun generateTempName() = "temp_obj_${tempChars.shuffled().joinToString("")}"

}

