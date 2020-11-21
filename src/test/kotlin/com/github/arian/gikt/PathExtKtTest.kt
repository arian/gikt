package com.github.arian.gikt

import com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.AccessDeniedException
import java.nio.file.AccessMode
import java.nio.file.Files
import java.nio.file.Path

internal class PathExtKtTest {

    @Test
    fun relativeTo() {
        assertEquals(
            Path.of("123/xyz"),
            Path.of("/abc/123/xyz").relativeTo(Path.of("/abc"))
        )
    }

    @Test
    fun parents() {
        assertEquals(
            listOf("a", "b", "c", "d"),
            Path.of("a/b/c/d/e").parents().map { it.toString() }
        )
    }

    @Test
    fun `parents from root`() {
        assertEquals(
            listOf("a", "b", "c", "d"),
            Path.of("/a/b/c/d/e").parents().map { it.toString() }
        )
    }

    @Test
    fun split() {
        assertEquals(
            listOf("a", "b", "c", "d", "e"),
            Path.of("a/b/c/d/e").split().map { it.toString() }
        )
    }

    @Test
    fun `split for empty path`() {
        assertEquals(
            listOf(""),
            Path.of("").split().map { it.toString() }
        )
    }

    @Test
    fun `split for root path`() {
        assertEquals(
            emptyList<String>(),
            Path.of("/").split().map { it.toString() }
        )
    }

    @Test
    fun parentsPaths() {
        assertEquals(
            listOf("a", "a/b", "a/b/c", "a/b/c/d"),
            Path.of("a/b/c/d/e").parentPaths().map { it.toString() }
        )
    }

    @Test
    fun `parentsPaths from root`() {
        assertEquals(
            listOf("/", "/a", "/a/b", "/a/b/c", "/a/b/c/d"),
            Path.of("/a/b/c/d/e").parentPaths().map { it.toString() }
        )
    }

    @Test
    fun `parentsPaths without parents`() {
        assertEquals(
            emptyList<String>(),
            Path.of("e").parentPaths().map { it.toString() }
        )
    }

    @Test
    fun readBytes() {
        val fs = MemoryFileSystemBuilder.newLinux().build()
        val bytes = "foobar".toByteArray()
        val path = fs.getPath("file.txt").write(bytes)
        assertTrue(bytes.contentEquals(path.readBytes()))
        path.delete()
    }

    @Test
    fun readText() {
        val fs = MemoryFileSystemBuilder.newLinux().build()
        val path = fs.getPath("file.txt").write("hello")
        assertEquals("hello", path.readText())
        path.delete()
    }

    @Test
    fun makeExecutable() {
        val fs = MemoryFileSystemBuilder.newLinux().build()
        val dir = Files.createDirectory(fs.getPath("source-directory"))
        val path = dir.resolve("file.txt").touch()
        path.makeExecutable()
        assertTrue(Files.isExecutable(path))
    }

    @Test
    fun makeUnExecutable() {
        val fs = MemoryFileSystemBuilder.newLinux().build()
        val dir = Files.createDirectory(fs.getPath("source-directory"))
        val path = dir.resolve("file.txt").touch()
        path.makeUnExecutable()
        assertFalse(Files.isExecutable(path))
    }

    @Test
    fun makeUnreadable() {
        val fs = MemoryFileSystemBuilder.newLinux().build()
        val dir = Files.createDirectory(fs.getPath("source-directory"))
        val path = dir.resolve("file.txt").touch()
        path.makeUnreadable()
        assertThrows<AccessDeniedException> {
            path.checkAccess(AccessMode.READ)
        }
    }
}
