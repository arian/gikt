package com.github.arian.gikt

import com.google.common.jimfs.Jimfs
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class LockfileTest {

    private lateinit var path: Path

    @BeforeEach
    fun before() {
        val fs = Jimfs.newFileSystem()
        path = fs.getPath("temp")
        Files.createDirectory(path)
    }

    @Test
    fun holdForUpdate() {
        val file = path.resolve("a.txt").touch()
        val lock = Lockfile(file)
        val success = lock.holdForUpdate {
            assertEquals(path.resolve("a.txt.lock"), lock.lockPath)
        }
        assertTrue(success)
    }

    @Test
    fun writeShouldHoldLockFirst() {
        val file = path.resolve("a.txt").touch()
        var ref: Lockfile.Ref? = null
        Lockfile(file).holdForUpdate {
            ref = it
        }
        assertThrows<Lockfile.StaleLock> { ref?.write("hello") }
    }

    @Test
    fun holdLockAndWrite() {
        val file = path.resolve("a.txt").touch()
        Lockfile(file).holdForUpdate {
            it.write("hello")
        }
        assertEquals("hello", file.readText())
    }

    @Test
    fun missingParent() {
        val file = path.resolve("a/b/c.txt")
        val lock = Lockfile(file)
        assertThrows<Lockfile.MissingParent> { lock.holdForUpdate() }
    }

    @Test
    fun `already exists`() {
        val file = path.resolve("a.txt").touch()
        path.resolve("a.txt.lock").touch()
        val lock = Lockfile(file)
        assertFalse(lock.holdForUpdate())
    }
}
