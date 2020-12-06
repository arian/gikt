package com.github.arian.gikt.database

import com.github.arian.gikt.utf8
import java.time.ZonedDateTime
import java.util.Scanner

data class Commit(
    val parents: List<ObjectId>,
    val tree: ObjectId,
    val author: Author,
    val message: ByteArray
) : GiktObject() {

    override val type = "commit"

    override val data: ByteArray by lazy {
        val parentString = parents.joinToString(separator = "") { "parent $it\n" }

        """
        |tree $tree
        |${parentString}author $author
        |committer $author
        |
        |${message.utf8()}
        """.trimMargin().toByteArray(Charsets.UTF_8)
    }

    val parent: ObjectId?
        get() =
            parents.firstOrNull()

    val title: String
        get() =
            message.utf8().lineSequence().first()

    val date: ZonedDateTime
        get() =
            author.date

    override fun equals(other: Any?): Boolean =
        super.equals(other)

    override fun hashCode(): Int =
        super.hashCode()

    override fun toString(): String =
        "Commit(oid=$oid)"

    companion object {

        fun parse(bytes: ByteArray): Commit {

            val lines = sequence {
                val scanner = Scanner(bytes.inputStream())
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
                .groupBy(
                    keySelector = { (key, _) -> key },
                    valueTransform = { (_, value) -> value }
                )

            val start = lines
                .takeWhile { it.isNotBlank() }
                .fold(1) { acc, s -> acc + s.length + 1 }

            val tree = headers["tree"]
                ?.firstOrNull()
                ?.let { ObjectId(it) }
                ?: throw IllegalStateException("Couldn't parse commit: missing 'tree' field")

            val author = headers["author"]
                ?.firstOrNull()
                ?.let { Author.parse(it) }
                ?: throw IllegalStateException("Couldn't parse commit: invalid 'author' field")

            val message = bytes.sliceArray(start until bytes.size)

            return Commit(
                parents = headers["parent"]?.map { ObjectId(it) } ?: emptyList(),
                tree = tree,
                author = author,
                message = message
            )
        }
    }
}
