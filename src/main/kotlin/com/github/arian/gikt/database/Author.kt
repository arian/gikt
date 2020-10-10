package com.github.arian.gikt.database

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.max

private val zoneFormatter = DateTimeFormatter.ofPattern("Z")
private val readableTimeFormatter = DateTimeFormatter.ofPattern("EEE LLL dd HH:mm:ss yyyy Z")

data class Author(
    val name: String,
    val email: String,
    private val now: ZonedDateTime
) {

    val shortDate: String get() =
        DateTimeFormatter.ISO_LOCAL_DATE.format(now)

    val readableTime: String get() =
        readableTimeFormatter.format(now)

    override fun toString(): String {
        val timestamp = "${now.toEpochSecond()} ${zoneFormatter.format(now)}"
        return "$name <$email> $timestamp"
    }

    override fun equals(other: Any?): Boolean =
        when (other) {
            is Author -> other.toString() == toString()
            else -> false
        }

    override fun hashCode(): Int =
        toString().hashCode()

    companion object {
        fun parse(line: String): Author {
            val open = line.indexOf("<")
            val close = max(open, line.indexOf(">"))
            val name = line.slice(0 until open).trim()
            val email = line.slice(open + 1 until close).trim()
            val time = line.slice(close + 2 until line.length).trim().split(" ", limit = 2)
            val timestamp = Instant.ofEpochSecond(time[0].toLong())
            val zone = ZoneId.of(time[1])
            val now = ZonedDateTime.ofInstant(timestamp, zone)
            return Author(name = name, email = email, now = now)
        }
    }
}
