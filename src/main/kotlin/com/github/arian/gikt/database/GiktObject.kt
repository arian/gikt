package com.github.arian.gikt.database

import com.github.arian.gikt.sha1
import com.github.arian.gikt.utf8
import java.nio.file.Path

abstract class GiktObject {
    abstract val type: String
    abstract val data: ByteArray
    val oid: ObjectId by lazy { ObjectId(content.sha1()) }
    val content: ByteArray by lazy { "$type ${data.size}".toByteArray() + 0 + data }

    override fun equals(other: Any?): Boolean =
        when (other) {
            is GiktObject -> other.oid == oid
            else -> false
        }

    override fun hashCode(): Int =
        oid.hashCode()

    companion object {
        fun parse(prefix: Path, bytes: ByteArray): GiktObject {
            val typeBytes = bytes
                .takeWhile { it != ' '.toByte() }
                .toByteArray()

            val type = typeBytes.utf8().trim()

            val sizeBytes = bytes
                .drop(typeBytes.size)
                .takeWhile { it != 0.toByte() }
                .toByteArray()

            val start = typeBytes.size + sizeBytes.size + 1
            val content = bytes.sliceArray(start until bytes.size)

            return when (type) {
                "blob" -> Blob.parse(content)
                "tree" -> Tree.parse(prefix, content)
                "commit" -> Commit.parse(content)
                else -> throw IllegalStateException("unknown object type")
            }
        }
    }
}
