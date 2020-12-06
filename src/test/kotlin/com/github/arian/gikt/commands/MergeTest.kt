package com.github.arian.gikt.commands

import com.github.arian.gikt.database.ObjectId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
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

    /**
     * A     B     C
     * o<----o<----o<-----.
     *        \            \
     *         o<-----o<----o<==
     *         D      E     M
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

        val head = requireNotNull(cmd.repository.refs.readHead())
        val mergeCommit = cmd.repository.loadObject(head) as DbCommit

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
}
