package com.github.arian.gikt

import com.github.arian.gikt.database.toHexString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

class GiktKtTest {

    @Test
    fun sha1Test() {
        assertEquals(
            "56170f5429b35dea081bb659b884b475ca9329a9",
            "hi there".toByteArray().sha1().toHexString())
    }

    @Test
    fun deflate() {
        val stream = ByteArrayOutputStream()
        "hello".toByteArray().deflateInto(stream)
        val array = stream.toByteArray()

        assertEquals(13, array.size)

        val result = ByteArray(5)
        Inflater().run {
            setInput(array)
            inflate(result)
            end()
        }

        assertEquals("hello", result.toString(Charsets.UTF_8))
    }
}
