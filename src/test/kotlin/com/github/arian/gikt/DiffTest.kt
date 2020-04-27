package com.github.arian.gikt

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
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
            d.joinToString("\n") { "$it" }
        )
    }

    @Test
    fun `only deletes`() {
        val d = Diff.diff("A", null)

        assertEquals(
            "-A".trimMargin(),
            d.joinToString("\n") { "$it" }
        )
    }

    @Test
    fun `only inserts`() {
        val d = Diff.diff(null, "A")

        assertEquals(
            "+A".trimMargin(),
            d.joinToString("\n") { "$it" }
        )
    }

    private fun diffChars(a: String, b: String) =
        Diff.myersDiff(
            a.chunked(1).mapIndexed { i, s -> Diff.Line(i + 1, s) },
            b.chunked(1).mapIndexed { i, s -> Diff.Line(i + 1, s) }
        )

    @Test
    fun diff() {
        val d = diffChars("ABCABBA", "CBABAC")

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
            d.joinToString("\n") { "$it" }
        )
    }

    @Nested
    inner class Hunks {

        private fun List<Diff.Edit>.fmt() = joinToString { it.toString() }

        @Test
        fun `inserts at beginning`() {
            val hunks = Diff.Hunk.build(diffChars("AAAAAAAA", "1234AAAAAAAA"))
            assertEquals(1, hunks.size)
            assertEquals("@@ -1,3 +1,7 @@", hunks[0].header)
            assertEquals("+1, +2, +3, +4,  A,  A,  A", hunks[0].edits.fmt())
        }

        @Test
        fun `dels at beginning`() {
            val hunks = Diff.Hunk.build(diffChars("1234AAAAAA", "AAAAAA"))
            assertEquals(1, hunks.size)
            assertEquals("@@ -1,7 +1,3 @@", hunks[0].header)
            assertEquals("-1, -2, -3, -4,  A,  A,  A", hunks[0].edits.fmt())
        }

        @Test
        fun `inserts at end`() {
            val hunks = Diff.Hunk.build(diffChars("AAAAAAAA", "AAAAAAAA1234"))
            assertEquals(1, hunks.size)
            assertEquals("@@ -6,3 +6,7 @@", hunks[0].header)
            assertEquals(" A,  A,  A, +1, +2, +3, +4", hunks[0].edits.fmt())
        }

        @Test
        fun `inserts at middle`() {
            val hunks = Diff.Hunk.build(diffChars("AAAAAAAA", "AAAA12AAAA"))
            assertEquals(1, hunks.size)
            assertEquals("@@ -2,6 +2,8 @@", hunks[0].header)
            assertEquals(" A,  A,  A, +1, +2,  A,  A,  A", hunks[0].edits.fmt())
        }

        @Test
        fun `joined hunks`() {
            val hunks = Diff.Hunk.build(diffChars("AAAAAAAAA", "AAAAA1A2AAA"))
            assertEquals(1, hunks.size)
            assertEquals("@@ -3,7 +3,9 @@", hunks[0].header)
            assertEquals(" A,  A,  A, +1,  A, +2,  A,  A,  A", hunks[0].edits.fmt())
        }

        @Test
        fun `multiple hunks`() {
            val hunks = Diff.Hunk.build(diffChars("AAAAAAAA", "123AAAAAAAA1"))
            assertEquals(2, hunks.size)

            assertEquals("@@ -1,3 +1,6 @@", hunks[0].header)
            assertEquals("+1, +2, +3,  A,  A,  A", hunks[0].edits.fmt())

            assertEquals("@@ -6,3 +9,4 @@", hunks[1].header)
            assertEquals(" A,  A,  A, +1", hunks[1].edits.fmt())
        }
    }
}
