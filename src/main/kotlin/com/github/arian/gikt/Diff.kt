package com.github.arian.gikt

class Diff {

    companion object {
        private fun lines(str: String) =
            str.lines()

        fun diff(a: String, b: String): List<Edit> {
            return Myers(lines(a), lines(b)).diff()
        }
    }

    enum class DiffType(val symbol: String) {
        Eql(" "),
        Ins("+"),
        Del("-")
    }

    data class Edit(val type: DiffType, val line: String)

    class Myers(private val a: List<String>, private val b: List<String>) {

        fun diff(): List<Edit> {
            return backtrack()
                .map { (prev, cur) ->
                    val (prevX, prevY) = prev
                    val (x, y) = cur

                    when {
                        x == prevX -> Edit(DiffType.Ins, b[prevY])
                        y == prevY -> Edit(DiffType.Del, a[prevX])
                        else -> Edit(DiffType.Eql, a[prevX])
                    }
                }
                .reversed()
        }

        private fun shortestEdit(): List<IntArray> {
            val n = a.size
            val m = b.size
            val max = n + m

            val size = 2 * max + 1
            val i = wrapIndex(size)

            val v = IntArray(size) { 0 }
            v[1] = 0

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

        private fun backtrack(): List<Pair<Point, Point>> {
            var x = a.size
            var y = b.size
            val i = wrapIndex((x + y) * 2 + 1)

            val pairs = mutableListOf<Pair<Point, Point>>()

            shortestEdit()
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
                        pairs.add((x - 1 to y - 1) to (x to y))
                        x -= 1
                        y -= 1
                    }

                    if (d > 0) {
                        pairs.add((prevX to prevY) to (x to y))
                    }

                    x = prevX
                    y = prevY
                }

            return pairs
        }

        private fun wrapIndex(size: Int) = fun(it: Int) = when {
            it < 0 -> it + size
            else -> it
        }
    }
}

typealias Point = Pair<Int, Int>
