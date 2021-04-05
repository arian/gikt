package com.github.arian.gikt.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class DiffTest {
    private val cmd = CommandHelper()

    @BeforeEach
    fun before() {
        cmd.init()
        cmd.commitFile(name = "1.txt", contents = "one", msg = "commit message")
    }

    private fun assertDiffExecution(expected: String, execution: CommandHelper.CommandTestExecution) {
        assertEquals(expected, execution.stdout.trimEnd())
        assertEquals(0, execution.status)
    }

    private fun assertDiff(expected: String, vararg args: String) {
        val execution = cmd.cmd("diff", *args)
        assertDiffExecution(expected, execution)
    }

    @Nested
    inner class WorkspaceChanges {

        @Test
        fun `show diff header of changed file contents`() {
            cmd.writeFile("1.txt", "changed")

            assertDiff(
                """ |diff --git a/1.txt b/1.txt
                    |index 43dd47e..21fb1ec 100644
                    |--- a/1.txt
                    |+++ b/1.txt
                    |@@ -1,1 +1,1 @@
                    |-one
                    |+changed
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
                    |@@ -1,1 +1,1 @@
                    |-one
                    |+changed
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
                    |@@ -1 +0,0 @@
                    |-one
                """.trimMargin()
            )
        }

        @Test
        fun `show diff no-patch`() {
            cmd.writeFile("1.txt", "changed")
            assertDiff(expected = "", "--no-patch")
        }
    }

    @Nested
    inner class CachedChanges {

        private fun assertDiffCached(expected: String, vararg args: String) {
            assertDiff(expected, "--cached", *args)
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
                    |@@ -1,1 +1,1 @@
                    |-one
                    |+changed
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
                    |@@ -0,0 +1 @@
                    |+two
                """.trimMargin()
            )
        }

        @Test
        fun `staged deleted`() {
            cmd.delete("1.txt")
            cmd.resetIndex()

            assertDiffCached(
                """ |diff --git a/1.txt b/1.txt
                    |deleted file mode 100644
                    |index 43dd47e..0000000
                    |--- a/1.txt
                    |+++ b/1.txt
                    |@@ -1 +0,0 @@
                    |-one
                """.trimMargin()
            )
        }

        @Test
        fun `with --staged argument`() {
            cmd.writeFile("1.txt", "changed")
            cmd.cmd("add", ".")

            val execution = cmd.cmd("diff", "--staged")
            assertDiffExecution(
                """ |diff --git a/1.txt b/1.txt
                    |index 43dd47e..21fb1ec 100644
                    |--- a/1.txt
                    |+++ b/1.txt
                    |@@ -1,1 +1,1 @@
                    |-one
                    |+changed
                """.trimMargin(),
                execution
            )
        }

        @Test
        fun `show diff no-patch`() {
            cmd.writeFile("1.txt", "changed")
            cmd.cmd("add", ".")
            assertDiffCached(expected = "", "--no-patch")
        }
    }

    @Nested
    inner class ConflictChanges {

        private fun createMergeConflictChanges() {
            cmd.commitFile("f.txt", "original")

            cmd.cmd("branch", "topic")
            cmd.cmd("checkout", "topic")
            cmd.commitFile("f.txt", "topic")

            cmd.cmd("checkout", "main")
            cmd.commitFile("f.txt", "main")

            cmd.cmd("merge", "topic")
        }

        @ParameterizedTest
        @ValueSource(strings = ["--base", "-1"])
        fun `show diff --base of a both modified change`(arg: String) {
            createMergeConflictChanges()

            assertDiff(
                """
                |* Unmerged path f.txt
                |diff --git a/f.txt b/f.txt
                |index 94f3610..9cc58b5 100644
                |--- a/f.txt
                |+++ b/f.txt
                |@@ -1,1 +1,6 @@
                |-original
                |+<<<<<<< HEAD
                |+main
                |+=======
                |+topic
                |+>>>>>>> topic
                |+
                """.trimMargin(),
                arg,
            )
        }

        @ParameterizedTest
        @ValueSource(strings = ["--ours", "-2"])
        fun `show diff --ours of a both modified change`(arg: String) {
            createMergeConflictChanges()

            assertDiff(
                """
                |* Unmerged path f.txt
                |diff --git a/f.txt b/f.txt
                |index 88d050b..9cc58b5 100644
                |--- a/f.txt
                |+++ b/f.txt
                |@@ -1,1 +1,6 @@
                |+<<<<<<< HEAD
                | main
                |+=======
                |+topic
                |+>>>>>>> topic
                |+
                """.trimMargin(),
                arg,
            )
        }

        @ParameterizedTest
        @ValueSource(strings = ["--theirs", "-3"])
        fun `show diff --theirs of a both modified change`(arg: String) {
            createMergeConflictChanges()

            assertDiff(
                """
                |* Unmerged path f.txt
                |diff --git a/f.txt b/f.txt
                |index b750dfc..9cc58b5 100644
                |--- a/f.txt
                |+++ b/f.txt
                |@@ -1,1 +1,6 @@
                |+<<<<<<< HEAD
                |+main
                |+=======
                | topic
                |+>>>>>>> topic
                |+
                """.trimMargin(),
                arg,
            )
        }
    }
}
