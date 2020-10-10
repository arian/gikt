package com.github.arian.gikt.database

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class AuthorTest {

    @Test
    fun `toString with all data`() {
        val zoneId = ZoneId.of("+0200")
        val author = Author(
            "arian",
            "arian@example.com",
            ZonedDateTime.now(Clock.fixed(Instant.parse("2019-08-14T10:08:22.00Z"), zoneId))
        )
        assertEquals("arian <arian@example.com> 1565777302 +0200", author.toString())
    }

    @Test
    fun `readableTime format`() {
        val zoneId = ZoneId.of("+0000")
        val author = Author(
            "arian",
            "arian@example.com",
            ZonedDateTime.now(Clock.fixed(Instant.parse("2018-02-11T15:14:00.00Z"), zoneId))
        )
        assertEquals("Sun Feb 11 15:14:00 2018 +0000", author.readableTime)
    }

    @ParameterizedTest
    @CsvSource(
        value = [
            "name, john@example.com,  2019-08-14T10:08:22.00Z, +0200",
            "first last, john@example.com,  2019-08-14T10:08:22.00Z, -0200",
            ", john@example.com,  2019-08-14T10:08:22.00Z, -0200",
            "john, ,  2019-08-14T10:08:22.00Z, -0200",
            "name, john@example.com,  2019-08-14T10:08:22.00Z, Europe/Amsterdam"
        ]
    )
    fun `parse author line`(name: String?, email: String?, time: String, offset: String) {
        val author = Author(
            name ?: "",
            email ?: "",
            ZonedDateTime.now(Clock.fixed(Instant.parse(time), ZoneId.of(offset)))
        )
        assertEquals(author, Author.parse(author.toString()))
    }
}
