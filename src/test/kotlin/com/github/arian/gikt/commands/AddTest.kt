package com.github.arian.gikt.commands

import com.github.arian.gikt.Repository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AddTest {

    private fun assertIndex(repo: Repository, expected: List<Pair<String, Boolean>>) {
        val index = repo.index.load()
        assertEquals(expected, index.toList().map { Pair(it.key, it.stat.executable) })
    }

    @Test
    fun `adds a regular file to the index`() {
        val cmd = CommandHelper()
        cmd.init()
        cmd.writeFile("hello.txt", "hello")

        cmd.cmd("add", "hello.txt")

        assertIndex(cmd.repository, listOf("hello.txt" to false))
    }
}
