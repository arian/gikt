package com.github.arian.gikt.database

import java.io.ByteArrayInputStream
import java.util.Scanner

data class Commit(
    val parent: ObjectId?,
    val tree: ObjectId,
    val author: Author,
    val message: ByteArray
) : GiktObject() {

    override val type = "commit"

    override val data: ByteArray by lazy {
        val parentString = parent?.let { "parent $it\n" } ?: ""

        """
        |tree $tree
        |${parentString}author $author
        |committer $author
        |
        |${message.toString(Charsets.UTF_8)}
        """.trimMargin().toByteArray(Charsets.UTF_8)
    }

    override fun equals(other: Any?): Boolean =
        super.equals(other)

    override fun hashCode(): Int =
        super.hashCode()

    companion object {

        fun parse(bytes: ByteArray): Commit {

            val lines = sequence {
                val scanner = Scanner(ByteArrayInputStream(bytes))
                while (scanner.hasNextLine()) {
                    yield(scanner.nextLine())
                }
            }

            val headers = lines
                .takeWhile { it.isNotBlank() }
                .map {
                    val item = it.split(" ", limit = 2)
                    item.first() to item.last()
                }
                .toMap()

            val start = lines
                .takeWhile { it.isNotBlank() }
                .fold(1) { acc, s -> acc + s.length + 1 }

            val tree = headers["tree"]
                ?.let { ObjectId(it) }
                ?: throw IllegalStateException("Couldn't parse commit: missing 'tree' field")

            val author = headers["author"]
                ?.let { Author.parse(it) }
                ?: throw IllegalStateException("Couldn't parse commit: invalid 'author' field")

            val message = bytes.sliceArray(start until bytes.size)

            return Commit(
                parent = headers["parent"]?.let { ObjectId(it) },
                tree = tree,
                author = author,
                message = message
            )
        }
    }
}
