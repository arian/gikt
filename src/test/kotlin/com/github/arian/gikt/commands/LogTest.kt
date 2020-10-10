package com.github.arian.gikt.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class LogTest {

    private val cmd = CommandHelper()

    @BeforeEach
    fun before() {
        cmd.init()
    }

    @Test
    fun `linear commits should log it in a-chronological order`() {
        val first = cmd.commitFile("a", msg = "first")
        val second = cmd.commitFile("b", msg = "second")

        val execution = cmd.cmd("log")

        assertEquals(0, execution.status)
        assertFalse(execution.stdout.startsWith("\n"))
        assertEquals(
            """
            |commit $second
            |Author: Arian <arian@example.com>
            |Date:   Wed Aug 14 12:08:22 2019 +0200
            |
            |    second
            |
            |commit $first
            |Author: Arian <arian@example.com>
            |Date:   Wed Aug 14 12:08:22 2019 +0200
            |
            |    first
            |
            """.trimMargin(),
            execution.stdout
        )
    }

    @Test
    fun `abbrev-commit option should abbreviate the commit hash`() {
        cmd.commitFile("a", msg = "first")
        val execution = cmd.cmd("log", "--abbrev-commit")
        assertEquals(0, execution.status)
        assertFalse(execution.stdout.startsWith("\n"))
        assertEquals(
            """
            |commit c437776
            |Author: Arian <arian@example.com>
            |Date:   Wed Aug 14 12:08:22 2019 +0200
            |
            |    first
            |
            """.trimMargin(),
            execution.stdout
        )
    }

    @Test
    fun `format=oneline should show each commit on a single line`() {
        val first = cmd.commitFile("a", msg = "first")
        val second = cmd.commitFile("a", msg = "second\nfoo\nbar")
        val execution = cmd.cmd("log", "--format", "oneline")
        assertEquals(0, execution.status)
        assertFalse(execution.stdout.startsWith("\n"))
        assertEquals(
            """
            |$second second
            |$first first
            |
            """.trimMargin(),
            execution.stdout
        )
    }

    @Test
    fun `oneline option should show each commit abbreviated on a single line`() {
        cmd.commitFile("a", msg = "first")
        cmd.commitFile("a", msg = "second\nfoo\nbar")
        val execution = cmd.cmd("log", "--oneline")
        assertEquals(0, execution.status)
        assertFalse(execution.stdout.startsWith("\n"))
        assertEquals(
            """
            |b195aa1 second
            |c437776 first
            |
            """.trimMargin(),
            execution.stdout
        )
    }
}
