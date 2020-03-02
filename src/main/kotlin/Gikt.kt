import java.io.OutputStream
import java.nio.file.FileSystems
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.zip.Deflater
import java.util.zip.DeflaterInputStream
import kotlin.system.exitProcess

fun getPwd(): Path = FileSystems.getDefault().getPath(".").toAbsolutePath().normalize()

fun ByteArray.sha1(): ByteArray {
    val data = this
    return MessageDigest.getInstance("SHA-1").run {
        update(data)
        digest()
    }
}

fun ByteArray.deflateInto(out: OutputStream) {
    val def = DeflaterInputStream(inputStream(), Deflater())
    out.use { def.copyTo(it) }
}

fun main(args: Array<String>) {

    when (val command = args.getOrNull(0)) {

        "init" -> {
            val rootPath = getPwd()
            val gitPath = rootPath.resolve(".git")

            listOf("objects", "refs").forEach {
                val path = gitPath.resolve(it)
                try {
                    path.mkdirp()
                } catch (e: Exception) {
                    System.err.println("fatal: ${e.message}")
                    exitProcess(1)
                }
            }

            println("Initialized empty Gikt repository in $rootPath")
        }

        "commit" -> {
            val rootPath = getPwd()
            val gitPath = rootPath.resolve(".git")
            val dbPath = gitPath.resolve("objects")

            val workspace = Workspace(rootPath)
            val database = Database(dbPath)
            val refs = Refs(gitPath)

            val entries = workspace.listFiles().map {
                val data = workspace.readFile(it)
                val blob = Blob(data)

                database.store(blob)

                Entry(it, it.mode(), blob.oid)
            }

            val tree = Tree.build(rootPath, entries)

            tree.traverse { database.store(it) }

            val parent = refs.readHead()
            val name = System.getenv("GIT_AUTHOR_NAME") ?: error("please set GIT_AUTHOR_NAME")
            val email = System.getenv("GIT_AUTHOR_EMAIL") ?: error("please set GIT_AUTHOR_EMAIL")
            val author = Author(
                name,
                email,
                Instant.now().atZone(Clock.systemDefaultZone().zone)
            )
            val message: ByteArray = System.`in`.readAllBytes()

            val commit = Commit(parent, tree.oid, author, message)
            database.store(commit)
            refs.updateHead(commit.oid)

            val firstLine = message.toString(Charsets.UTF_8).split("\n").getOrNull(0) ?: ""
            val isRoot = parent?.let { "" } ?: "(root-commit) "
            println("[$isRoot${commit.oid.hex} $firstLine")
        }

        else -> {
            System.err.println("gikt: '$command' is not a gikt command")
            exitProcess(1)
        }
    }
}
