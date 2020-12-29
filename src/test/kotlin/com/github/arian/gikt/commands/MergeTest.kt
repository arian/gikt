package com.github.arian.gikt.commands

import com.github.arian.gikt.Mode
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.utf8
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import com.github.arian.gikt.database.Commit as DbCommit

internal class MergeTest {

    private val cmd = CommandHelper()

    @BeforeEach
    fun before() {
        cmd.init()
    }

    private fun commitFiles(vararg files: Pair<String, String>): ObjectId {
        files.forEach { (name, contents) ->
            cmd.writeFile(name, contents)
            cmd.cmd("add", name)
        }
        cmd.commit("commit")
        return requireNotNull(cmd.repository.refs.readHead())
    }

    private fun readHeadCommit(): DbCommit {
        val head = requireNotNull(cmd.repository.refs.readHead())
        return cmd.repository.loadObject(head) as DbCommit
    }

    /**
     * ```
     * A     B     C
     * o<----o<----o<-----.
     *        \            \
     *         o<-----o<----o<==
     *         D      E     M
     * ```
     */
    @Test
    fun `merge two branches`() {
        // A
        commitFiles(
            "f.txt" to "1",
            "g.txt" to "1",
            "h.txt" to "1"
        )
        val commitB = commitFiles(
            "f.txt" to "2",
            "g.txt" to "3",
            "h.txt" to "1"
        )
        val commitC = commitFiles(
            "f.txt" to "4",
            "g.txt" to "3",
            "h.txt" to "1"
        )
        cmd.cmd("checkout", commitB.short)
        // D
        commitFiles(
            "f.txt" to "2",
            "g.txt" to "5",
            "h.txt" to "1"
        )
        val commitE = commitFiles(
            "f.txt" to "2",
            "g.txt" to "5",
            "h.txt" to "6"
        )

        val execution = cmd.cmd("merge", commitC.short, stdin = "merge")
        assertEquals(0, execution.status)

        val mergeCommit = readHeadCommit()
        assertEquals("merge", mergeCommit.message.utf8())
        assertEquals(listOf(commitE, commitC), mergeCommit.parents)

        assertEquals("4", cmd.readFile("f.txt"))
        assertEquals("5", cmd.readFile("g.txt"))
        assertEquals("6", cmd.readFile("h.txt"))
    }

    @Test
    fun `revision doesn't exist`() {
        val execution = cmd.cmd("merge", "foo", stdin = "merge")
        assertEquals(1, execution.status)
        assertEquals("merge: foo - not something we can merge\n", execution.stderr)
    }

    /**
     * ```
     * A     B     C
     * o<----o<----o
     * ```
     */
    @Test
    fun `commit was already merged`() {
        val commitA = commitFiles("f.txt" to "1")
        commitFiles("f.txt" to "2")
        val commitC = commitFiles("f.txt" to "3")

        val execution = cmd.cmd("merge", commitA.short, stdin = "merge")
        assertEquals(0, execution.status)
        assertEquals("Already up to date.\n", execution.stdout)

        val head = requireNotNull(cmd.repository.refs.readHead())
        assertEquals(commitC, head)
    }

    /**
     * ```
     * A     B
     * o<----o [main] <== [HEAD]
     *        \
     *         o<---o [topic]
     *         C    D
     * ```
     */
    @Test
    fun `BCA is equal to the HEAD does a fast-forward`() {
        commitFiles("f.txt" to "A")
        val commitB = commitFiles("f.txt" to "B")
        cmd.cmd("branch", "topic")
        cmd.cmd("checkout", "topic")
        commitFiles("f.txt" to "C")
        val commitD = commitFiles("f.txt" to "D")
        cmd.cmd("checkout", "main")

        assertEquals(commitB, requireNotNull(cmd.repository.refs.readHead()))

        val execution = cmd.cmd("merge", "topic", stdin = "merge")
        assertEquals(0, execution.status)

        assertEquals(commitD, requireNotNull(cmd.repository.refs.readHead()))
        assertEquals(commitD, requireNotNull(cmd.repository.refs.readRef("main")))
        assertEquals(commitD, requireNotNull(cmd.repository.refs.readRef("topic")))

        assertEquals("D", cmd.readFile("f.txt"))
    }

