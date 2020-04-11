package com.github.arian.gikt

import com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FileStatTest {

    private lateinit var path: Path

    @BeforeEach
    fun before() {
        path = Files.createTempDirectory("gikt")
    }

    @AfterEach
    fun after() {
        path.deleteRecursively()
    }

    @Test
    fun readUnixFileStat() {
        val file = Files.createFile(path.resolve("a"))
        file.write("hello")

        val stat = FileStat.of(file)

        // executable
        assertFalse(stat.executable)

        // ctime
        val time = FileTime.fromMillis(stat.ctime * 1000 + (stat.ctimeNS / 1e6).toLong()).toInstant()
        val timeDiff = Duration.between(time, Instant.now()).seconds
        assertTrue(timeDiff < 5)

        // mtime
        assertTrue(Instant.now().epochSecond - stat.mtime < 5)

        // size
        assertEquals(stat.size, 5)
    }

    @Test
    fun executable() {
        val fs = MemoryFileSystemBuilder.newLinux().build()
        val path = Files.createDirectory(fs.getPath("gikt"))

        val file = Files.createFile(path.resolve("a"))
            .write("hellp")
            .makeExecutable()

        assertTrue(FileStat.of(file).executable)
    }

    @Test
    fun readWhenWindows() {
        val fs = MemoryFileSystemBuilder.newWindows().build()
        val path = Files.createDirectory(fs.getPath("gikt-index"))
        val file = Files.createFile(path.resolve("a")).write("hello")

        val stat = FileStat.of(file)

        assertFalse(stat.executable)

        // ctime
        val time = FileTime.fromMillis(stat.ctime * 1000 + (stat.ctimeNS / 1e6).toLong()).toInstant()
        val timeDiff = Duration.between(time, Instant.now()).seconds
        assertTrue(timeDiff < 5)

        // mtime
        assertTrue(Instant.now().epochSecond - stat.mtime < 5)

        // size
        assertEquals(stat.size, 5)
    }
}
