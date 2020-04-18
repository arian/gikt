package com.github.arian.gikt.database

import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GiktObjectTest {

    @Test
    fun parse() {
        val blob = Blob(data = "hello".toByteArray(Charsets.UTF_8))
        val parsed = GiktObject.parse(Path.of("."), blob.content)
        assertEquals(blob, parsed)
    }

    @Test
    fun `parse unknown type`() {
        assertThrows<IllegalStateException> {
            GiktObject.parse(Path.of("."), "foo".toByteArray(Charsets.UTF_8))
        }
    }
}
