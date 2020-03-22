package com.github.arian.gikt

import java.io.Closeable
import java.io.OutputStream
import java.nio.file.*

class Lockfile(private val path: Path) {

    val lockPath: Path = path.run {
        resolveSibling("$fileName.lock")
    }

    fun holdForUpdate(consumer: (Ref) -> Unit = {}): Boolean {
        return try {
            Ref(this).use(consumer)
            true
        } catch (e: FileAlreadyExistsException) {
            false
        } catch (e: NoSuchFileException) {
            throw MissingParent(e.message)
        } catch (e: AccessDeniedException) {
            throw NoPermission(e.message)
        }
    }

    class Ref internal constructor(private val lockfile: Lockfile) : Closeable {

        val path = lockfile.path
        val lockPath = lockfile.lockPath

        private var stream: OutputStream?

        init {
            val flags = arrayOf<OpenOption>(
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE_NEW
            )
            stream = Files.newOutputStream(lockfile.lockPath, *flags)
        }

        override fun close() {
            commit()
        }

        fun write(bytes: String) =
            this.write(bytes.toByteArray())

        fun write(bytes: ByteArray) =
            raiseOnStaleLock { it.write(bytes) }

        private fun commit() {
            raiseOnStaleLock {
                it.close()
                Files.move(lockfile.lockPath, lockfile.path, StandardCopyOption.REPLACE_EXISTING)
                stream = null
            }
        }

        private fun raiseOnStaleLock(withBytes: (OutputStream) -> Unit) {
            stream?.let { withBytes(it) } ?: throw StaleLock("Not holding lock on file ${lockfile.lockPath}")
        }
    }

    class MissingParent(m: String?) : Exception(m)
    class NoPermission(m: String?) : Exception(m)
    class StaleLock(m: String?) : Exception(m)
}
