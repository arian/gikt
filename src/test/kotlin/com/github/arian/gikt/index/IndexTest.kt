package com.github.arian.gikt.index

import com.github.arian.gikt.*
import com.github.arian.gikt.database.Blob
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.database.toHexString
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

class IndexTest {

    private lateinit var workspacePath: Path

    @BeforeEach
    fun before() {
        val fs = Jimfs.newFileSystem(Configuration.unix().toBuilder().setAttributeViews("unix").build())
        workspacePath = fs.getPath("gitk-index")
        Files.createDirectory(workspacePath)
    }

    @Test
    fun createIndex() {
        val indexPath = workspacePath.resolve("index")
        val index = Index(workspacePath, indexPath)
        val path = workspacePath.resolve("a.txt").touch()

        val called = AtomicBoolean(false)

        index.loadForUpdate { lock ->
            called.set(true)

            index.add(
                path,
                ObjectId("1234512345123451234512345123451234512345"),
                FileStat(executable = false)
            )

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
        val index = Index(workspacePath, workspacePath.resolve("index"))
        workspacePath.resolve("index.lock").touch()

        val called = AtomicBoolean(false)

        index.loadForUpdate {
            called.set(true)
        }

        assertFalse(called.get())
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
        val index = Index(workspacePath, indexPath)

        index.loadForUpdate { lock ->

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

                val stat = path.stat()
                val data = path.readBytes()
                val blob = Blob(data)
                index.add(path, blob.oid, stat)
            }

            index.writeUpdates(lock)
        }

        val called = AtomicBoolean(false)

        index.loadForUpdate {
            called.set(true)
            it.rollback()
        }

        assertTrue(called.get())
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
