package com.github.arian.gikt

import com.github.arian.gikt.database.Commit
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.repository.Repository

class RevList(
    private val repository: Repository,
    private val revs: List<StartPoint> = listOf(StartPoint(Revision(repository, Revision.HEAD)))
) {

    data class StartPoint(val revision: Revision, val interesting: Boolean = true)

    private data class RevState(
        private val repository: Repository,
        val commit: Commit,
        val commits: Map<ObjectId, Commit> = emptyMap(),
        val flags: Flags = Flags(),
        val queue: LinkedHashSet<Commit> = LinkedHashSet(),
        val limited: Boolean = false
    ) {

        fun asSequence(): Sequence<RevState> =
            generateSequence(this) { it.step() }

        fun readAndFlagAllUninterested(): RevState {
            return if (limited) {
                val uninterested = asSequence().last().flags.filterOnlyUninterested()
                copy(flags = uninterested, limited = false)
            } else {
                this
            }
        }

        fun markUninteresting(commit: Commit): RevState =
            copy(
                flags = flags.mark(commit.oid, Flag.UNINTERESTING).markParentsUninteresting(commit, commits),
                limited = true
            )

        private fun step(): RevState? {
            if (!stillInteresting()) {
                return null
            }

            val head = queue.firstOrNull() ?: return null
            val oid = head.oid
            val tail = queue.drop(1).map { it.oid }

            val queue = (tail + listOfNotNull(head.parent))
                .filterNot { flags.marked(it, Flag.SEEN) }
                .mapNotNull { commits[it] ?: repository.loadObject(it) as? Commit }
                .toSortedByDateDescendingSet()

            val commits = commits
                .plus(queue.associateBy { it.oid })
                .let { commitCache ->
                    if (limited) {
                        commitCache
                    } else {
                        commitCache.filterKeys { !flags.marked(it, Flag.SEEN) }
                    }
                }

            val flags = when (flags.marked(oid, Flag.UNINTERESTING)) {
                true -> flags.mark(oid, Flag.SEEN).markParentsUninteresting(head, commits)
                else -> flags.mark(oid, Flag.SEEN)
            }

            return copy(
                commit = head,
                queue = queue,
                commits = commits,
                flags = flags
            )
        }

        private fun stillInteresting(): Boolean {
            val firstInQueue = queue.firstOrNull() ?: return false
            if (commit.date <= firstInQueue.date) {
                return true
            }
            if (queue.any { !flags.marked(it.oid, Flag.UNINTERESTING) }) {
                return true
            }
            return false
        }
    }

    private data class Flags(private val flags: Map<ObjectId, Set<Flag>> = emptyMap()) {
        fun mark(oid: ObjectId, flag: Flag): Flags {
            val oidFlags = flags.getOrDefault(oid, emptySet()) + flag
            return copy(flags = flags + (oid to oidFlags))
        }

        fun marked(oid: ObjectId, flag: Flag): Boolean =
            flags[oid]?.contains(flag) == true

        fun markParentsUninteresting(commit: Commit, commits: Map<ObjectId, Commit>): Flags =
            generateSequence(commit.parent) { commits[it]?.parent }
                .takeWhile { !marked(it, Flag.UNINTERESTING) }
                .fold(this) { flags, parent ->
                    flags.mark(parent, Flag.UNINTERESTING)
                }

        fun filterOnlyUninterested(): Flags =
            copy(
                flags = flags.mapValues { (_, value) ->
                    value.filter { it == Flag.UNINTERESTING }.toSet()
                }
            )
    }

    private enum class Flag {
        SEEN,
        UNINTERESTING
    }

    private fun initialState(startPoints: List<StartPoint>): RevState? {
        val revsCommits = startPoints
            .map { it.revision }
            .mapNotNull { repository.loadObject(it.resolve()) as? Commit }

        val initial = RevState(
            repository = repository,
            commit = revsCommits.firstOrNull() ?: return null,
            commits = revsCommits.associateBy { it.oid },
            queue = revsCommits.toSortedByDateDescendingSet()
        )

        return startPoints.fold(initial) { state, rev ->
            if (rev.interesting) {
                state
            } else {
                val commit = state.commits[rev.revision.resolve()] ?: return@fold state
                state.markUninteresting(commit)
            }
        }
    }

    fun commits(): Sequence<Commit> {
        val initial = initialState(revs)
            ?: initialState(parseStartPoints(repository, listOf(Revision.HEAD)))
            ?: return emptySequence()

        return initial
            .readAndFlagAllUninterested()
            .asSequence()
            .drop(1)
            .filterNot { it.flags.marked(it.commit.oid, Flag.UNINTERESTING) }
            .map { it.commit }
    }

    companion object {

        private fun Collection<Commit>.toSortedByDateDescendingSet() =
            LinkedHashSet(sortedByDescending { it.date }.distinctBy { it.oid })

        private val RANGE = Regex("^(.*)\\.\\.(.*)$")
        private val EXCLUDE = Regex("^\\^(.+)$")

        private fun parseRange(repository: Repository, rev: String): List<StartPoint>? =
            RANGE.find(rev)?.destructured?.let { (uninteresting, interesting) ->
                listOf(
                    StartPoint(
                        revision = Revision(repository, uninteresting.takeIf { it.isNotBlank() } ?: Revision.HEAD),
                        interesting = false
                    ),
                    StartPoint(
                        Revision(repository, interesting.takeIf { it.isNotBlank() } ?: Revision.HEAD),
                        interesting = true
                    )
                )
            }

        private fun parseExclude(repository: Repository, rev: String): List<StartPoint>? =
            EXCLUDE.find(rev)?.destructured?.let { (uninteresting) ->
                listOf(
                    StartPoint(
                        revision = Revision(repository, uninteresting),
                        interesting = false
                    ),
                    StartPoint(
                        Revision(repository, Revision.HEAD),
                        interesting = true
                    )
                )
            }

        private fun parseRev(repository: Repository, rev: String): List<StartPoint> =
            parseRange(repository, rev)
                ?: parseExclude(repository, rev)
                ?: listOf(StartPoint(revision = Revision(repository, rev)))

        fun parseStartPoints(repository: Repository, revs: List<String>): List<StartPoint> =
            revs.flatMap { parseRev(repository, it) }
    }
}
