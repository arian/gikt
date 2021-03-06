package com.github.arian.gikt

import com.github.arian.gikt.database.TreeEntry
import java.nio.file.Path

class PathFilter private constructor(private val routes: Trie) {

    fun each(entries: List<Path>): Sequence<Path> =
        entries
            .asSequence()
            .filter { routes.matched || routes.children.containsKey(it) }

    fun eachEntry(entries: List<TreeEntry>): Sequence<TreeEntry> =
        entries
            .asSequence()
            .filter { routes.matched || routes.children.containsKey(it.key) }

    fun join(name: Path): PathFilter =
        when (routes.matched) {
            true -> PathFilter(routes)
            else -> PathFilter(routes.children[name] ?: Trie.notMatchedNode)
        }

    private data class Trie(
        val matched: Boolean,
        val children: Map<Path, Trie>
    ) {

        companion object {

            val matchedNode = Trie(matched = true, children = emptyMap())
            val notMatchedNode = Trie(matched = false, children = emptyMap())

            fun fromPathParts(paths: List<List<Path>>): Trie {
                val (leafPaths, triesPaths) = paths.partition { it.size == 1 }

                val leaves = leafPaths
                    .associate { it.first() to matchedNode }

                val tries = triesPaths
                    .groupBy { it.first() }
                    .mapValues { (_, lists) -> fromPathParts(lists.map { it.drop(1) }) }

                return notMatchedNode.copy(children = tries + leaves)
            }

            fun fromPaths(paths: List<Path>): Trie {
                val split = paths.filterNot { it.toString().isEmpty() }.map { it.split() }
                return if (split.isEmpty() || split.any { it.isEmpty() }) {
                    matchedNode
                } else {
                    fromPathParts(split)
                }
            }
        }
    }

    companion object {

        /**
         * Matches any path
         */
        val any: PathFilter =
            PathFilter(Trie.matchedNode)

        fun build(paths: List<Path>): PathFilter =
            if (paths.isEmpty()) {
                any
            } else {
                PathFilter(Trie.fromPaths(paths))
            }
    }
}
