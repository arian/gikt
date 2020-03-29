package com.github.arian.gikt.database

import com.github.arian.gikt.FileStat
import com.github.arian.gikt.Workspace
import com.github.arian.gikt.deleteRecursively
import com.github.arian.gikt.makeExecutable
import com.github.arian.gikt.mkdirp
import com.github.arian.gikt.relativeTo
import com.github.arian.gikt.touch
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class TreeTest {

    private val stat = FileStat(executable = false)

    @Test
    fun tree() {
        val oid1 = ObjectId(ByteArray(20) { it.toByte() })
        val oid2 = ObjectId(ByteArray(20) { (it + 10).toByte() })

        val path = Path.of(".")

        val tree = Tree(path).apply {
            addEntry(
                parents = null, entry = Entry(
                    Path.of("file1"),
                    oid = oid1,
                    stat = stat
                )
            )
            addEntry(
                parents = null, entry = Entry(
                    Path.of("before"),
                    oid = oid2,
                    stat = stat
                )
            )
        }

        val expected = ByteArray(0) +
            "100644 before".toByteArray() + 0.toByte() + oid2.bytes +
            "100644 file1".toByteArray() + 0.toByte() + oid1.bytes

        assertEquals(expected.toString(Charsets.UTF_8), tree.data.toString(Charsets.UTF_8))
    }

    @Test
    fun treeNested() {
        val oid = ObjectId(ByteArray(20) { it.toByte() })

        val path = Path.of("workspace")

        val a = path.resolve("a.txt")
        val b = path.resolve("b")
        val c = path.resolve("b/c.txt")

        val entryC = Entry(
            name = c,
            stat = stat,
            oid = oid
        )
        val treeB = Tree(
            b, mutableMapOf(
                c.toString() to entryC
            )
        )

        val entryA = Entry(
            name = a,
            stat = stat,
            oid = oid
        )

        val tree = Tree(
            path, mutableMapOf(
                a.toString() to entryA,
                b.toString() to treeB
            )
        )

        val expected = ByteArray(0) +
            "100644 a.txt".toByteArray() + 0.toByte() + entryA.oid.bytes +
            "40000 b".toByteArray() + 0.toByte() + treeB.oid.bytes

        assertEquals(expected.toString(Charsets.UTF_8), tree.data.toString(Charsets.UTF_8))
    }

    @Test
    fun `tree empty`() {
        val path = Path.of(".")
        val tree = Tree(path)
        val expected = ByteArray(0)
        assertEquals(expected.toString(Charsets.UTF_8), tree.data.toString(Charsets.UTF_8))
    }

    @Test
    fun traverse() {
        val oid = ObjectId(ByteArray(20) { it.toByte() })

        val path = Path.of("workspace")

        val a = path.resolve("a.txt")
        val b = path.resolve("b")
        val c = path.resolve("b/c.txt")

        val entryC = Entry(
            name = c,
            stat = stat,
            oid = oid
        )
        val treeB = Tree(
            b, mutableMapOf(
                c.toString() to entryC
            )
        )

        val entryA = Entry(
            name = a,
            stat = stat,
            oid = oid
        )

        val tree = Tree(
            path, mutableMapOf(
                a.toString() to entryA,
                b.toString() to treeB
            )
        )

        val names = mutableListOf<Path>()
        tree.traverse { names.add(it.name) }

        assertEquals(listOf(b, path), names)
    }

    @Nested
    inner class WithRealFs {
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
        fun executableFile() {
            val oid = ObjectId(ByteArray(20) { it.toByte() })

            val file = Files.createFile(path.resolve("file.txt")).makeExecutable()

            val tree = Tree(path.relativeTo(path)).apply {
                addEntry(Tree.Parents(),
                    Entry(
                        file.relativeTo(path),
                        FileStat(executable = true),
                        oid
                    )
                )
            }

            val expected = ByteArray(0) +
                "100755 file.txt".toByteArray() + 0.toByte() + oid.bytes

            assertEquals(expected.toString(Charsets.UTF_8), tree.data.toString(Charsets.UTF_8))
        }

        @Test
        fun buildTree() {
            path.resolve("a/b/c").mkdirp()
            path.resolve("a/b/c/d.txt").touch()
            path.resolve("a/b/c.txt").touch()
            path.resolve("a/b.txt").touch()
            path.resolve("a/x.txt").touch()

            val workspace = Workspace(path)

            val entries = workspace.listFiles().map {
                Entry(
                    it,
                    workspace.statFile(it),
                    ObjectId(ByteArray(20))
                )
            }

            val tree = Tree.build(path, entries)

            assertEquals(listOf("a"), tree.list())
            assertEquals(listOf("b", "b.txt", "x.txt"), tree.getTree("a")?.list())
            assertEquals(listOf("c", "c.txt"), tree.getTree("a")?.getTree("b")?.list())

            val names = mutableListOf<String>()
            tree.traverse { names.add(it.name.toString()) }

            assertEquals(
                listOf("a/b/c", "a/b", "a", ""),
                names
            )
        }
    }
}
