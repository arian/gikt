package com.github.arian.gikt

import java.io.Closeable
import java.io.OutputStream
import java.nio.file.*

class Lockfile(private val path: Path) {

    val lockPath: Path = path.run {
        resolveSibling("$fileName.lock")
    }

    fun holdForUpdate(consumer: (Ref) -> Unit = {}): Boolean {
        val ref = try {
            Ref(this)
        } catch (e: FileAlreadyExistsException) {
            null
        } catch (e: NoSuchFileException) {
            throw MissingParent(e.message)
        } catch (e: AccessDeniedException) {
            throw NoPermission(e.message)
        }

        ref?.use(consumer)
        return ref != null
    }

    class Ref internal constructor(private val lockfile: Lockfile) : Closeable {

        val path = lockfile.path
        val lockPath = lockfile.lockPath

        private var stream: OutputStream?
        private var done = false

        init {
            val flags = arrayOf<OpenOption>(
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE_NEW
            )
            stream = Files.newOutputStream(lockPath, *flags)
        }

        override fun close() {
            stream?.close()
            stream = null
        }

        fun write(bytes: String) =
            this.write(bytes.toByteArray())

        fun write(bytes: ByteArray) =
            raiseOnStaleLock { it.write(bytes) }

        fun rollback() {
            raiseOnStaleLock {
                lockPath.delete()
                done = true
            }
        }

        fun commit() {
            raiseOnStaleLock {
                Files.move(lockfile.lockPath, lockfile.path, StandardCopyOption.REPLACE_EXISTING)
                done = true
            }
        }

        private fun raiseOnStaleLock(withBytes: (OutputStream) -> Unit) {
            if (done) {
                stale()
            }
            stream?.let { withBytes(it) } ?: stale()
        }

        private fun stale() {
            throw StaleLock("Not holding lock on file ${lockfile.lockPath}")
        }
    }

    class MissingParent(m: String?) : Exception(m)
    class NoPermission(m: String?) : Exception(m)
    class StaleLock(m: String?) : Exception(m)
}
