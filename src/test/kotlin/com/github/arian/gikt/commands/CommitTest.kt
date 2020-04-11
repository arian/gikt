package com.github.arian.gikt.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CommitTest {

    private val cmd = CommandHelper()

    private val env = mapOf(
        "GIT_AUTHOR_NAME" to "Arian",
        "GIT_AUTHOR_EMAIL" to "arian@example.com"
    )

    @BeforeEach
    fun before() {
        cmd.init()
    }

    @Test
    fun `initial commit`() {
        cmd.writeFile("hello.txt", "hello")
        cmd.cmd("add", "hello.txt")
        val execution = cmd.cmd(
            "commit",
            env = env,
            stdin = "Initial commit message"
        )

        val head = cmd.repository.refs.readHead()

        assertNotNull(head)
        assertEquals(0, execution.status)
        assertEquals("[(root-commit) ${head?.hex}] Initial commit message\n", execution.stdout)
    }

    @Test
    fun `second commit commit`() {
        cmd.writeFile("hello.txt", "hello")
        cmd.cmd("add", "hello.txt")
        cmd.cmd("commit", env = env, stdin = "Initial commit message")

        cmd.writeFile("world.txt", "world")
        cmd.cmd("add", "world.txt")
        val execution = cmd.cmd("commit", env = env, stdin = "second commit")

        val head = cmd.repository.refs.readHead()

        assertNotNull(head)
        assertEquals(0, execution.status)
        assertEquals("[${head?.hex}] second commit\n", execution.stdout)
    }

    @Test
    fun `should fail for empty commit message`() {
        cmd.writeFile("hello.txt", "hello")
        cmd.cmd("add", "hello.txt")

        val execution = cmd.cmd("commit", env = env, stdin = "")

        assertEquals(1, execution.status)
        assertEquals("gikt: empty commit message\n", execution.stderr)
    }
}
