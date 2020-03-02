package com.github.arian.gikt

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Path

internal class PathExtKtTest{

    @Test
    fun relativeTo() {
        assertEquals(
            Path.of("123/xyz"),
            Path.of("/abc/123/xyz").relativeTo(Path.of("/abc"))
        )
    }
}
