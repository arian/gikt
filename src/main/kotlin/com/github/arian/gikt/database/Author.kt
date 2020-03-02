package com.github.arian.gikt.database

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val zoneFormatter = DateTimeFormatter.ofPattern("Z")

class Author(private val name: String, private val email: String, private val now: ZonedDateTime) {

    override fun toString(): String {
        val timestamp = "${now.toEpochSecond()} ${zoneFormatter.format(now)}"
        return "$name <$email> $timestamp"
    }

}
