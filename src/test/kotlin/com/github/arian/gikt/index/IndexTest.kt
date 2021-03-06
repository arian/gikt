package com.github.arian.gikt.index

import com.github.arian.gikt.FileStat
import com.github.arian.gikt.Lockfile
import com.github.arian.gikt.Mode
import com.github.arian.gikt.createDirectory
import com.github.arian.gikt.database.Blob
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.database.ParsedTreeEntry
import com.github.arian.gikt.database.toHexString
import com.github.arian.gikt.delete
import com.github.arian.gikt.exists
import com.github.arian.gikt.mkdirp
import com.github.arian.gikt.readBytes
import com.github.arian.gikt.relativeTo
import com.github.arian.gikt.stat
import com.github.arian.gikt.test.FileSystemExtension
import com.github.arian.gikt.touch
import com.github.arian.gikt.write
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import com.github.arian.gikt.database.Entry as DbEntry

@ExtendWith(FileSystemExtension::class)
class IndexTest(private val fileSystemProvider: FileSystemExtension.FileSystemProvider) {

    private lateinit var workspacePath: Path

    private val oid = ObjectId("1234512345123451234512345123451234512345")
    private val stat = FileStat(executable = false)

    @BeforeEach
    fun before() {
        val fs = fileSystemProvider.get()
        workspacePath = fs.getPath("/gitk-index").createDirectory()
    }

    private fun rel(path: String) = workspacePath.resolve(path).relativeTo(workspacePath)

    private fun rel(path: Path) = path.relativeTo(workspacePath)

    @Test
    fun `new index`() {
        val indexPath = workspacePath.resolve("index")
        val index = Index(indexPath)

        index.loadForUpdate {
            add(rel("alice.txt"), oid, stat)
            add(rel("bob.txt"), oid, stat)
            add(rel("nested/inner/clara.txt"), oid, stat)
            writeUpdates()
        }

        val loaded = index.load()
        assertEquals(listOf("alice.txt", "bob.txt", "nested/inner/clara.txt"), loaded.toList().map { it.name })
        assertTrue(loaded.tracked(rel("alice.txt")))
        assertTrue(loaded.tracked(rel("bob.txt")))
        assertTrue(loaded.tracked(rel("nested/inner")))
    }

    @Test
    fun `replaces a file with a directory`() {
        val index = Index(workspacePath.resolve("index"))

        index.loadForUpdate {
            add(rel("alice.txt"), oid, stat)
            add(rel("bob.txt"), oid, stat)
            add(rel("alice.txt/nested.txt"), oid, stat)
            writeUpdates()
        }

        assertEquals(listOf("alice.txt/nested.txt", "bob.txt"), index.load().toList().map { it.name })
    }

    @Test
    fun `replaces a directory with a file`() {
        val index = Index(workspacePath.resolve("index"))

        index.loadForUpdate {
            add(rel("alice.txt"), oid, stat)
            add(rel("nested/bob.txt"), oid, stat)
            add(rel("nested"), oid, stat)
            writeUpdates()
        }

        assertEquals(listOf("alice.txt", "nested"), index.load().toList().map { it.name })
    }

    @Test
    fun `recursively replaces a directory with a file`() {
        val index = Index(workspacePath.resolve("index"))

        index.loadForUpdate {
            add(rel("alice.txt"), oid, stat)
            add(rel("nested/bob.txt"), oid, stat)
            add(rel("nested/inner/claire.txt"), oid, stat)

            add(rel("nested"), oid, stat)

            writeUpdates()
        }

        assertEquals(listOf("alice.txt", "nested"), index.load().toList().map { it.name })
    }

