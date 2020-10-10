package com.github.arian.gikt.commands

import com.github.arian.gikt.database.Blob
import com.github.arian.gikt.database.ObjectId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BranchTest {

    private val cmd = CommandHelper()
    private var count = 0

    @BeforeEach
    fun before() {
        cmd.init()
    }

    private fun commitFile(msg: String = "commit"): ObjectId {
        return cmd.commitFile("file-${count++}", msg = msg)
    }

    @Test
    fun `creates a new branch`() {
        val head = commitFile()
        val execution = cmd.cmd("branch", "topic")
        assertEquals(0, execution.status)
        val topicRef = cmd.readFile(".git/refs/heads/topic").trim()
        assertEquals(head.hex, topicRef)
    }

    @Test
    fun `creates a new branch from HEAD commit`() {
        commitFile()
        val head = commitFile()

        val execution = cmd.cmd("branch", "topic", "HEAD")
        assertEquals(0, execution.status)

        val topicRef = cmd.readFile(".git/refs/heads/topic").trim()

        assertEquals(head.hex, topicRef)
    }

    @Test
    fun `creates a new branch from parent commit`() {
        val first = commitFile()
        commitFile()

        val execution = cmd.cmd("branch", "topic", "HEAD^")
        assertEquals(0, execution.status)

        val topicRef = cmd.readFile(".git/refs/heads/topic").trim()

        assertEquals(first.hex, topicRef)
    }

    @Test
    fun `creates a new branch from ancestor commit`() {
        commitFile()
        val second = commitFile()
        commitFile()
        commitFile()
        commitFile()

        val execution = cmd.cmd("branch", "topic", "HEAD~3")
        assertEquals(0, execution.status)

        val topicRef = cmd.readFile(".git/refs/heads/topic").trim()

        assertEquals(second.hex, topicRef)
    }

    @Test
    fun `creates a new branch from another branch`() {
        commitFile()
        val second = commitFile()
        cmd.cmd("branch", "topic")
        val topicRef = cmd.readFile(".git/refs/heads/topic").trim()
        assertEquals(second.hex, topicRef)

        commitFile()

        val execution = cmd.cmd("branch", "feature", "topic")
        assertEquals(0, execution.status)

        val featureRef = cmd.readFile(".git/refs/heads/feature").trim()

        assertEquals(topicRef, featureRef)
    }

    @Test
    fun `creates branch when HEAD does not exist yet fails`() {
        val execution = cmd.cmd("branch", "topic")
        assertEquals(128, execution.status)
        assertEquals(
            """
            fatal: Not a valid object name: 'main'

            """.trimIndent(),
            execution.stderr
        )
    }

    @Test
    fun `creates a new branch from too short (2) sha1`() {
        commitFile()
        val second = commitFile()
        assertEquals(ObjectId("203bb2a8f80adb04857f6b9882355a35797e0c16"), second)

        val execution = cmd.cmd("branch", "topic", "20")
        assertEquals(128, execution.status)
        assertEquals(
            """
            fatal: Not a valid object name: '20'

            """.trimIndent(),
            execution.stderr
        )
    }

    @Test
    fun `creates a new branch from too short (3) sha1`() {
        commitFile()
        val second = commitFile()
        assertEquals(ObjectId("203bb2a8f80adb04857f6b9882355a35797e0c16"), second)

        val execution = cmd.cmd("branch", "topic", "203")
        assertEquals(128, execution.status)
        assertEquals(
            """
            fatal: Not a valid object name: '203'

            """.trimIndent(),
            execution.stderr
        )
    }

    @Test
    fun `creates a new branch from ambiguous sha1`() {
        commitFile()
        val second = commitFile()
        assertEquals(ObjectId("203bb2a8f80adb04857f6b9882355a35797e0c16"), second)

        cmd.copy(
            ".git/objects/20/3bb2a8f80adb04857f6b9882355a35797e0c16",
            ".git/objects/20/3bb2b8f80adb04857f6b9882355a35797e0c17"
        )

        val execution = cmd.cmd("branch", "topic", "203bb2")
        assertEquals(128, execution.status)
        assertEquals(
            """
            error: short SHA1 203bb2 is ambiguous
            hint: The candidates are:
            hint:  203bb2a commit 2019-08-14 - commit
            hint:  203bb2b commit 2019-08-14 - commit
            fatal: Not a valid object name: '203bb2'

            """.trimIndent(),
            execution.stderr
        )
    }

    @Test
    fun `creates a new branch from sha1 object that's not a commit`() {
        val blob = Blob(data = "".toByteArray(Charsets.UTF_8))
        cmd.repository.database.store(blob)

        val execution = cmd.cmd("branch", "topic", blob.oid.short)
        assertEquals(128, execution.status)
        assertEquals(
            """
            error: object e69de29bb2d1d6434b8b29ae775ad8c2e48c5391 is a blob, not a commit
            fatal: Not a valid object name: 'e69de29'

            """.trimIndent(),
            execution.stderr
        )
    }

    @Test
    fun `branch without arguments should list the created branches`() {
        commitFile()
        cmd.cmd("branch", "topic")
        cmd.cmd("branch", "foo/qux")
        cmd.cmd("branch", "bar")
        cmd.cmd("checkout", "bar")

        val execution = cmd.cmd("branch")

        assertEquals(0, execution.status)
        assertEquals(
            """
            |* bar
            |  foo/qux
            |  main
            |  topic
            |""".trimMargin(),
            execution.stdout
        )
    }

    @Test
    fun `branch without arguments and verbose option should show branch information`() {
        val oid1 = commitFile("first")
        cmd.cmd("branch", "topic")
        val oid2 = commitFile()
        cmd.cmd("branch", "foo/qux")
        cmd.cmd("branch", "bar")
        cmd.cmd("checkout", "bar")

        val execution = cmd.cmd("branch", "--verbose")

        assertEquals(0, execution.status)
        assertEquals(
            """
            |* bar     ${oid2.short} commit
            |  foo/qux ${oid2.short} commit
            |  main    ${oid2.short} commit
            |  topic   ${oid1.short} first
            |""".trimMargin(),
            execution.stdout
        )
    }

    @Test
    fun `deleted branches should not be listed anymore`() {
        val oid = commitFile()
        cmd.cmd("branch", "topic")
        cmd.cmd("branch", "foo/qux")
        cmd.cmd("branch", "bar")
        val execution = cmd.cmd("branch", "--delete", "bar")

        assertEquals(0, execution.status)
        assertEquals(
            """
            |Deleted branch bar (was ${oid.short})
            |""".trimMargin(),
            execution.stdout
        )

        val executionListBranches = cmd.cmd("branch")
        assertEquals(0, executionListBranches.status)
        assertEquals(
            """
            |  foo/qux
            |* main
            |  topic
            |""".trimMargin(),
            executionListBranches.stdout
        )
    }

    @Test
    fun `deleting branch should cleanup empty parent directories`() {
        commitFile()
        cmd.cmd("branch", "foo/qux/bar")
        cmd.cmd("branch", "--delete", "foo/qux/bar")

        val executionListBranches = cmd.cmd("branch")

        assertEquals(0, executionListBranches.status)
        assertEquals(
            """
            |* main
            |""".trimMargin(),
            executionListBranches.stdout
        )
        assertFalse(cmd.exists(".git/refs/heads/foo"))
    }

    @Test
    fun `deleting unknown branch should show an error`() {
        val execution = cmd.cmd("branch", "--delete", "foo")

        assertEquals(1, execution.status)
        assertEquals(
            """
            |error: branch 'foo' not found.
            |""".trimMargin(),
            execution.stderr
        )
    }
}
