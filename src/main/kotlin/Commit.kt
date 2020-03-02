class Commit(
    parent: ObjectId?,
    tree: ObjectId,
    author: Author,
    message: ByteArray
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
}
