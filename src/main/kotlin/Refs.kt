import java.nio.file.Path

class Refs(pathname: Path) {

    private val headPath = pathname.resolve("HEAD")

    fun updateHead(oid: ObjectId) {
        val lockfile = Lockfile(headPath)

        if (!lockfile.holdForUpdate()) {
            throw LockDenied("Could not acquire lock on file: $headPath")
        }

        lockfile.write(oid.hex)
        lockfile.write("\n")
        lockfile.commit()
    }

    fun readHead(): ObjectId? =
        headPath
            .takeIf { it.exists() }
            ?.readText()
            ?.let { ObjectId(it.trim())}

    class LockDenied(m: String) : Exception(m)

}
