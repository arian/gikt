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
        val execution = cmd.cmd("status", "--porcelain")
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

        @Test
        fun `reports deleted files`() {
            cmd.delete("a/2.txt")
            assertStatus(
                """
                    | D a/2.txt
                """.trimMargin()
            )
        }

        @Test
        fun `reports files in deleted directories`() {
            cmd.delete("a")
            assertStatus(
                """
                    | D a/2.txt
                    | D a/b/3.txt
                """.trimMargin()
            )
        }
    }

    @Nested
    inner class HeadIndexChanges {

        @BeforeEach
        fun before() {
            cmd.writeFile("1.txt", "one")
            cmd.writeFile("a/2.txt", "two")
            cmd.writeFile("a/b/3.txt", "three")
            cmd.cmd("add", ".")
            cmd.commit("first message")
        }

        @Test
        fun `reports a file added to a tracked directory`() {
            cmd.writeFile("a/4.txt", "four")
            cmd.cmd("add", ".")

            assertStatus(
                """
                    |A  a/4.txt
                """.trimMargin()
            )
        }

        @Test
        fun `reports a file added to an untracked directory`() {
            cmd.writeFile("d/e/5.txt", "five")
            cmd.cmd("add", ".")

            assertStatus(
                """
                    |A  d/e/5.txt
                """.trimMargin()
            )
        }

        @Test
        fun `reports modified modes`() {
            cmd.makeExecutable("1.txt")
            cmd.cmd("add", ".")

            assertStatus(
                """
                    |M  1.txt
                """.trimMargin()
            )
        }

        @Test
        fun `reports modified contents`() {
            cmd.writeFile("a/b/3.txt", "changed")
            cmd.cmd("add", ".")

            assertStatus(
                """
                    |M  a/b/3.txt
                """.trimMargin()
            )
        }

        @Test
        fun `reports deleted files`() {
            cmd.delete("1.txt")
            cmd.resetIndex()

            assertStatus(
                """
                    |D  1.txt
                """.trimMargin()
            )
        }

        @Test
        fun `reports deleted files inside directories`() {
            cmd.delete("a")
            cmd.resetIndex()

            assertStatus(
                """
                    |D  a/2.txt
                    |D  a/b/3.txt
                """.trimMargin()
            )
        }
    }

    @Nested
    inner class LongStatus {

        private fun assertStatusLong(output: String) {
            val execution = cmd.cmd("status")
            assertEquals(output, execution.stdout.trimEnd())
            assertEquals(0, execution.status)
        }

        @BeforeEach
        fun before() {
            cmd.writeFile("1.txt", "one")
            cmd.cmd("add", ".")
            cmd.commit("commit message")
        }

        @Test
        fun `prints nothing when no files are changed`() {
            assertStatusLong("nothing to commit, working tree clean")
        }

        @Test
        fun changes() {
            cmd.writeFile("1.txt", "changed")
            assertStatusLong(
                """ |Changes not staged for commit:
                    |
                    |${"\t"}modified:   1.txt
                    |
                    |no changes added to commit
                """.trimMargin()
            )
        }

        @Test
        fun `staged changes`() {
            cmd.writeFile("1.txt", "changed")
            cmd.cmd("add", "1.txt")
            assertStatusLong(
                """ |Changes to be committed:
                    |
                    |${"\t"}modified:   1.txt
                """.trimMargin()
            )
        }
    }
}
