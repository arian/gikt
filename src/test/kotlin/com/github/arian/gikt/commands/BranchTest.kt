package com.github.arian.gikt.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BranchTest {

    private val cmd = CommandHelper()

    @BeforeEach
    fun before() {
        cmd.init()
    }

    @Test
    fun `creates a new branch`() {
        cmd.touch("a")
        cmd.cmd("add", ".")
        cmd.commit("first commit")

        cmd.cmd("branch", "topic")

        val head = cmd.repository.refs.readHead()

        val topicRef = cmd.readFile(".git/refs/heads/topic").trim()

        assertEquals(head?.hex, topicRef)
    }
}
