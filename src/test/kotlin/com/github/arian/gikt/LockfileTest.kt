package com.github.arian.gikt

import com.github.arian.gikt.test.FileSystemExtension
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(FileSystemExtension::class)
class LockfileTest(private val fileSystemProvider: FileSystemExtension.FileSystemProvider) {

    private lateinit var path: Path

    @BeforeEach
    fun before() {
        val fs = fileSystemProvider.get()
        path = fs.getPath("temp")
        Files.createDirectory(path)
    }

    @Test
    fun holdForUpdate() {
        val file = path.resolve("a.txt").touch()
        val lock = Lockfile(file)
        val success = lock.holdForUpdate {
            assertEquals(path.resolve("a.txt.lock"), lock.lockPath)
            it.rollback()
            true
        }
        assertTrue(success)
    }

    @Test
    fun writeShouldHoldLockFirst() {
        val file = path.resolve("a.txt").touch()
        var ref: Lockfile.Ref? = null
        Lockfile(file).holdForUpdate {
            ref = it
            it.rollback()
        }
        assertThrows<Lockfile.StaleLock> { ref?.write("hello") }
    }

    @Test
    fun holdLockAndWrite() {
        val file = path.resolve("a.txt").touch()
        Lockfile(file).holdForUpdate {
            it.write("hello")
            it.commit()
        }
        assertEquals("hello", file.readText())
    }

    @Test
    fun missingParent() {
        val file = path.resolve("a/b/c.txt")
        val lock = Lockfile(file)
        assertThrows<Lockfile.MissingParent> { lock.holdForUpdate { } }
    }

    @Test
    fun `cannot commit twice`() {
        val file = path.resolve("a.txt").touch()
        Lockfile(file).holdForUpdate {
            it.write("hello")
            it.commit()
            assertThrows<Lockfile.StaleLock> { it.commit() }
        }
    }

    @Test
    fun `rollback should ignore the written values`() {
        val file = path.resolve("a.txt").touch()
        Lockfile(file).holdForUpdate {
            it.write("hello")
            it.rollback()
        }
        assertEquals("", file.readText())
    }

    @Test
    fun `rollback after commit should throw`() {
        val file = path.resolve("a.txt").touch()
        Lockfile(file).holdForUpdate {
            it.write("hello")
            it.commit()
            assertThrows<Lockfile.StaleLock> { it.rollback() }
        }
    }

    @Test
    fun `rollback neither commit called`() {
        val file = path.resolve("a.txt").touch()
        assertThrows<Lockfile.StaleLock> {
            Lockfile(file).holdForUpdate {
                it.write("hello")
            }
        }
    }

    @Test
    fun `already exists`() {
        val file = path.resolve("a.txt").touch()
        path.resolve("a.txt.lock").touch()
        val lock = Lockfile(file)
        assertThrows<Lockfile.LockDenied> { lock.holdForUpdate {} }
    }

    @Test
    fun `nested lock should not be called`() {
        val file = path.resolve("a.txt").touch()
        val lock = Lockfile(file)

        val one = AtomicBoolean(false)

        lock.holdForUpdate {
            one.set(true)
            assertThrows<Lockfile.LockDenied> { lock.holdForUpdate {} }
            it.rollback()
        }

        assertTrue(one.get())
    }
}
