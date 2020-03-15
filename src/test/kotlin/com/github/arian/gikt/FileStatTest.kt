package com.github.arian.gikt

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFileAttributes
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileStatTest{


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
}