package com.github.arian.gikt.index

import com.github.arian.gikt.FileStat
import com.github.arian.gikt.Lockfile
import com.github.arian.gikt.database.Blob
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.database.toHexString
import com.github.arian.gikt.mkdirp
import com.github.arian.gikt.readBytes
import com.github.arian.gikt.relativeTo
import com.github.arian.gikt.stat
import com.github.arian.gikt.touch
import com.github.arian.gikt.write
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class IndexTest {

    private lateinit var workspacePath: Path

    private val oid = ObjectId("1234512345123451234512345123451234512345")
    private val stat = FileStat(executable = false)

    @BeforeEach
    fun before() {
        val fs = Jimfs.newFileSystem(Configuration.unix().toBuilder().setAttributeViews("unix").build())
        workspacePath = fs.getPath("/gitk-index")
        Files.createDirectory(workspacePath)
    }

    private fun rel(path: String) = workspacePath.resolve(path).relativeTo(workspacePath)

    private fun rel(path: Path) = path.relativeTo(workspacePath)

    @Test
    fun `new index`() {
        val indexPath = workspacePath.resolve("index")
        val index = Index(indexPath)

        index.add(rel("alice.txt"), oid, stat)
        index.add(rel("bob.txt"), oid, stat)

        assertEquals(listOf("alice.txt", "bob.txt"), index.toList().map { it.key })
    }

    @Test
    fun `replaces a file with a directory`() {
        val index = Index(workspacePath.resolve("index"))

        index.add(rel("alice.txt"), oid, stat)
        index.add(rel("bob.txt"), oid, stat)
        index.add(rel("alice.txt/nested.txt"), oid, stat)

        assertEquals(listOf("alice.txt/nested.txt", "bob.txt"), index.toList().map { it.key })
    }

    @Test
    fun `replaces a directory with a file`() {
        val index = Index(workspacePath.resolve("index"))

        index.add(rel("alice.txt"), oid, stat)
        index.add(rel("nested/bob.txt"), oid, stat)
        index.add(rel("nested"), oid, stat)

        assertEquals(listOf("alice.txt", "nested"), index.toList().map { it.key })
    }

    @Test
    fun `recursively replaces a directory with a file`() {
        val index = Index(workspacePath.resolve("index"))

        index.add(rel("alice.txt"), oid, stat)
        index.add(rel("nested/bob.txt"), oid, stat)
        index.add(rel("nested/inner/claire.txt"), oid, stat)

        index.add(rel("nested"), oid, stat)

        assertEquals(listOf("alice.txt", "nested"), index.toList().map { it.key })
    }

    @Test
    fun createIndex() {
        val indexPath = workspacePath.resolve("index")
        val index = Index(indexPath)
        val path = workspacePath.resolve("a.txt").touch()

        val called = AtomicBoolean(false)

        index.loadForUpdate { lock ->
            called.set(true)
            index.add(rel(path), oid, stat)
            index.writeUpdates(lock)
        }

        assertTrue(called.get())

        val result = indexPath.readBytes()
        val resultString = result.toString(Charsets.UTF_8)
        assertEquals("DIRC", resultString.substring(0, 4))

        assertEquals(
            "44 49 52 43 00 00 00 02 00 00 00 01".replace(" ", ""),
            result.toHexString().substring(0, 24)
        )

        assertTrue(resultString.contains("a.txt"))
    }

    @Test
    fun `return false when index is locked`() {
        val index = Index(workspacePath.resolve("index"))
        workspacePath.resolve("index.lock").touch()
        assertThrows<Lockfile.LockDenied> { index.loadForUpdate { } }
    }

    @Test
    fun `entry content bytes`() {

        listOf(
            "src/entry.kt",
            "src/index.kt",
            "src/workspace.kt",
            "test/entry.kt",
            "test/index.kt",
            "test/refs.kt",
            "test/lockfile.kt"
        ).forEach {
            val path = workspacePath.resolve(it)
            path.parent.mkdirp()
            path.write(it)

            val entry = Entry(
                path.relativeTo(workspacePath),
                ObjectId("1234512345123451234512345123451234512345"),
                FileStat(executable = true)
            )

            assertEquals(it, entry.key)
            assertEquals(0, entry.content.size % 8)
            assertEquals(0.toByte(), entry.content.last())
        }
    }

    @Test
    fun addMultipleFiles() {
        val indexPath = workspacePath.resolve("index")
        val index = Index(indexPath)

        val fileNames = listOf(
            "src/entry.kt",
            "src/index.kt",
            "src/workspace.kt",
            "test/entry.kt",
            "test/index.kt",
            "test/refs.kt",
            "test/lockfile.kt"
        )

        index.loadForUpdate { lock ->

            fileNames.forEach {
                val path = workspacePath.resolve(it)
                path.parent.mkdirp()
                path.write(it)

                val stat = path.stat()
                val data = path.readBytes()
                val blob = Blob(data)
                index.add(rel(path), blob.oid, stat)
            }

            index.writeUpdates(lock)
        }

        val list = index.load().toList().map { it.key }

        assertEquals(
            listOf(
                "src/entry.kt",
                "src/index.kt",
                "src/workspace.kt",
                "test/entry.kt",
                "test/index.kt",
                "test/lockfile.kt",
                "test/refs.kt"
            ), list
        )
    }

    @Test
    fun `entry content ends with zero-byte`() {
        val path = workspacePath.resolve("a")

        val entry = Entry(
            path.relativeTo(workspacePath),
            ObjectId("1234512345123451234512345123451234512345"),
            FileStat(executable = true)
        )

        val bytes = entry.content

        assertEquals(0, bytes.size % 8)
        assertEquals(0.toByte(), bytes.last())
    }

    @Test
    fun `write and parse entry`() {
        val path = workspacePath.resolve("a")

        val entry = Entry(
            path.relativeTo(workspacePath),
            ObjectId("1234512345123451234512345123451234512345"),
            FileStat(executable = true)
        )

        val bytes = entry.content

        val parsed = Entry.parse(bytes)

        assertEquals(entry.key, parsed.key)
        assertEquals(entry.oid, parsed.oid)
    }
}
