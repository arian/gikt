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
}
