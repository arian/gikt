package com.github.arian.gikt.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class LogTest {

    private val cmd = CommandHelper()

    @BeforeEach
    fun before() {
        cmd.init()
    }

    @Test
    fun `linear commits should log it in a-chronological order`() {
        val first = cmd.commitFile("a", msg = "first")
        val second = cmd.commitFile("b", msg = "second")
        val third = cmd.commitFile("c", msg = "third")

        val execution = cmd.cmd("log")

        assertEquals(0, execution.status)
        assertFalse(execution.stdout.startsWith("\n"))
        assertEquals(
            """
            |commit $third
            |Author: Arian <arian@example.com>
            |Date:   Wed Aug 14 12:08:22 2019 +0200
            |
            |    third
            |
            |commit $second
            |Author: Arian <arian@example.com>
            |Date:   Wed Aug 14 12:08:22 2019 +0200
            |
            |    second
            |
            |commit $first
            |Author: Arian <arian@example.com>
            |Date:   Wed Aug 14 12:08:22 2019 +0200
            |
            |    first
            |
            """.trimMargin(),
            execution.stdout
        )
    }

    @Test
    fun `abbrev-commit option should abbreviate the commit hash`() {
        cmd.commitFile("a", msg = "first")
        val execution = cmd.cmd("log", "--abbrev-commit")
        assertEquals(0, execution.status)
        assertFalse(execution.stdout.startsWith("\n"))
        assertEquals(
            """
            |commit c437776
            |Author: Arian <arian@example.com>
            |Date:   Wed Aug 14 12:08:22 2019 +0200
            |
            |    first
            |
            """.trimMargin(),
            execution.stdout
        )
    }

    @Test
    fun `format=oneline should show each commit on a single line`() {
        val first = cmd.commitFile("a", msg = "first")
        val second = cmd.commitFile("a", msg = "second\nfoo\nbar")
        val execution = cmd.cmd("log", "--format", "oneline")
        assertEquals(0, execution.status)
        assertFalse(execution.stdout.startsWith("\n"))
        assertEquals(
            """
            |$second second
            |$first first
            |
            """.trimMargin(),
            execution.stdout
        )
    }

    @Test
    fun `oneline option should show each commit abbreviated on a single line`() {
        cmd.commitFile("a", msg = "first")
        cmd.commitFile("a", msg = "second\nfoo\nbar")
        val execution = cmd.cmd("log", "--oneline")
        assertEquals(0, execution.status)
        assertFalse(execution.stdout.startsWith("\n"))
        assertEquals(
            """
            |b195aa1 second
            |c437776 first
            |
            """.trimMargin(),
            execution.stdout
        )
    }

    @Test
    fun `decorate flag shows the branch names`() {
        val commit1 = cmd.commitFile("a", msg = "first")
        val commit2 = cmd.commitFile("a", msg = "second")
        cmd.cmd("branch", "topic")
        val commit3 = cmd.commitFile("a", msg = "third")
        val commit4 = cmd.commitFile("a", msg = "fourth")
        cmd.cmd("branch", "new-topic")

        val execution = cmd.cmd("log", "--format", "oneline", "--decorate", "short")
        assertEquals(0, execution.status)
        assertFalse(execution.stdout.startsWith("\n"))
        assertEquals(
            """
            |$commit4 (HEAD -> main, new-topic) fourth
            |$commit3 third
            |$commit2 (topic) second
            |$commit1 first
            |
            """.trimMargin(),
            execution.stdout
        )
    }

    @Test
    fun `decorate flag HEAD`() {
        val commit1 = cmd.commitFile("a", msg = "first")
        cmd.cmd("checkout", "HEAD")

        val execution = cmd.cmd("log", "--format", "oneline", "--decorate", "short")
        assertEquals(0, execution.status)
        assertFalse(execution.stdout.startsWith("\n"))
        assertEquals(
            """
            |$commit1 (HEAD, main) first
            |
            """.trimMargin(),
            execution.stdout
        )
    }

    @Test
    fun `decorate flag full`() {
        val commit1 = cmd.commitFile("a", msg = "first")
        cmd.cmd("branch", "topic")
        val commit2 = cmd.commitFile("a", msg = "second")

        val execution = cmd.cmd("log", "--format", "oneline", "--decorate", "full")
        assertEquals(0, execution.status)
        assertFalse(execution.stdout.startsWith("\n"))
        assertEquals(
            """
            |$commit2 (HEAD -> refs/heads/main) second
            |$commit1 (refs/heads/topic) first
            |
            """.trimMargin(),
            execution.stdout
        )
    }

    @Test
    fun `decorate flag medium format`() {
        val commit = cmd.commitFile("a", msg = "first")
        val execution = cmd.cmd("log", "--decorate", "short")
        assertEquals(0, execution.status)
        assertEquals(
            """
            |commit $commit (HEAD -> main)
            |Author: Arian <arian@example.com>
            |Date:   Wed Aug 14 12:08:22 2019 +0200
            |
            |    first
            |
            """.trimMargin(),
            execution.stdout
        )
    }

    @Test
    fun `no-decorate flag`() {
        cmd.commitFile("a", msg = "first")
        val execution = cmd.cmd("log", "--oneline", "--no-decorate")
        assertEquals(0, execution.status)
        assertEquals(
            """
            |c437776 first
            |
            """.trimMargin(),
            execution.stdout
        )
    }

    @Test
    fun `patch flag`() {
        val commit1 = cmd.commitFile("file", contents = "a", msg = "first")
        val commit2 = cmd.commitFile("file", contents = "b", msg = "second")
        val execution = cmd.cmd("log", "--patch")
        assertEquals(0, execution.status)
        assertEquals(
            """
            |commit $commit2
            |Author: Arian <arian@example.com>
            |Date:   Wed Aug 14 12:08:22 2019 +0200
            |
            |    second
            |
            |diff --git a/file b/file
            |index 2e65efe..63d8dbd 100644
            |--- a/file
            |+++ b/file
            |@@ -1,1 +1,1 @@
            |-a
            |+b
            |
            |commit $commit1
            |Author: Arian <arian@example.com>
            |Date:   Wed Aug 14 12:08:22 2019 +0200
            |
            |    first
            |
            |diff --git a/file b/file
            |new file mode 100644
            |index 0000000..2e65efe
            |--- a/file
            |+++ b/file
            |@@ -0,0 +1 @@
            |+a
            |
            """.trimMargin(),
            execution.stdout
        )
    }

    @Test
    fun `patch flag oneline`() {
        val commit1 = cmd.commitFile("file", contents = "a", msg = "first")
        val commit2 = cmd.commitFile("file", contents = "b", msg = "second")
        cmd.writeFile("file", "c")
        val commit3 = cmd.commitFile("other", contents = "third\nabc", msg = "third")

        val execution = cmd.cmd("log", "--patch", "--oneline")

        assertEquals(0, execution.status)
        assertEquals(
            """
            |${commit3.short} third
            |diff --git a/file b/file
            |index 63d8dbd..3410062 100644
            |--- a/file
            |+++ b/file
            |@@ -1,1 +1,1 @@
            |-b
            |+c
            |diff --git a/other b/other
            |new file mode 100644
            |index 0000000..425b849
            |--- a/other
            |+++ b/other
            |@@ -0,0 +1,2 @@
            |+third
            |+abc
            |${commit2.short} second
            |diff --git a/file b/file
            |index 2e65efe..63d8dbd 100644
            |--- a/file
            |+++ b/file
            |@@ -1,1 +1,1 @@
            |-a
            |+b
            |${commit1.short} first
            |diff --git a/file b/file
            |new file mode 100644
            |index 0000000..2e65efe
            |--- a/file
            |+++ b/file
            |@@ -0,0 +1 @@
            |+a
            |
            """.trimMargin(),
            execution.stdout
        )
    }

    @Test
    fun `start argument`() {
        val commit1 = cmd.commitFile("a", msg = "first")
        val commit2 = cmd.commitFile("a", msg = "second")
        val commit3 = cmd.commitFile("a", msg = "third")
        cmd.commitFile("a", msg = "fourth")
        cmd.commitFile("a", msg = "fifth")

        val execution = cmd.cmd("log", "--format", "oneline", "HEAD^^")
        assertEquals(0, execution.status)
        assertEquals(
            """
            |$commit3 third
            |$commit2 second
            |$commit1 first
            |
            """.trimMargin(),
            execution.stdout
        )
    }

    @Test
    fun `multiple start arguments`() {
        val commit1 = cmd.commitFile("a", msg = "first", timeOffset = 0)
        val commit2 = cmd.commitFile("a", msg = "second", timeOffset = 1)
        cmd.cmd("branch", "main")
        cmd.cmd("checkout", "main^")
        val commit3 = cmd.commitFile("a", msg = "third", timeOffset = 2)
        val commit4 = cmd.commitFile("a", msg = "fourth", timeOffset = 3)
        cmd.cmd("branch", "topic")

        val execution = cmd.cmd("log", "--format", "oneline", "main", "topic")
        assertEquals(0, execution.status)
        assertEquals(
            """
            |$commit4 fourth
            |$commit3 third
            |$commit2 second
            |$commit1 first
            |
            """.trimMargin(),
            execution.stdout
        )
    }

    @Test
    fun `excluded branch`() {
        cmd.commitFile("a", msg = "first", timeOffset = 0)
        val commit2 = cmd.commitFile("a", msg = "second", timeOffset = 1)
        cmd.cmd("branch", "main")
        cmd.cmd("checkout", "main^")
        cmd.commitFile("a", msg = "third", timeOffset = 2)
        cmd.commitFile("a", msg = "fourth", timeOffset = 3)
        cmd.cmd("branch", "topic")

        val execution = cmd.cmd("log", "--format", "oneline", "..main")
        assertEquals(0, execution.status)
        assertEquals(
            """
            |$commit2 second
            |
            """.trimMargin(),
            execution.stdout
        )
    }

    @Test
    fun `filter paths`() {
        val commit1 = cmd.commitFile("a", contents = "1", msg = "first")
        val commit2 = cmd.commitFile("a", contents = "2", msg = "second")
        cmd.commitFile("b", msg = "third")
        val commit4 = cmd.commitFile("a", contents = "3", msg = "fourth")

        val execution = cmd.cmd("log", "--oneline", "$commit4", "a")
        assertEquals(0, execution.status)
        assertEquals(
            """
            |${commit4.short} fourth
            |${commit2.short} second
            |${commit1.short} first
            |
            """.trimMargin(),
            execution.stdout
        )
    }

    @Test
    fun `filter paths and show diffs`() {
        cmd.commitFile("a", contents = "1", msg = "first")
        cmd.commitFile("a", contents = "2", msg = "second")
        cmd.commitFile("b", msg = "third")
        val commit4 = cmd.commitFile("a", contents = "3", msg = "fourth")

        val execution = cmd.cmd("log", "--oneline", "-p", "$commit4", "a")
        assertEquals(0, execution.status)
        assertEquals(
            """
            |1944a87 fourth
            |diff --git a/a b/a
            |index d8263ee..e440e5c 100644
            |--- a/a
            |+++ b/a
            |@@ -1,1 +1,1 @@
            |-2
            |+3
            |3708e4a second
            |diff --git a/a b/a
            |index 56a6051..d8263ee 100644
            |--- a/a
            |+++ b/a
            |@@ -1,1 +1,1 @@
            |-1
            |+2
            |5b81e73 first
            |diff --git a/a b/a
            |new file mode 100644
            |index 0000000..56a6051
            |--- a/a
            |+++ b/a
            |@@ -0,0 +1 @@
            |+1
            |
            """.trimMargin(),
            execution.stdout
        )
    }

    @Test
    fun `filter paths from HEAD`() {
        val commit1 = cmd.commitFile("a", contents = "1", msg = "first")
        val commit2 = cmd.commitFile("a", contents = "2", msg = "second")
        cmd.commitFile("b", msg = "third")
        val commit4 = cmd.commitFile("a", contents = "3", msg = "fourth")

        val execution = cmd.cmd("log", "--oneline", "a")
        assertEquals(0, execution.status)
        assertEquals(
            """
            |${commit4.short} fourth
            |${commit2.short} second
            |${commit1.short} first
            |
            """.trimMargin(),
            execution.stdout
        )
    }

    @Test
    fun `hide patches for merge commits`() {
        cmd.cmd("branch", "topic")
        val commit1 = cmd.commitFile("a", contents = "1", msg = "first")
        val commit2 = cmd.commitFile("a", contents = "2", msg = "second")
        cmd.cmd("checkout", "topic")
        val commit3 = cmd.commitFile("b", msg = "third")
        cmd.cmd("merge", "main", stdin = "merge main")

        val execution = cmd.cmd("log", "--oneline", "-p")
        assertEquals(0, execution.status)
        assertEquals(
            """
            |ad2dd18 merge main
            |${commit3.short} third
            |diff --git a/b b/b
            |new file mode 100755
            |index 0000000..e69de29
            |--- a/b
            |+++ b/b
            |@@ -0,0 +1 @@
            |+
            |${commit2.short} second
            |diff --git a/a b/a
            |index 56a6051..d8263ee 100644
            |--- a/a
            |+++ b/a
            |@@ -1,1 +1,1 @@
            |-1
            |+2
            |${commit1.short} first
            |diff --git a/a b/a
            |new file mode 100644
            |index 0000000..56a6051
            |--- a/a
            |+++ b/a
            |@@ -0,0 +1 @@
            |+1
            |
            """.trimMargin(),
            execution.stdout
        )
    }
}
