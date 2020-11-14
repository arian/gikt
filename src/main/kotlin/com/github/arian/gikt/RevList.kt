package com.github.arian.gikt

import com.github.arian.gikt.database.Commit
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.repository.Repository

class RevList(
    private val repository: Repository,
    private val revs: List<Revision> = listOf(Revision(repository, Revision.HEAD))
) {

    constructor(repository: Repository, rev: Revision) : this(repository, listOf(rev))

    private inner class RevState(
        val commit: Commit,
        val commits: Map<ObjectId, Commit> = emptyMap(),
        val flags: Flags = Flags(),
        val queue: LinkedHashSet<Commit> = LinkedHashSet()
    ) {

        fun step(): RevState? {
            val head = queue.firstOrNull() ?: return null
            val oid = head.oid

            val tail = queue.drop(1).map { it.oid }

            val queue = (tail + listOfNotNull(head.parent))
                .filterNot { flags.marked(it, Flag.SEEN) }
                .mapNotNull { commits[it] ?: repository.loadObject(it) as? Commit }
                .toSortedByDateDescendingSet()

            return RevState(
                commit = head,
                queue = queue,
                commits = (commits + queue.associateBy { it.oid }) - oid,
                flags = flags.mark(oid, Flag.SEEN)
            )
        }
    }

    private data class Flags(private val flags: Map<ObjectId, Set<Flag>> = emptyMap()) {
        fun mark(oid: ObjectId, flag: Flag): Flags {
            val oidFlags = (flags[oid] ?: emptySet()) + flag
            return Flags(flags + (oid to oidFlags))
        }

        fun marked(oid: ObjectId, flag: Flag): Boolean {
            return flags[oid]?.contains(flag) == true
        }
    }

    private enum class Flag {
        SEEN
    }

    private fun Collection<Commit>.toSortedByDateDescendingSet() =
        LinkedHashSet(sortedByDescending { it.date }.distinctBy { it.oid })

    private fun initialState(revs: List<Revision>): RevState? {
        val revsCommits = revs.mapNotNull { repository.loadObject(it.resolve()) as? Commit }
        return RevState(
            commit = revsCommits.firstOrNull() ?: return null,
            commits = revsCommits.associateBy { it.oid },
            queue = revsCommits.toSortedByDateDescendingSet()
        )
    }

    fun commits(): Sequence<Commit> {
        val initial = initialState(revs)
            ?: initialState(listOf(Revision(repository, Revision.HEAD)))

        return generateSequence(initial) { it.step() }
            .drop(1)
            .map { it.commit }
    }
}
