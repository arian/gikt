package com.github.arian.gikt

import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PathExtKtTest {

    @Test
    fun relativeTo() {
        assertEquals(
            Path.of("123/xyz"),
            Path.of("/abc/123/xyz").relativeTo(Path.of("/abc"))
        )
    }
}
