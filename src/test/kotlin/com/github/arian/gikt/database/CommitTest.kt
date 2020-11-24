package com.github.arian.gikt.database

import com.github.arian.gikt.utf8
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class CommitTest {

    private val author = Author(
        "arian",
        "arian@example.com",
        ZonedDateTime.now(Clock.fixed(Instant.parse("2019-08-14T10:08:22.00Z"), ZoneId.of("Europe/Amsterdam")))
    )

    @Test
    fun createCommit() {
        val oid = ObjectId("28e5b0ff3555872675b84c7c0d0119d8899743f0")
        val commit = Commit(emptyList(), oid, author, "hello".toByteArray())

        assertEquals(
            """tree $oid
            |author arian <arian@example.com> 1565777302 +0200
            |committer arian <arian@example.com> 1565777302 +0200
            |
            |hello
            """.trimMargin(),
            commit.data.utf8()
        )
    }

    @Test
    fun commitWithParent() {
        val oid = ObjectId("28e5b0ff3555872675b84c7c0d0119d8899743f0")

        val parent = ObjectId(
            "ffe5b0ff3555872675b84c7c0d0119d8899743f0"
        )

        val commit = Commit(listOf(parent), oid, author, "hello".toByteArray())

        assertEquals(
            """tree $oid
              |parent ffe5b0ff3555872675b84c7c0d0119d8899743f0
              |author arian <arian@example.com> 1565777302 +0200
              |committer arian <arian@example.com> 1565777302 +0200
              |
              |hello
              """.trimMargin(),
            commit.data.toString(Charsets.UTF_8)
        )
    }

    @Test
    fun commitWithParents() {
        val oid = ObjectId("28e5b0ff3555872675b84c7c0d0119d8899743f0")

        val parents = listOf(
            ObjectId("ffe5b0ff3555872675b84c7c0d0119d8899743f0"),
            ObjectId("ffe5b0ff3555872675b84c7c0d0119d8899743f1")
        )

        val commit = Commit(parents, oid, author, "hello".toByteArray())

        assertEquals(
            """tree $oid
              |parent ffe5b0ff3555872675b84c7c0d0119d8899743f0
              |parent ffe5b0ff3555872675b84c7c0d0119d8899743f1
              |author arian <arian@example.com> 1565777302 +0200
              |committer arian <arian@example.com> 1565777302 +0200
              |
              |hello
              """.trimMargin(),
            commit.data.toString(Charsets.UTF_8)
        )
    }

    private fun commitWithMessage(msg: String): Commit {
        val oid = ObjectId("28e5b0ff3555872675b84c7c0d0119d8899743f0")
        return Commit(emptyList(), oid, author, msg.toByteArray())
    }

    @Test
    fun `commit title`() {
        val commit = commitWithMessage("title")
        assertEquals("title", commit.title)
    }

    @Test
    fun `commit title should be the first line of the message`() {
        val commit = commitWithMessage("title\nsecond")
        assertEquals("title", commit.title)
    }

    @Test
    fun `empty commit message should result into empty commit title`() {
        val commit = commitWithMessage("")
        assertEquals("", commit.title)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "",
            "28e5b0ff3555872675b84c7c0d0119d8899743f1",
            "28e5b0ff3555872675b84c7c0d0119d8899743f1,28e5b0ff3555872675b84c7c0d0119d8899743f2",
            "28e5b0ff3555872675b84c7c0d0119d8899743f1,28e5b0ff3555872675b84c7c0d0119d8899743f2," +
                "28e5b0ff3555872675b84c7c0d0119d8899743f3,28e5b0ff3555872675b84c7c0d0119d8899743f4"
        ]
    )
    fun parseCommit(parentHex: String) {
        val parents = parentHex
            .split(",")
            .mapNotNull { hex -> hex.takeIf { it.isNotBlank() }?.let { ObjectId(it) } }
        val tree = ObjectId("28e5b0ff3555872675b84c7c0d0119d8899743f0")

        val message = "hello\nfoo\n\nlast\n"
        val commit = Commit(parents, tree, author, message.toByteArray())

        val bytes = commit.data
        val parsed = Commit.parse(bytes)

        assertEquals(parents, parsed.parents)
        assertEquals(parents.firstOrNull(), parsed.parent)
        assertEquals(tree, parsed.tree)
        assertEquals(author, parsed.author)
        assertEquals(message, parsed.message.utf8())
        assertEquals(commit, parsed)
    }
}