    @Nested
    inner class AddEditDeleteConflicts {

        @Test
        fun `changing the same file with different content`() {
            commitFiles("f.txt" to "one")
            cmd.cmd("branch", "topic")
            cmd.cmd("checkout", "topic")
            commitFiles("f.txt" to "two")
            cmd.cmd("checkout", "main")
            val mainCommit = commitFiles("f.txt" to "three\n")
            val execution = cmd.cmd("merge", "topic", stdin = "merge")

            assertEquals(1, execution.status)

            assertEquals(
                """
                |<<<<<<< HEAD
                |three
                |=======
                |two
                |>>>>>>> topic
                |
                """.trimMargin(),
                cmd.readFile("f.txt")
            )
            assertEquals(mainCommit, readHeadCommit().oid)
        }

        @Test
        fun `changing the same file with identical content`() {
            commitFiles("f.txt" to "one")
            cmd.cmd("branch", "topic")
            cmd.cmd("checkout", "topic")
            val topicCommit = commitFiles("f.txt" to "two")
            cmd.cmd("checkout", "main")
            val mainCommit = commitFiles("f.txt" to "two")
            val execution = cmd.cmd("merge", "topic", stdin = "merge")
            assertEquals(0, execution.status)
            assertEquals("two", cmd.readFile("f.txt"))
            assertEquals(listOf(mainCommit, topicCommit), readHeadCommit().parents)
        }

        @Test
        fun `added the same file with different content`() {
            commitFiles("a.txt" to "one")
            cmd.cmd("branch", "topic")
            cmd.cmd("checkout", "topic")
            commitFiles("f.txt" to "two\n")
            cmd.cmd("checkout", "main")
            commitFiles("f.txt" to "three")
            val execution = cmd.cmd("merge", "topic", stdin = "merge")

            assertEquals(1, execution.status)

            assertEquals(
                """
                |<<<<<<< HEAD
                |three
                |=======
                |two
                |>>>>>>> topic
                |
                """.trimMargin(),
                cmd.readFile("f.txt")
            )
        }

        @Test
        fun `added the same file with identical content`() {
            commitFiles("a.txt" to "one")
            cmd.cmd("branch", "topic")
            cmd.cmd("checkout", "topic")
            commitFiles("f.txt" to "two")
            cmd.cmd("checkout", "main")
            commitFiles("f.txt" to "two")
            val execution = cmd.cmd("merge", "topic", stdin = "merge")

            assertEquals(0, execution.status)
            assertEquals("two", cmd.readFile("f.txt"))
        }

        @Test
        fun `changed and deleted the same file`() {
            commitFiles("f.txt" to "one")
            cmd.cmd("branch", "topic")
            cmd.cmd("checkout", "topic")
            cmd.delete("f.txt")
            cmd.resetIndex()
            cmd.commit("delete f.txt")
            cmd.cmd("checkout", "main")
            commitFiles("f.txt" to "three")
            val execution = cmd.cmd("merge", "topic", stdin = "merge file deletion")

            assertEquals(1, execution.status)
            assertEquals("three", cmd.readFile("f.txt"))
        }

        @Test
        fun `deleted and changed the same file`() {
            commitFiles("f.txt" to "one")
            cmd.cmd("branch", "topic")
            cmd.cmd("checkout", "topic")
            commitFiles("f.txt" to "two")
            cmd.cmd("checkout", "main")
            cmd.delete("f.txt")
            cmd.resetIndex()
            cmd.commit("delete f.txt")
            val execution = cmd.cmd("merge", "topic", stdin = "merge file change")

            assertEquals(1, execution.status)
            assertEquals("two", cmd.readFile("f.txt"))
        }

        @Test
        fun `both deleted the same file`() {
            commitFiles("f.txt" to "one")
            cmd.cmd("branch", "topic")
            cmd.cmd("checkout", "topic")
            cmd.delete("f.txt")
            cmd.resetIndex()
            val topicCommit = cmd.commit("delete f.txt in topic")
            cmd.cmd("checkout", "main")
            cmd.delete("f.txt")
            cmd.resetIndex()
            val mainCommit = cmd.commit("delete f.txt in main")

            val execution = cmd.cmd("merge", "topic", stdin = "merge topic")

            assertEquals(0, execution.status)
            assertFalse(cmd.exists("f.txt"))
            assertEquals(listOf(mainCommit, topicCommit), readHeadCommit().parents)
        }

        @Test
        fun `changed file mode and deleted file`() {
            commitFiles("f.txt" to "one")
            cmd.cmd("branch", "topic")
            cmd.cmd("checkout", "topic")
            cmd.delete("f.txt")
            cmd.resetIndex()
            cmd.commit("delete f.txt")
            cmd.cmd("checkout", "main")
            cmd.makeExecutable("f.txt")
            cmd.cmd("add", ".")
            cmd.commit("change file mode")
            val execution = cmd.cmd("merge", "topic", stdin = "merge file deletion")

            assertEquals(1, execution.status)
            assertEquals("one", cmd.readFile("f.txt"))
        }

        @Test
        fun `changed file mode and modified file content`() {
            commitFiles("f.txt" to "one")
            cmd.cmd("branch", "topic")
            cmd.cmd("checkout", "topic")
            commitFiles("f.txt" to "two")
            cmd.cmd("checkout", "main")
            cmd.makeExecutable("f.txt")
            cmd.cmd("add", ".")
            cmd.commit("change file mode")
            val execution = cmd.cmd("merge", "topic", stdin = "merge file change")

            assertEquals(0, execution.status)
            assertEquals("two", cmd.readFile("f.txt"))
            assertEquals(Mode.EXECUTABLE, cmd.readFileMode("f.txt"))
        }
    }
}
