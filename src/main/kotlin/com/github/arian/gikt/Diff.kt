package com.github.arian.gikt

class Diff {

    sealed class Edit {
        data class Eql(val lineNumberA: Int, val lineNumberB: Int, val text: String) : Edit() {
            override fun toString(): String = " $text"
        }

        data class Ins(val line: Line) : Edit() {
            override fun toString(): String = "+${line.text}"
        }

        data class Del(val line: Line) : Edit() {
            override fun toString(): String = "-${line.text}"
        }
    }

    data class Hunk(val aStart: Int, val bStart: Int, val edits: List<Edit>) {

        val header: String
            get() {
                val aOffset = "$aStart,${edits.count { it !is Edit.Ins }}"
                val bOffset = "$bStart,${edits.count { it !is Edit.Del }}"
                return "@@ -$aOffset +$bOffset @@"
            }

        companion object {

            private const val CONTEXT = 3

            fun build(edits: List<Edit>): List<Hunk> {
                return filter(edits, emptyList(), 0)
                    .map {
                        val hunkEdits = edits.subList(it.start, it.end)
                        when (val edit = hunkEdits.first()) {
                            is Edit.Eql -> Hunk(edit.lineNumberA, edit.lineNumberB, hunkEdits)
                            // can only happen at start of the file (for example when the entire file has changed)
                            else -> Hunk(1, 1, hunkEdits)
                        }
                    }
            }

            private data class HunkStartEnd(val start: Int, val end: Int)

            private tailrec fun filter(
                edits: List<Edit>,
                startEnds: List<HunkStartEnd>,
                offset: Int
            ): List<HunkStartEnd> {

                val firstEditIndex = edits
                    .drop(offset)
                    .indexOfFirst { it !is Edit.Eql }
                    .takeIf { it >= 0 }
                    ?.let { it + offset }
                    ?: return startEnds

                val lastEditIndex = edits
                    .drop(firstEditIndex)
                    .indexOfFirst { it is Edit.Eql }
                    .takeIf { it >= 0 }
                    ?.let { it + firstEditIndex }
                    ?: edits.size

                val newStart = (firstEditIndex - CONTEXT).coerceAtLeast(offset)
                val newEnd = (lastEditIndex + CONTEXT).coerceAtMost(edits.size)

                val newStartEnds = startEnds
                    .lastOrNull()
                    ?.takeIf { it.end >= newStart }
                    ?.let { startEnds.dropLast(1) + HunkStartEnd(it.start, newEnd) }
                    ?: startEnds + HunkStartEnd(newStart, newEnd)

                return if (newEnd >= edits.size) {
                    newStartEnds
                } else {
                    filter(edits, newStartEnds, lastEditIndex)
                }
            }
        }
    }

    data class Line(val number: Int, val text: String)

    private data class PositionPairs(val prevX: Int, val prevY: Int, val x: Int, val y: Int)

    companion object {

        fun diff(a: String?, b: String?): List<Edit> =
            myersDiff(lines(a), lines(b))

        private fun lines(a: String?): List<Line> =
            when (a) {
                null -> emptyList()
                else -> a.lines().mapIndexed { i, l -> Line(i + 1, l) }
            }

        fun diffHunks(a: String?, b: String?): List<Hunk> {
            return Hunk.build(diff(a, b))
        }

        internal fun myersDiff(a: List<Line>, b: List<Line>): List<Edit> {
            return backtrack(a, b)
                .map { (prevX, prevY, x, y) ->
                    when {
                        x == prevX -> Edit.Ins(b[prevY])
                        y == prevY -> Edit.Del(a[prevX])
                        else -> Edit.Eql(a[prevX].number, b[prevY].number, a[prevX].text)
                    }
                }
                .reversed()
        }

        private fun shortestEdit(a: List<Line>, b: List<Line>): List<IntArray> {
            val n = a.size
            val m = b.size
            val max = n + m

            val size = 2 * max + 1
            val i = wrapIndex(max, size)

            val v = IntArray(size) { 0 }

            val trace = mutableListOf<IntArray>()

            (0..max).step(1).forEach { d ->

                trace.add(v.clone())

                (-d..d).step(2).forEach { k ->

                    var x = if (k == -d || (k != d && v[i(k - 1)] < v[i(k + 1)])) {
                        v[i(k + 1)]
                    } else {
                        v[i(k - 1)] + 1
                    }

                    var y = x - k

                    while (x < n && y < m && a[x].text == b[y].text) {
                        x += 1
                        y += 1
                    }

                    v[i(k)] = x

                    if (x >= n && y >= m) {
                        return trace
                    }
                }
            }

            return trace
        }

        private fun backtrack(a: List<Line>, b: List<Line>): List<PositionPairs> {
            var x = a.size
            var y = b.size
            val i = wrapIndex(x + y, (x + y) * 2 + 1)

            val pairs = mutableListOf<PositionPairs>()

            shortestEdit(a, b)
                .mapIndexed { d, v -> (d to v) }
                .reversed()
                .forEach { (d, v) ->

                    val k = x - y

                    val prevK = if (k == -d || (k != -d && v[i(k - 1)] < v[i(k + 1)])) {
                        k + 1
                    } else {
                        k - 1
                    }

                    val prevX = v[i(prevK)]
                    val prevY = prevX - prevK

                    while (x > prevX && y > prevY) {
                        pairs.add(PositionPairs(x - 1, y - 1, x, y))
                        x -= 1
                        y -= 1
                    }

                    if (d > 0) {
                        pairs.add(PositionPairs(prevX, prevY, x, y))
                    }

                    x = prevX
                    y = prevY
                }

            return pairs
        }

        private fun wrapIndex(max: Int, size: Int) = { it: Int -> (it + max) % size }
    }
}
