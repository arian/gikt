package com.github.arian.gikt

import com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder
import java.nio.file.AccessDeniedException
import java.nio.file.AccessMode
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

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
    fun parentsPaths() {
        assertEquals(
            listOf("a", "a/b", "a/b/c", "a/b/c/d"),
            Path.of("a/b/c/d/e").parentPaths().map { it.toString() }
        )
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
