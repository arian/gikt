package com.github.arian.gikt.commands.util

import com.github.arian.gikt.Mode
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.sha1
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PrintDiffTest {

    @Test
    fun `print diff`() {

        val print = PrintDiff(fmt = { _, str -> str })

        val diff = print.diff(
            a = PrintDiff.Target(
                path = "a/b.txt",
                data = "a".toByteArray(),
                oid = ObjectId("a".toByteArray().sha1())
            ),
            b = PrintDiff.Target(
                path = "a/b.txt",
                data = "b".toByteArray(),
                oid = ObjectId("b".toByteArray().sha1())
            )
        )

        assertEquals(
            """
            |diff --git a/a/b.txt b/a/b.txt
            |index 86f7e43..e9d71f5
            |--- a/a/b.txt
            |+++ b/a/b.txt
            |@@ -1,1 +1,1 @@
            |-a
            |+b
            """.trimMargin(),
            diff
        )
    }

    @Test
    fun `print diff with multiple hunks`() {

        val print = PrintDiff(fmt = { _, str -> str })

        val a = (1..15).joinToString(separator = "\n") { "$it" }.toByteArray()
        val b = (1..15).filterNot { it == 2 || it == 12 }.joinToString(separator = "\n") { "$it" }.toByteArray()

        val aOid = ObjectId(a.sha1())
        val bOid = ObjectId(b.sha1())

        val diff = print.diff(
            a = PrintDiff.Target(
                path = "1.txt",
                data = a,
                oid = aOid,
                mode = Mode.REGULAR,
            ),
            b = PrintDiff.Target(
                path = "1.txt",
                data = b,
                oid = bOid,
                mode = Mode.REGULAR,
            )
        )

        assertEquals(
            """
            |diff --git a/1.txt b/1.txt
            |index ${aOid.short}..${bOid.short} 100644
            |--- a/1.txt
            |+++ b/1.txt
            |@@ -1,5 +1,4 @@
            | 1
            |-2
            | 3
            | 4
            | 5
            |@@ -9,7 +8,6 @@
            | 9
            | 10
            | 11
            |-12
            | 13
            | 14
            | 15
            """.trimMargin(),
            diff
        )
    }
}
