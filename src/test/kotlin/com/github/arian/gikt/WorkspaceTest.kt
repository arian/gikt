package com.github.arian.gikt

import com.github.arian.gikt.test.FileSystemExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.nio.file.Path

@ExtendWith(FileSystemExtension::class)
class WorkspaceTest(private val fileSystemProvider: FileSystemExtension.FileSystemProvider) {

    private lateinit var path: Path

    @BeforeEach
    fun before() {
        path = fileSystemProvider.get().getPath("gitk-workspace").createDirectory()
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
                "a.txt",
                "a/b/c/d.txt",
                "a/d.txt",
                "b.txt"
            ),
            files.sorted()
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

    @Test
    fun `listFiles throws for non-existing file`() {
        val workspace = Workspace(path)
        val exception = assertThrows<Workspace.MissingFile> {
            workspace.listFiles(path.resolve("blabla"))
        }
        assertEquals("pathspec 'blabla' did not match any files", exception.message)
    }

    @Test
    fun `listDir lists files in a directory`() {
        path.resolve("a.txt").touch()
        path.resolve("b.txt").touch()
        path.resolve(".git").mkdirp()
        path.resolve("a/b").mkdirp()

        val workspace = Workspace(path)

        val files = workspace.listDir()
            .map { (p, stat) -> p.toString() to stat.directory }
            .toMap()
            .toSortedMap()

        assertEquals(
            mapOf(
                "a" to true,
                "a.txt" to false,
                "b.txt" to false
            ),
            files
        )
    }

    @Test
    fun `listDir lists files in a directory with argument, result path are relative to the root`() {
        path.resolve("lib").mkdirp()
        path.resolve("lib/a.txt").touch()
        path.resolve("lib/b.txt").touch()
        path.resolve("lib/.git").mkdirp()
        path.resolve("lib/a/b").mkdirp()

        val workspace = Workspace(path)

        val files = workspace.listDir(path.resolve("lib").relativeTo(path))
            .map { (p, _) -> p.toString() }

        assertEquals(
            listOf("lib/a", "lib/a.txt", "lib/b.txt"),
            files
        )
    }

    @Test
    fun `readFile throws exception when access denied`() {
        val workspace = Workspace(path)
        val file = path.resolve("secret.txt").touch().makeUnreadable()

        val exception = assertThrows<Workspace.NoPermission> {
            workspace.readFile(file.relativeTo(path))
        }

        assertEquals("open('secret.txt'): Permission denied", exception.message)
    }

    @Test
    fun `readFile by string filename`() {
        val workspace = Workspace(path)
        path.resolve("world.txt").write("hello")

        assertEquals(
            "hello",
            workspace.readFile("world.txt").toString(Charsets.UTF_8)
        )
    }

    @Test
    fun `statFile throws exception when access denied`() {
        val workspace = Workspace(path)
        val file = path.resolve("secret.txt").touch().makeUnreadable()

        val exception = assertThrows<Workspace.NoPermission> {
            workspace.statFile(file.relativeTo(path))
        }

        assertEquals("stat('secret.txt'): Permission denied", exception.message)
    }
}
