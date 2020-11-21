package com.github.arian.gikt

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Path

internal class PathFilterTest {

    private fun expectPaths(expected: List<String>, actual: Sequence<Path>) =
        assertEquals(
            expected,
            actual.map { it.toString() }.toList()
        )

    @Test
    fun `match everything for an empty filter`() {
        val filter = PathFilter.build(emptyList())
        val filtered = filter.eachEntry(listOf(Path.of("file.txt")))
        expectPaths(listOf("file.txt"), filtered)
    }

    @Test
    fun `match everything for the root path`() {
        val filter = PathFilter.build(listOf(Path.of("/")))
        val filtered = filter.eachEntry(listOf(Path.of("file.txt")))
        expectPaths(listOf("file.txt"), filtered)
    }

    @Test
    fun `match single file`() {
        val filter = PathFilter.build(listOf(Path.of("file.txt")))
        val filtered = filter.eachEntry(listOf(Path.of("file.txt")))
        expectPaths(listOf("file.txt"), filtered)
    }

    @Test
    fun `do not match single file`() {
        val filter = PathFilter.build(listOf(Path.of("foo.txt")))
        val filtered = filter.eachEntry(listOf(Path.of("file.txt")))
        expectPaths(emptyList(), filtered)
    }

    @Test
    fun `match file with depth`() {
        val filter = PathFilter.build(
            listOf(
                Path.of("a/b/c"),
                Path.of("a/b/d"),
                Path.of("a/e"),
            )
        )

        val filtered = filter.eachEntry(listOf(Path.of("a"), Path.of("b")))
        expectPaths(listOf("a"), filtered)
    }

    @Test
    fun `join the filter with a sub-filter`() {
        val filter = PathFilter.build(
            listOf(
                Path.of("a/b/c"),
                Path.of("a/b/d"),
                Path.of("a/e"),
            )
        )

        val filterWithA = filter.join(Path.of("a"))

        val filtered = filterWithA.eachEntry(listOf(Path.of("b"), Path.of("c")))
        expectPaths(listOf("b"), filtered)
    }

    @Test
    fun `join the sub-filter with for a sub sub-filter`() {
        val filter = PathFilter.build(
            listOf(
                Path.of("a/b/c"),
                Path.of("a/b/d"),
                Path.of("a/e"),
            )
        )

        val filterWithA = filter.join(Path.of("a")).join(Path.of("b"))

        val filtered = filterWithA.eachEntry(listOf(Path.of("b"), Path.of("c")))
        expectPaths(listOf("c"), filtered)
    }

    @Test
    fun `join the filter with a non existing sub-filter should match nothing`() {
        val filter = PathFilter.build(listOf(Path.of("a/b/c")))
        val filterWithA = filter.join(Path.of("b"))
        val filtered = filterWithA.eachEntry(listOf(Path.of("b"), Path.of("c")))
        expectPaths(emptyList(), filtered)
    }

    @Test
    fun `join an matching filter with something should still match everything`() {
        val filter = PathFilter.build(emptyList())
        val filterWithA = filter.join(Path.of("b"))
        val filtered = filterWithA.eachEntry(listOf(Path.of("b"), Path.of("c")))
        expectPaths(listOf("b", "c"), filtered)
    }
}
