package com.github.arian.gikt.commands

import com.github.arian.gikt.Repository
import com.github.arian.gikt.makeExecutable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AddTest {

    private val cmd = CommandHelper()

    private fun assertIndex(repo: Repository, expected: List<Pair<String, Boolean>>) {
        val index = repo.index.load()
        assertEquals(expected, index.toList().map { Pair(it.key, it.stat.executable) })
    }

    @BeforeEach
    fun before() {
        cmd.init()
    }

    @Test
    fun `adds a regular file to the index`() {
        cmd.writeFile("hello.txt", "hello")
        cmd.cmd("add", "hello.txt")
        assertIndex(cmd.repository, listOf("hello.txt" to false))
    }

    @Test
    fun `adds an executable file to the index`() {
        val file = cmd.writeFile("hello.txt", "hello")
        file.makeExecutable()
        cmd.cmd("add", "hello.txt")
        assertIndex(cmd.repository, listOf("hello.txt" to true))
    }

    @Test
    fun `adds multiple files to the index`() {
        cmd.writeFile("hello.txt", "hello")
        cmd.writeFile("world.txt", "world")

        cmd.cmd("add", "world.txt")

        assertIndex(cmd.repository, listOf("world.txt" to false))

        cmd.cmd("add", "hello.txt")

        assertIndex(
            cmd.repository, listOf(
                "hello.txt" to false,
                "world.txt" to false
            )
        )
    }

    @Test
    fun `adds a directory to the index`() {
        cmd.writeFile("a-dir/nested.txt", "content")

        cmd.cmd("add", "a-dir")

        assertIndex(cmd.repository, listOf("a-dir/nested.txt" to false))
    }

    @Test
    fun `adds the repository root to the index`() {
        cmd.writeFile("a-dir/nested.txt", "content")

        cmd.cmd("add", ".")

        assertIndex(cmd.repository, listOf("a-dir/nested.txt" to false))
    }

    @Test
    fun `is silent on success`() {
        cmd.writeFile("hello.txt", "hello")

        val execution = cmd.cmd("add", "hello.txt")

        assertEquals(0, execution.status)
        assertEquals("", execution.stdout)
        assertEquals("", execution.stderr)
    }

    @Test
    fun `fails for non-existent files`() {
        val execution = cmd.cmd("add", "no-such-file")

        assertEquals(
            """
                |fatal: pathspec 'no-such-file' did not match any files
                |
            """.trimMargin(),
            execution.stderr
        )

        assertEquals(128, execution.status)
        assertIndex(cmd.repository, emptyList())
    }

    @Test
    fun `fails for unreadable files`() {
        cmd.writeFile("secret.txt", "")
        cmd.makeUnreadable("secret.txt")

        val execution = cmd.cmd("add", "secret.txt")

        assertEquals(128, execution.status)

        assertEquals(
            """
                |error: open('secret.txt'): Permission denied
                |fatal: adding files failed
                |
            """.trimMargin(),
            execution.stderr
        )

        assertEquals(128, execution.status)
        assertIndex(cmd.repository, emptyList())
    }

    @Test
    fun `fails if the index is locked`() {
        cmd.writeFile("file.txt", "")
        cmd.writeFile(".git/index.lock", "")

        val execution = cmd.cmd("add", "file.txt")

        assertEquals(128, execution.status)
        assertIndex(cmd.repository, emptyList())
    }
}
