package com.github.arian.gikt.database

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ObjectIdKtTest {
    @Test
    fun hexOfId() {
        assertEquals(
            "ff00ab0000000000000000000000000000000000",
            ObjectId(
                ByteArray(20).also {
                    it[0] = 0xFF.toByte()
                    it[2] = 0xAB.toByte()
                }
            ).hex
        )
    }

    @Test
    fun `equals method`() {
        val oid1 = ObjectId("ff00ab0000000000000000000000000000000000")
        val oid2 = ObjectId("ff00ab0000000000000000000000000000000000")
        assertEquals(oid1, oid2)
    }

    @Test
    fun `hash method`() {
        val oid1 = ObjectId("ff00ab0000000000000000000000000000000000")
        val oid2 = ObjectId("ff00ab0000000000000000000000000000000000")
        assertEquals(oid1.hashCode(), oid2.hashCode())
    }

    @Test
    fun bytesOfHex() {
        val bytes = ObjectId("ff00ab0000000000000000000000000000000000").bytes
        assertEquals(20, bytes.size)

        val expected = ByteArray(20).also {
            it[0] = 0xFF.toByte()
            it[2] = 0xAB.toByte()
        }

        expected.forEachIndexed { i, byte ->
            assertEquals(byte, bytes[i])
        }
    }

    @Test
    fun inverse() {
        val result = ObjectId(ObjectId("b6fc4c620b67d95f953a5c1c1230aaab5db5a1b0").bytes)

        assertEquals(
            "b6fc4c620b67d95f953a5c1c1230aaab5db5a1b0",
            result.hex
        )
    }
}
