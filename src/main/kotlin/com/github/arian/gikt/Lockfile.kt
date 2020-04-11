package com.github.arian.gikt

import java.io.Closeable
import java.io.OutputStream
import java.nio.file.AccessDeniedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

class Lockfile(private val path: Path) {

    val lockPath: Path = path.run {
        resolveSibling("$fileName.lock")
    }

    fun holdForUpdate(consumer: (Ref) -> Unit = {}): Boolean {
        val ref = try {
            Ref(this)
        } catch (e: FileAlreadyExistsException) {
            throw LockDenied("Unable to create '$lockPath': File exists.")
        } catch (e: NoSuchFileException) {
            throw MissingParent(e.message)
        } catch (e: AccessDeniedException) {
            throw NoPermission(e.message)
        }

        ref.use {
            var throwable: Throwable? = null
            try {
                consumer(it)
            } catch (e: Throwable) {
                throwable = e
                throw e
            } finally {
                if (!it.done) {
                    throw StaleLock("Neither `commit` or `rollback` called with open lock", throwable)
                }
            }
        }

        return true
    }

    class Ref internal constructor(private val lockfile: Lockfile) : Closeable {

        val path = lockfile.path
        val lockPath = lockfile.lockPath

        private var stream: OutputStream?
        internal var done = false

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
                it.close()
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
    class StaleLock(m: String?, cause: Throwable? = null) : Exception(m, cause)
    class LockDenied(m: String) : Exception(m)
}
