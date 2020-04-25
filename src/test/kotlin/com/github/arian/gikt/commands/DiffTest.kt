package com.github.arian.gikt.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
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

    private fun assertDiff(output: String) {
        val execution = cmd.cmd("diff")
        assertEquals(output, execution.stdout.trimEnd())
        assertEquals(0, execution.status)
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
