package com.github.arian.gikt

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DiffTest {

    @Test
    fun myers() {
        val d = Diff.diff(
            """
                |A
                |B
                |C
                |A
                |B
                |B
                |A
            """.trimMargin(),
            """
                |C
                |B
                |A
                |B
                |A
                |C
            """.trimMargin()
        )

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

    @Test
    fun diff() {
        val d = Diff.myersDiff(
            "ABCABBA".chunked(1),
            "CBABAC".chunked(1)
        )

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
