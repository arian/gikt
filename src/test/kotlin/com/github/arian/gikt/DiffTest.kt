package com.github.arian.gikt

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DiffTest {

    @Test
    fun myers() {
        val d = Diff.Myers(
            "ABCABBA".chunked(1),
            "CBABAC".chunked(1)
        ).diff()

        assertEquals(
            """
                |-A
                |-B
                | C
                |+B
                | A
                | B
                |-B
                | A
                |+C
            """.trimMargin(),
            d.joinToString("\n") { "${it.type.symbol}${it.line}" }
        )
    }
}
