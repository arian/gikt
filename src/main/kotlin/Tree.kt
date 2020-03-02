import java.nio.charset.Charset
import java.nio.file.Path
import kotlin.math.max

private val nullByteArray = ByteArray(1).also { it[0] = 0 }

interface TreeEntry {
    val name: Path
    val mode: Mode
    val oid: ObjectId
}

class Tree(
    override val name: Path,
    private val entries: MutableMap<String, TreeEntry> = mutableMapOf()
) : GiktObject(), TreeEntry {

    override fun toString(): String {
        return String(data, Charset.defaultCharset())
    }

    override val type = "tree"
    override val mode = Mode.TREE
    override val data: ByteArray by lazy {
        entries
            .values
            .sortedBy { it.name }
            .flatMap { entry ->
                listOf(
                    entry.mode.mode.toByteArray() + ' '.toByte(), // .toByteArrayPaddedRight(size = 7, padding = ' '),
                    entry.name.relativeTo(name).toString().toByteArray(),
                    nullByteArray,
                    entry.oid.bytes
                )
            }
            .reduceRight(ByteArray::plus)
    }

    fun addEntry(parents: Parents?, entry: TreeEntry) {
        if (parents == null || parents.isEmpty()) {
            entries[entry.name.toAbsolutePath().toString()] = entry
        } else {
            val dirname = name.resolve(parents.first())
            val dirString = dirname.toAbsolutePath().toString()
            val tree = entries.getOrElse(dirString) { Tree(dirname) }
            if (tree is Tree) {
                tree.addEntry(parents.tail(), entry)
                entries[dirString] = tree
            }
        }
    }

    fun traverse(fn: (Tree) -> Unit) {
        entries.values.mapNotNull { it as? Tree }.forEach { it.traverse(fn) }
        fn(this)
    }

    fun list() : List<String> =
        entries
            .values
            .sortedBy { it.name }
            .map { it.name.relativeTo(name).toString() }

    operator fun get(key: String): TreeEntry? = entries[name.resolve(key).toAbsolutePath().toString()]

    fun getTree(key: String): Tree? = entries[name.resolve(key).toAbsolutePath().toString()] as? Tree

    companion object {
        fun build(path: Path, paths: List<Entry>): Tree {

            val entries = paths.sortedBy { it.name.toString() }
            val root = Tree(path)

            entries
                .forEach { e ->
                    val p = e.name
                    val r = p.relativeTo(path)
                    val names = (0 until r.nameCount).map { r.getName(it) }
                    val parents = names.dropLast(1)
                    root.addEntry(Parents(parents), e)
                }

            return root
        }
    }

    data class Parents(val ps: List<Path> = emptyList()) {
        fun first(): Path = ps.first()
        fun tail() = Parents(ps.drop(1))
        fun isEmpty() = ps.isEmpty()
    }

}
