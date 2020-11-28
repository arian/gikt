package com.github.arian.gikt.merge

import com.github.arian.gikt.database.Commit
import com.github.arian.gikt.database.Database
import com.github.arian.gikt.database.ObjectId

class CommonAncestors(
    private val database: Database,
    private val one: ObjectId,
    private val two: ObjectId
) {

    fun find(): ObjectId? {
        val queue = emptyList<Commit>()
            .insertByDate(database.load(one) as? Commit)
            .insertByDate(database.load(two) as? Commit)

        val flags = mapOf(one to setOf(Flag.PARENT1), two to setOf(Flag.PARENT2))

        return generateSequence(BCA(flags = flags, queue = queue)) { it.step() }
            .lastOrNull()
            ?.result
    }

    private fun BCA.step(): BCA? {
        val commit = queue.firstOrNull() ?: return null
        val tail = queue.drop(1)
        val commitFlags = flags[commit.oid] ?: emptySet()

        if (commitFlags == bothParents) {
            return copy(
                result = commit.oid,
                queue = emptyList()
            )
        }

        val parent = commit.parent
            ?.let { database.load(it) as? Commit }
            ?: return copy(queue = tail)

        val parentFlags = flags[parent.oid] ?: emptySet()

        if (parentFlags.containsAll(commitFlags)) {
            return copy(queue = tail)
        }

        return copy(
            queue = tail.insertByDate(parent),
            flags = flags + (parent.oid to (parentFlags + commitFlags))
        )
    }

    private data class BCA(
        val result: ObjectId? = null,
        val flags: Map<ObjectId, Set<Flag>> = emptyMap(),
        val queue: List<Commit> = emptyList(),
    )

    private enum class Flag {
        PARENT1,
        PARENT2
    }

    companion object {
        private fun List<Commit>.insertByDate(commit: Commit?): List<Commit> {
            commit ?: return this
            return when (val index = indexOfFirst { it.date < commit.date }) {
                -1 -> this + commit
                else -> take(index) + commit + drop(index)
            }
        }

        private val bothParents = setOf(Flag.PARENT1, Flag.PARENT2)
    }
}
