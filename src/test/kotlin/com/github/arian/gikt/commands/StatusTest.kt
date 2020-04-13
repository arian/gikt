package com.github.arian.gikt.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class StatusTest {

    private val cmd = CommandHelper()

    @BeforeEach
    fun before() {
        cmd.init()
    }

    private fun assertStatus(output: String) {
        val execution = cmd.cmd("status")
        assertEquals(output, execution.stdout.trimEnd())
        assertEquals(0, execution.status)
    }

    @Test
    fun `list untracked files in name order`() {
        cmd.writeFile("file.txt", "")
        cmd.writeFile("another.txt", "")

        assertStatus(
            """
                ?? another.txt
                ?? file.txt
            """.trimIndent()
        )
    }

    @Test
    fun `lists files as untracked if they are not in the index`() {
        cmd.writeFile("committed.txt", "")
        cmd.cmd("add", ".")
        cmd.commit("commit message")

        cmd.writeFile("file.txt", "")

        assertStatus(
            """
                ?? file.txt
            """.trimIndent()
        )
    }

    @Test
    fun `lists untracked directories, not their contents`() {
        cmd.writeFile("file.txt", "")
        cmd.writeFile("dir/another.txt", "")

        assertStatus(
            """
                ?? dir/
                ?? file.txt
            """.trimIndent()
        )
    }

    @Test
    fun `lists untracked files inside tracked directories`() {
        cmd.writeFile("a/b/inner.txt", "")
        cmd.cmd("add", ".")
        cmd.commit("commit message")

        cmd.writeFile("a/outer.txt", "")
        cmd.writeFile("a/b/c/file.txt", "")

        assertStatus(
            """
                ?? a/b/c/
                ?? a/outer.txt
            """.trimIndent()
        )
    }

    @Test
    fun `does not list empty untracked directories`() {
        cmd.mkdir("outer")
        assertStatus("")
    }

    @Test
    fun `lists untracked directories that indirectly contain files`() {
        cmd.writeFile("outer/inner/file.txt", "")
        assertStatus(
            """
                ?? outer/
            """.trimIndent()
        )
    }

    @Nested
    inner class IndexAndWorkspaceChanges {

        @BeforeEach
        fun before() {
            cmd.writeFile("1.txt", "one")
            cmd.writeFile("a/2.txt", "two")
            cmd.writeFile("a/b/3.txt", "three")
            cmd.cmd("add", ".")
            cmd.commit("commit message")
        }

        @Test
        fun `prints nothing when no files are changed`() {
            assertStatus("")
        }

        @Test
        fun `reports files with modified contents`() {
            cmd.writeFile("1.txt", "changed")
            cmd.writeFile("a/2.txt", "modified")
            assertStatus(
                """
                    | M 1.txt
                    | M a/2.txt
                """.trimMargin()
            )
        }

        @Test
        fun `reports files with changed modes`() {
            cmd.makeExecutable("a/2.txt")
            assertStatus(
                """
                    | M a/2.txt
                """.trimMargin()
            )
        }

        @Test
        fun `reports modified files with unchanged size`() {
            cmd.writeFile("a/b/3.txt", "hello")
            assertStatus(
                """
                    | M a/b/3.txt
                """.trimMargin()
            )
        }

        @Test
        fun `prints nothing if a file is touched`() {
            cmd.touch("1.txt")
            assertStatus("")
            assertStatus("")
        }
    }
}
