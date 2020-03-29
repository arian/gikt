package com.github.arian.gikt

import com.google.common.jimfs.Jimfs
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class WorkspaceTest {

    private lateinit var path: Path

    @BeforeEach
    fun before() {
        val fs = Jimfs.newFileSystem()
        path = fs.getPath("gitk-workspace")
        Files.createDirectory(path)
    }

    @Test
    @Suppress("UNUSED_VARIABLE")
    fun listFiles() {
        val a = path.resolve("a.txt").touch()
        val b = path.resolve("b.txt").touch()
        path.resolve(".git").mkdirp()
        path.resolve(".git/HEAD").touch()

        val workspace = Workspace(path)

        val files = workspace.listFiles().map { it.toString() }

        assertEquals(
            listOf("a.txt", "b.txt"),
            files
        )
    }

    @Test
    @Suppress("UNUSED_VARIABLE")
    fun listFilesRecursive() {
        path.resolve(".git").mkdirp()
        path.resolve(".git/HEAD").touch()

        val a = path.resolve("a.txt").touch()
        val b = path.resolve("b.txt").touch()
        path.resolve("a/b/c").mkdirp()
        val c = path.resolve("a/d.txt").touch()
        val d = path.resolve("a/b/c/d.txt").touch()

        val workspace = Workspace(path)
        val files = workspace.listFiles().map { it.toString() }

        assertEquals(
            listOf(
                "a/b/c/d.txt",
                "a/d.txt",
                "a.txt",
                "b.txt"
            ),
            files
        )
    }

    @Test
    @Suppress("UNUSED_VARIABLE")
    fun `listFiles with directory as parameter`() {
        path.resolve(".git").mkdirp()
        path.resolve(".git/HEAD").touch()

        val a = path.resolve("a.txt").touch()
        val b = path.resolve("b.txt").touch()

        val workspace = Workspace(path)
        val files = workspace.listFiles(path.resolve(".")).map { it.toString() }

        assertEquals(
            listOf("a.txt", "b.txt"),
            files
        )
    }

    @Test
    @Suppress("UNUSED_VARIABLE")
    fun `listFiles list single file`() {
        val a = path.resolve("a.txt").touch()

        val workspace = Workspace(path)
        val files = workspace.listFiles(path.resolve("./a.txt")).map { it.toString() }

        assertEquals(listOf("a.txt"), files)
    }

    @Test
    fun `listFiles ignore ignored file`() {
        val workspace = Workspace(path)
        val files = workspace.listFiles(path.resolve(".git")).map { it.toString() }
        assertEquals(emptyList<String>(), files)
    }
}
