import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.file.*

class Lockfile(private val path: Path) {

    val lockPath = path.run {
        resolveSibling("${fileName.toString()}.lock")
    }

    var lock: SeekableByteChannel? = null

    fun holdForUpdate(): Boolean {
        return try {
            if (lock == null) {
                val flags = arrayOf<OpenOption>(
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.CREATE_NEW
                )
                lock = Files.newByteChannel(lockPath, *flags)
            }
            true
        } catch (e: FileAlreadyExistsException) {
            false
        } catch (e : NoSuchFileException) {
            throw MissingParent(e.message)
        } catch (e: AccessDeniedException) {
            throw NoPermission(e.message)
        }
    }

    fun write(bytes: String) =
        write(ByteBuffer.wrap(bytes.toByteArray()))

    fun write(bytes: ByteArray) =
        write(ByteBuffer.wrap(bytes))

    private fun write(byteBuffer: ByteBuffer) =
        raiseOnStaleLock { write(byteBuffer) }

    fun commit() {
        raiseOnStaleLock {
            close()
            Files.move(lockPath, path, StandardCopyOption.REPLACE_EXISTING)
            lock = null
        }
    }

    private fun raiseOnStaleLock(withBytes: SeekableByteChannel.() -> Unit) {
        lock?.withBytes() ?: throw StaleLock("Not holding lock on file $lockPath")
    }

    class MissingParent(m: String?) : Exception(m)
    class NoPermission(m: String?) : Exception(m)
    class StaleLock(m: String?) : Exception(m)
}
