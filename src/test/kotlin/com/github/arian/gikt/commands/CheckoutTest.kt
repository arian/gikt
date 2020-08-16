package com.github.arian.gikt.commands

import com.github.arian.gikt.database.ObjectId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class CheckoutTest {

    private val cmd = CommandHelper()

    companion object {
        private const val TAB = "\t"
    }

    @BeforeEach
    fun before() {
        cmd.init()
    }

    private fun commitFile(name: String, contents: String): ObjectId {
        cmd.writeFile(name, contents)
        cmd.cmd("add", ".")
        cmd.commit("commit")
        return requireNotNull(cmd.repository.refs.readHead())
    }

    private fun listWorkspaceFiles() =
        cmd.repository.workspace.listFiles().map { it.toString() }

    @Test
    fun `checkout in unborn repo`() {
        val execution = cmd.cmd("checkout")
        assertEquals(128, execution.status)
    }

    @Test
    fun `checkout in invalid ref`() {
        commitFile("a.txt", "")
        val execution = cmd.cmd("checkout", "branch")
        assertEquals(128, execution.status)
        assertEquals("error: Not a valid object name: 'branch'\n", execution.stderr)
    }

    @Test
    fun `checkout ambiguous branch name`() {
        commitFile("a.txt", "")

        val second = commitFile("a.txt", "2")
        assertEquals(ObjectId("7008be3badf19af8f49f6775216d8efb64ea947a"), second)

        cmd.copy(
            ".git/objects/70/08be3badf19af8f49f6775216d8efb64ea947a",
            ".git/objects/70/08be3badf19af8f49f6775216d8efb64ea947b"
        )

        val execution = cmd.cmd("checkout", "7008be3")
        assertEquals(128, execution.status)
        assertEquals(
            """|error: short SHA1 7008be3 is ambiguous
               |hint: The candidates are:
               |hint:  7008be3 commit 2019-08-14 - commit
               |hint:  7008be3 commit 2019-08-14 - commit
               |error: Not a valid object name: '7008be3'
               |""".trimMargin(),
            execution.stderr
        )
    }

    @Test
    fun `checkout previous commit`() {
        commitFile("a.txt", "a")
        commitFile("b.txt", "a")

        assertEquals(listOf("a.txt", "b.txt"), listWorkspaceFiles())

        val execution = cmd.cmd("checkout", "HEAD^")
        assertEquals(0, execution.status)

        assertEquals(listOf("a.txt"), listWorkspaceFiles())
    }

    @Test
    fun `checkout commit with new files`() {
        cmd.writeFile("a.txt", "a")
        cmd.writeFile("a/b.txt", "b")
        cmd.writeFile("a/c.txt", "c")
        val head = commitFile("d.txt", "d")

        cmd.delete("d.txt")
        cmd.resetIndex()
        cmd.commit("delete d.txt")

        assertEquals(listOf("a.txt", "a/b.txt", "a/c.txt"), listWorkspaceFiles().sorted())

        val execution = cmd.cmd("checkout", head.short)

        assertEquals(0, execution.status)
        assertEquals(listOf("a.txt", "a/b.txt", "a/c.txt", "d.txt"), listWorkspaceFiles().sorted())
        assertEquals("d", cmd.readFile("d.txt"))
    }

    @Test
    fun `checkout commit replaces directory`() {
        cmd.writeFile("a", "a")
        cmd.cmd("add", "a")
        cmd.commit("first")

        cmd.delete("a")
        cmd.writeFile("a/b.txt", "b")
        cmd.resetIndex()
        cmd.cmd("add", "a")
        cmd.commit("replace a")

        assertEquals(listOf("a/b.txt"), listWorkspaceFiles())

        val execution = cmd.cmd("checkout", "HEAD^")

        assertEquals(0, execution.status)
        assertEquals(listOf("a"), listWorkspaceFiles())
        assertEquals("a", cmd.readFile("a"))
    }

    @Test
    fun `checkout commit replaces file with nested directories`() {
        cmd.writeFile("a/b/c/d", "d")
        cmd.cmd("add", "a")
        cmd.commit("first")

        cmd.delete("a")
        cmd.writeFile("a/b.txt", "b")
        cmd.resetIndex()
        cmd.cmd("add", "a")
        cmd.commit("replace a")

        assertEquals(listOf("a/b.txt"), listWorkspaceFiles())

        val execution = cmd.cmd("checkout", "HEAD^")

        assertEquals(0, execution.status)
        assertEquals(listOf("a/b/c/d"), listWorkspaceFiles())
        assertEquals("d", cmd.readFile("a/b/c/d"))
    }

    @Test
    fun `checkout commit replaces nested directories with file`() {
        cmd.writeFile("a/b.txt", "b")
        cmd.cmd("add", "a")
        cmd.commit("first")

        cmd.delete("a")
        cmd.writeFile("a/b/c/d", "d")
        cmd.resetIndex()
        cmd.cmd("add", "a")
        cmd.commit("replace a")

        assertEquals(listOf("a/b/c/d"), listWorkspaceFiles())

        val execution = cmd.cmd("checkout", "HEAD^")

        assertEquals(0, execution.status)
        assertEquals(listOf("a/b.txt"), listWorkspaceFiles())
        assertEquals("b", cmd.readFile("a/b.txt"))
    }

    @Test
    fun `checkout should update index`() {
        cmd.writeFile("a/b.txt", "b")
        cmd.cmd("add", "a")
        cmd.commit("first")

        cmd.delete("a")
        cmd.writeFile("a/b/c/d", "d")
        cmd.resetIndex()
        cmd.cmd("add", "a")
        cmd.commit("replace a")

        assertEquals(listOf("a/b/c/d"), listWorkspaceFiles())

        val checkout = cmd.cmd("checkout", "HEAD^")
        assertEquals(0, checkout.status)
        assertEquals(listOf("a/b.txt"), listWorkspaceFiles())

        val status = cmd.cmd("status", "--porcelain")
        assertEquals(0, status.status)
        assertEquals("", status.stdout)
    }

    @Test
    fun `checkout commit conflicting untracked file`() {
        cmd.writeFile("a.txt", "a")
        val head = commitFile("b.txt", "b")

        cmd.delete("b.txt")
        cmd.resetIndex()
        cmd.commit("delete b.txt")

        cmd.writeFile("b.txt", "bb")

        val execution = cmd.cmd("checkout", head.short)

        assertEquals(1, execution.status)
        assertEquals("""
            error: The following untracked working tree files would be overwritten by checkout:
            ${TAB}b.txt
            Please move or remove them before you switch branches.
            Aborting
        """.trimIndent(), execution.stderr.trim())
    }

    @Test
    fun `checkout commit conflicting unstaged stale file`() {
        val head = commitFile("a.txt", "a")
        commitFile("a.txt", "ab")
        cmd.writeFile("a.txt", "abc")

        val execution = cmd.cmd("checkout", head.short)

        assertEquals(1, execution.status)
        assertEquals("""
            error: Your local changes to the following files would be overwritten by checkout:
            ${TAB}a.txt
            Please commit your changes or stash them before you switch branches.
            Aborting
        """.trimIndent(), execution.stderr.trim())
    }

    @Test
    fun `checkout commit conflicting staged stale file`() {
        val head = commitFile("a.txt", "a")
        commitFile("a.txt", "ab")
        cmd.writeFile("a.txt", "abc")
        cmd.cmd("add", "a.txt")

        val execution = cmd.cmd("checkout", head.short)

        assertEquals(1, execution.status)
        assertEquals("""
            error: Your local changes to the following files would be overwritten by checkout:
            ${TAB}a.txt
            Please commit your changes or stash them before you switch branches.
            Aborting
        """.trimIndent(), execution.stderr.trim())
    }

    @Test
    fun `checkout commit conflicting untracked file in directory`() {
        val head = commitFile("a", "a")
        commitFile("a", "ab")
        cmd.delete("a")
        cmd.writeFile("a/b", "b")

        val execution = cmd.cmd("checkout", head.short)

        assertEquals(1, execution.status)
        assertEquals("""
            error: Updating the following directories would lose untracked files in them:
            ${TAB}a

            Aborting
        """.trimIndent(), execution.stderr.trim())
    }

    @Test
    fun `checkout commit conflicting multiple files`() {
        cmd.writeFile("a.txt", "1")
        cmd.writeFile("b.txt", "1")
        val head = commitFile("c.txt", "1")

        cmd.writeFile("a.txt", "2")
        cmd.writeFile("b.txt", "2")
        commitFile("c.txt", "2")

        cmd.writeFile("a.txt", "3")
        cmd.writeFile("b.txt", "3")
        cmd.writeFile("c.txt", "3")

        val execution = cmd.cmd("checkout", head.short)

        assertEquals(1, execution.status)
        assertEquals("""
            error: Your local changes to the following files would be overwritten by checkout:
            ${TAB}a.txt
            ${TAB}b.txt
            ${TAB}c.txt
            Please commit your changes or stash them before you switch branches.
            Aborting
        """.trimIndent(), execution.stderr.trim())
    }

    @Test
    fun `checkout from HEAD to branch with name prints the new branch name`() {
        commitFile("a.txt", "")
        cmd.cmd("branch", "topic")
        commitFile("b.txt", "")
        val execution = cmd.cmd("checkout", "topic")

        assertEquals(0, execution.status)
        assertEquals("""
            |Previous HEAD position was 8a45122 commit
            |Switched to branch 'topic'
        """.trimMargin(), execution.stderr.trim())
    }

    @Test
    fun `checkout branc to another branch with name prints only the new branch name`() {
        commitFile("a.txt", "")
        cmd.cmd("branch", "topic")
        commitFile("b.txt", "")
        cmd.cmd("branch", "bar")
        cmd.cmd("checkout", "bar")
        val execution = cmd.cmd("checkout", "topic")

        assertEquals(0, execution.status)
        assertEquals("""
            |Switched to branch 'topic'
        """.trimMargin(), execution.stderr.trim())
    }

    @Test
    fun `checkout commit prints a detached HEAD message`() {
        val oid = commitFile("a.txt", "")
        commitFile("b.txt", "")
        cmd.cmd("branch", "topic")
        cmd.cmd("checkout", "topic")
        val execution = cmd.cmd("checkout", oid.short)

        assertEquals(0, execution.status)
        assertEquals("""
            |Note: checking out '717094a'
            |
            |You are in 'detached HEAD' state. You can look around, make experimental
            |changes and commit them, and you can discard any commits you make in this
            |state without impacting any branches by performing another checkout.
            |
            |If you want to create a new branch to retain commits you create, you may
            |do so (now or later) by using the branch command. Example:
            |
            |  gikt branch <new-branch-name>
            |
            |HEAD is now at 717094a commit
        """.trimMargin(), execution.stderr.trim())
    }

    @Test
    fun `checkout existing branch`() {
        commitFile("a.txt", "")
        cmd.cmd("branch", "topic")
        cmd.cmd("checkout", "topic")

        val execution = cmd.cmd("checkout", "topic")

        assertEquals(0, execution.status)
        assertEquals("""
            |Already on 'topic'
        """.trimMargin(), execution.stderr.trim())
    }
}
