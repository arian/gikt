package com.github.arian.gikt.database

import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.assertEquals

class CommitTest {

    @Test
    fun createCommit() {
        val oid = ObjectId("28e5b0ff3555872675b84c7c0d0119d8899743f0")

        val zoneId = ZoneId.of("Europe/Amsterdam")
        val author = Author(
            "arian",
            "arian@example.com",
            ZonedDateTime.now(Clock.fixed(Instant.parse("2019-08-14T10:08:22.00Z"), zoneId))
        )

        val commit = Commit(null, oid, author, "hello".toByteArray())

        assertEquals(
            """tree $oid
            |author arian <arian@example.com> 1565777302 +0200
            |committer arian <arian@example.com> 1565777302 +0200
            |
            |hello
            """.trimMargin(), commit.data.toString(Charsets.UTF_8)
        )
    }

    @Test
    fun commitWithParent() {
        val oid = ObjectId("28e5b0ff3555872675b84c7c0d0119d8899743f0")

        val zoneId = ZoneId.of("Europe/Amsterdam")
        val author = Author(
            "arian",
            "arian@example.com",
            ZonedDateTime.now(Clock.fixed(Instant.parse("2019-08-14T10:08:22.00Z"), zoneId))
        )

        val parent = ObjectId(
            "ffe5b0ff3555872675b84c7c0d0119d8899743f0"
        )

        val commit = Commit(parent, oid, author, "hello".toByteArray())

        assertEquals(
            """tree $oid
              |parent ffe5b0ff3555872675b84c7c0d0119d8899743f0
              |author arian <arian@example.com> 1565777302 +0200
              |committer arian <arian@example.com> 1565777302 +0200
              |
              |hello
              """.trimMargin(), commit.data.toString(Charsets.UTF_8)
        )
    }

}
