package com.github.arian.gikt.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class DiffTest {
    private val cmd = CommandHelper()

    @BeforeEach
    fun before() {
        cmd.init()
        cmd.writeFile("1.txt", "one")
        cmd.cmd("add", ".")
        cmd.commit("commit message")
    }

    private fun assertDiffExecution(expected: String, execution: CommandHelper.CommandTestExecution) {
        val lines = expected.lineSequence().count()
        assertEquals(expected, execution.stdout.lineSequence().take(lines).joinToString(separator = "\n"))
        assertEquals(0, execution.status)
    }

    @Nested
    inner class WorkspaceChanges {

        private fun assertDiff(expected: String) {
            val execution = cmd.cmd("diff")
            assertDiffExecution(expected, execution)
        }

        @Test
        fun `show diff header of changed file contents`() {
            cmd.writeFile("1.txt", "changed")

            assertDiff(
                """ |diff --git a/1.txt b/1.txt
                    |index 43dd47e..21fb1ec 100644
                    |--- a/1.txt
                    |+++ b/1.txt
                """.trimMargin()
            )
        }

        @Test
        fun `show diff header of file only changed mode`() {
            cmd.makeExecutable("1.txt")

            assertDiff(
                """ |diff --git a/1.txt b/1.txt
                    |old mode 100644
                    |new mode 100755
                """.trimMargin()
            )
        }

        @Test
        fun `show diff header of file changed contents and mode`() {
            cmd.writeFile("1.txt", "changed")
            cmd.makeExecutable("1.txt")

            assertDiff(
                """ |diff --git a/1.txt b/1.txt
                    |old mode 100644
                    |new mode 100755
                    |index 43dd47e..21fb1ec
                    |--- a/1.txt
                    |+++ b/1.txt
                """.trimMargin()
            )
        }

        @Test
        fun `show diff header of deleted file`() {
            cmd.delete("1.txt")

            assertDiff(
                """ |diff --git a/1.txt /dev/null
                    |deleted file mode 100644
                    |index 43dd47e..0000000
                    |--- a/1.txt
                    |+++ /dev/null
                """.trimMargin()
            )
        }
    }

    @Nested
    inner class CachedChanges {

        private fun assertDiffCached(expected: String) {
            val execution = cmd.cmd("diff", "--cached")
            assertDiffExecution(expected, execution)
        }

        @Test
        fun `staged modified`() {
            cmd.writeFile("1.txt", "changed")
            cmd.cmd("add", ".")

            assertDiffCached(
                """ |diff --git a/1.txt b/1.txt
                    |index 43dd47e..21fb1ec 100644
                    |--- a/1.txt
                    |+++ b/1.txt
                """.trimMargin()
            )
        }

        @Test
        fun `staged added`() {
            cmd.writeFile("2.txt", "two")
            cmd.cmd("add", ".")

            assertDiffCached(
                """ |diff --git a/2.txt b/2.txt
                    |new file mode 100644
                    |index 0000000..64c5e58
                    |--- a/2.txt
                    |+++ b/2.txt
                """.trimMargin()
            )
        }

        @Test
        fun `staged deleted`() {
            cmd.delete("1.txt")
            cmd.delete(".git/index")
            cmd.cmd("add", ".")

            assertDiffCached(
                """ |diff --git a/1.txt b/1.txt
                    |deleted file mode 100644
                    |index 43dd47e..0000000
                    |--- a/1.txt
                    |+++ b/1.txt
                """.trimMargin()
            )
        }
    }
}
