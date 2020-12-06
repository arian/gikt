package com.github.arian.gikt.merge

import com.github.arian.gikt.database.Commit
import com.github.arian.gikt.database.Database
import com.github.arian.gikt.database.ObjectId

class CommonAncestors(
    private val database: Database,
    private val one: ObjectId,
    private val two: List<ObjectId>
) {

    fun find(): BCA {
        val queue = listOfNotNull(database.load(one) as? Commit)
            .insertByDate(two.mapNotNull { database.load(it) as? Commit })

        val flags = mapOf(one to setOf(Flag.PARENT1)) + two.associateWith { setOf(Flag.PARENT2) }

        return generateSequence(BcaStep(flags = flags, queue = queue)) { it.step() }
            .last()
            .let { step ->
                BCA(
                    results = step.results.map { it.oid }.filterNot { step.flags.marked(it, Flag.STALE) },
                    flags = step.flags
                )
            }
    }

    private fun BcaStep.step(): BcaStep? {
        if (queue.all { flags.marked(it.oid, Flag.STALE) }) {
            return null
        }

        val commit = queue.firstOrNull() ?: return null
        val tail = queue.drop(1)
        val commitFlags = flags[commit.oid] ?: emptySet()

        return if (commitFlags == bothParents) {
            val (parentsToQueue, parentsFlags) = loadParents(commit, commitFlags + Flag.STALE)
            copy(
                results = results.insertByDate(commit),
                flags = flags + parentsFlags + (commit.oid to (commitFlags + Flag.RESULT)),
                queue = tail.insertByDate(parentsToQueue)
            )
        } else {
            val (parentsToQueue, parentsFlags) = loadParents(commit, commitFlags)
            copy(
                flags = flags + parentsFlags,
                queue = tail.insertByDate(parentsToQueue)
            )
        }
    }

    private fun BcaStep.loadParents(
        commit: Commit,
        commitFlags: Set<Flag>
    ): Pair<List<Commit>, List<Pair<ObjectId, Set<Flag>>>> {
        return commit
            .parents
            .mapNotNull { parentOid ->
                val parentFlags = flags[parentOid] ?: emptySet()
                if (parentFlags.containsAll(commitFlags)) {
                    return@mapNotNull null
                }

                val parent = database.load(parentOid) as? Commit
                    ?: return@mapNotNull null

                parent to (parent.oid to (parentFlags + commitFlags))
            }
            .unzip()
    }

    private data class BcaStep(
        val results: List<Commit> = emptyList(),
        val flags: Map<ObjectId, Set<Flag>> = emptyMap(),
        val queue: List<Commit> = emptyList(),
    )

    class BCA(
        val results: List<ObjectId> = emptyList(),
        private val flags: Map<ObjectId, Set<Flag>> = emptyMap(),
    ) {
        fun marked(oid: ObjectId, flag: Flag): Boolean = flags.marked(oid, flag)
    }

    enum class Flag {
        PARENT1,
        PARENT2,
        RESULT,
        STALE,
    }

    companion object {
        private fun List<Commit>.insertByDate(commit: Commit): List<Commit> =
            when (val index = indexOfFirst { it.date < commit.date }) {
                -1 -> this + commit
                else -> take(index) + commit + drop(index)
            }

        private fun List<Commit>.insertByDate(commits: List<Commit>): List<Commit> =
            commits.fold(this) { result, commit -> result.insertByDate(commit) }

        private fun Map<ObjectId, Set<Flag>>.marked(oid: ObjectId, flag: Flag): Boolean =
            get(oid)?.contains(flag) == true

        private val bothParents = setOf(Flag.PARENT1, Flag.PARENT2)
    }
}
