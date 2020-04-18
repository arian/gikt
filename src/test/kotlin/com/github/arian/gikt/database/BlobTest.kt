package com.github.arian.gikt.database

import com.github.arian.gikt.utf8
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BlobTest {

    @Test
    fun parse() {
        val blob = Blob(data = "hello".toByteArray())
        val bytes = blob.data
        val parsed = Blob.parse(bytes)
        assertEquals("hello", parsed.data.utf8())
        assertEquals(blob, parsed)
    }
}
