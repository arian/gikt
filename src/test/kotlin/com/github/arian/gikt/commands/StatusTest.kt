package com.github.arian.gikt.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StatusTest {

    private val cmd = CommandHelper()

    @BeforeEach
    fun before() {
        cmd.init()
    }

    private fun assertStatus(output: String) {
        val execution = cmd.cmd("status")
        assertEquals("$output\n", execution.stdout)
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
}
