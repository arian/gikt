package com.github.arian.gikt.commands

import com.github.arian.gikt.database.ObjectId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BranchTest {

    private val cmd = CommandHelper()
    private var count = 0

    @BeforeEach
    fun before() {
        cmd.init()
    }

    private fun commitFile(): ObjectId {
        cmd.touch("file-${count++}")
        cmd.cmd("add", ".")
        cmd.commit("commit")
        return requireNotNull(cmd.repository.refs.readHead())
    }

    @Test
    fun `creates a new branch`() {
        val head = commitFile()
        val execution = cmd.cmd("branch", "topic")
        assertEquals(0, execution.status)
        val topicRef = cmd.readFile(".git/refs/heads/topic").trim()
        assertEquals(head.hex, topicRef)
    }

    @Test
    fun `creates a new branch from HEAD commit`() {
        commitFile()
        val head = commitFile()

        val execution = cmd.cmd("branch", "topic", "HEAD")
        assertEquals(0, execution.status)

        val topicRef = cmd.readFile(".git/refs/heads/topic").trim()

        assertEquals(head.hex, topicRef)
    }

    @Test
    fun `creates a new branch from parent commit`() {
        val first = commitFile()
        commitFile()

        val execution = cmd.cmd("branch", "topic", "HEAD^")
        assertEquals(0, execution.status)

        val topicRef = cmd.readFile(".git/refs/heads/topic").trim()

        assertEquals(first.hex, topicRef)
    }

    @Test
    fun `creates a new branch from ancestor commit`() {
        commitFile()
        val second = commitFile()
        commitFile()
        commitFile()
        commitFile()

        val execution = cmd.cmd("branch", "topic", "HEAD~3")
        assertEquals(0, execution.status)

        val topicRef = cmd.readFile(".git/refs/heads/topic").trim()

        assertEquals(second.hex, topicRef)
    }
}
