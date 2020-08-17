package com.github.arian.gikt.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class InitTest {

    private val cmd = CommandHelper()

    @Test
    fun init() {
        cmd.init()
        val head = cmd.readFile(".git/HEAD")
        assertEquals("ref: refs/heads/main\n", head)
    }
}
