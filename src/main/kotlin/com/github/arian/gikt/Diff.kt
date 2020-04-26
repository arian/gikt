package com.github.arian.gikt

class Diff {

    enum class DiffType(val symbol: String) {
        Eql(" "),
        Ins("+"),
        Del("-")
    }

    data class Edit(val type: DiffType, val line: String) {
        override fun toString(): String = "${type.symbol}$line"
    }

    private data class PositionPairs(val prevX: Int, val prevY: Int, val x: Int, val y: Int)

    companion object {

        fun diff(a: String, b: String): List<Edit> {
            return myersDiff(a.lines(), b.lines())
        }

        internal fun myersDiff(a: List<String>, b: List<String>): List<Edit> {
            return backtrack(a, b)
                .map { (prevX, prevY, x, y) ->
                    when {
                        x == prevX -> Edit(DiffType.Ins, b[prevY])
                        y == prevY -> Edit(DiffType.Del, a[prevX])
                        else -> Edit(DiffType.Eql, a[prevX])
                    }
                }
                .reversed()
        }

        private fun shortestEdit(a: List<String>, b: List<String>): List<IntArray> {
            val n = a.size
            val m = b.size
            val max = n + m

            val size = 2 * max + 1
            val i = wrapIndex(max)

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

                    while (x < n && y < m && a[x] == b[y]) {
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

        private fun backtrack(a: List<String>, b: List<String>): List<PositionPairs> {
            var x = a.size
            var y = b.size
            val i = wrapIndex(x + y)

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

        private fun wrapIndex(size: Int) = { it: Int -> it + size }
    }
}
