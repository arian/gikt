package com.github.arian.gikt.database

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AuthorTest {

    @Test
    fun `toString with all data`() {
        val zoneId = ZoneId.of("Europe/Amsterdam")
        val author = Author(
            "arian",
            "arian@example.com",
            ZonedDateTime.now(Clock.fixed(Instant.parse("2019-08-14T10:08:22.00Z"), zoneId))
        )
        assertEquals("""arian <arian@example.com> 1565777302 +0200""".trimIndent(), author.toString())
    }
}