    @Test
    fun createIndex() {
        val indexPath = workspacePath.resolve("index")
        val index = Index(indexPath)
        val path = workspacePath.resolve("a.txt").touch()

        val called = AtomicBoolean(false)

        index.loadForUpdate {
            called.set(true)
            add(rel(path), oid, stat)
            writeUpdates()
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
    fun `remove a file from the index`() {
        val index = Index(workspacePath.resolve("index"))

        index.loadForUpdate {
            add(rel("alice.txt"), oid, stat)
            add(rel("nested/bob.txt"), oid, stat)
            writeUpdates()
        }

        index.loadForUpdate {
            remove(rel("nested/bob.txt"))
            writeUpdates()
        }

        assertEquals(listOf("alice.txt"), index.load().toList().map { it.name })
    }

    @Test
    fun `return false when index is locked`() {
        val index = Index(workspacePath.resolve("index"))
        workspacePath.resolve("index.lock").touch()
        assertThrows<Lockfile.LockDenied> { index.loadForUpdate { } }
    }

    @Test
    fun `index is closed and delete-able`() {
        val indexPath = workspacePath.resolve("index")
        val index = Index(indexPath)

        index.loadForUpdate {
            add(rel("alice.txt"), oid, stat)
            writeUpdates()
        }

        indexPath.delete()
        assertFalse(indexPath.exists())
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

            assertEquals(Entry.Key(it, 0.toByte()), entry.key)
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

        index.loadForUpdate {

            fileNames.forEach {
                val path = workspacePath.resolve(it)
                path.parent.mkdirp()
                path.write(it)

                val stat = path.stat()
                val data = path.readBytes()
                val blob = Blob(data)
                add(rel(path), blob.oid, stat)
            }

            writeUpdates()
        }

        val list = index.load().toList().map { it.name }

        assertEquals(
            listOf(
                "src/entry.kt",
                "src/index.kt",
                "src/workspace.kt",
                "test/entry.kt",
                "test/index.kt",
                "test/lockfile.kt",
                "test/refs.kt"
            ),
            list
        )
    }

    @Test
    fun `add conflict set which clears stage-0 entries`() {
        val indexPath = workspacePath.resolve("index")
        val index = Index(indexPath)

        val items = listOf(
            DbEntry(rel("alice.txt"), stat, oid),
            DbEntry(rel("alice.txt"), stat, oid),
            DbEntry(rel("alice.txt"), stat, oid),
        )

        index.loadForUpdate {
            add(rel("alice.txt"), oid, stat)
            addConflictSet(rel("alice.txt"), items)
            writeUpdates()
        }

        val loaded = index.load()
        assertEquals(
            listOf(
                Entry.Key("alice.txt", 1),
                Entry.Key("alice.txt", 2),
                Entry.Key("alice.txt", 3),
            ),
            loaded.toList().map { it.key }
        )
        assertTrue(loaded.tracked(rel("alice.txt")))
        assertTrue(loaded.hasConflicts())
    }

    @Test
    fun `add sparse conflict set`() {
        val indexPath = workspacePath.resolve("index")
        val index = Index(indexPath)

        val items = listOf(
            ParsedTreeEntry(rel("alice.txt"), rel("alice.txt"), Mode.REGULAR, oid),
            null,
            DbEntry(rel("alice.txt"), stat, oid),
        )

        index.loadForUpdate {
            add(rel("alice.txt"), oid, stat)
            addConflictSet(rel("alice.txt"), items)
            writeUpdates()
        }

        val loaded = index.load()
        assertEquals(
            listOf(
                Entry.Key("alice.txt", 1),
                Entry.Key("alice.txt", 3),
            ),
            loaded.toList().map { it.key }
        )
        assertTrue(loaded.tracked(rel("alice.txt")))
        assertTrue(loaded.hasConflicts())
    }

    @Test
    fun `get specific stage entry`() {
        val indexPath = workspacePath.resolve("index")
        val index = Index(indexPath)

        val oid1 = ObjectId("1234512345123451234512345123451234512341")
        val oid2 = ObjectId("1234512345123451234512345123451234512342")
        val oid3 = ObjectId("1234512345123451234512345123451234512343")

        index.loadForUpdate {
            addConflictSet(
                rel("alice.txt"),
                listOf(
                    DbEntry(rel("alice.txt"), stat, oid1),
                    DbEntry(rel("alice.txt"), stat, oid2),
                    DbEntry(rel("alice.txt"), stat, oid3),
                )
            )
            writeUpdates()
        }

        val loaded = index.load()
        assertNull(loaded["alice.txt", 0])
        assertEquals(oid1, loaded["alice.txt", 1]?.oid)
        assertEquals(oid2, loaded["alice.txt", 2]?.oid)
        assertEquals(oid3, loaded["alice.txt", 3]?.oid)
    }

    @Test
    fun `entry executable mode`() {
        val entry = Entry(
            workspacePath.resolve("a").relativeTo(workspacePath),
            ObjectId("1234512345123451234512345123451234512345"),
            FileStat(executable = true)
        )
        assertEquals(Mode.EXECUTABLE, entry.mode)
    }

    @Test
    fun `entry regular mode`() {
        val entry = Entry(
            workspacePath.resolve("a").relativeTo(workspacePath),
            ObjectId("1234512345123451234512345123451234512345"),
            FileStat()
        )
        assertEquals(Mode.REGULAR, entry.mode)
    }

    @Test
    fun `entry stage`() {
        val entry = Entry(
            Entry.Key("a", stage = 1),
            ObjectId("1234512345123451234512345123451234512345"),
            FileStat()
        )
        assertEquals(1.toByte(), entry.stage)
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
        val entry = Entry(
            Entry.Key("a", stage = 3),
            ObjectId("1234512345123451234512345123451234512345"),
            FileStat(executable = true)
        )

        val bytes = entry.content

        val parsed = Entry.parse(bytes)

        assertEquals(entry.key, parsed.key)
        assertEquals(entry.oid, parsed.oid)
        assertEquals(3.toByte(), parsed.stage)
        assertEquals("a", parsed.name)
    }

    @Test
    fun `from db entry`() {
        val pathFull = workspacePath.resolve("a")
        val path = pathFull.relativeTo(workspacePath)
        val oid = ObjectId("1234512345123451234512345123451234512345")

        val dbEntry = DbEntry(path, Mode.EXECUTABLE, oid)
        val indexEntry = Entry.createFromDb(dbEntry, 3)

        assertEquals(oid, indexEntry.oid)
        assertEquals(3.toByte(), indexEntry.stage)
        assertEquals("a", indexEntry.name)
        assertEquals(true, indexEntry.stat.executable)
    }
}
