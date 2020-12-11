package com.github.arian.gikt

import com.github.arian.gikt.database.Commit
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.database.TreeDiffMap
import com.github.arian.gikt.repository.Repository
import java.nio.file.Path

class RevList(
    private val repository: Repository,
    private val revs: List<StartPoint> = listOf(StartPoint.Rev(Revision(repository, Revision.HEAD)))
) {

    fun itemsWithPatches(): Sequence<Item> =
        revListStates().map {
            Item.CommitWithPatch(
                commit = it.commit,
                patch = it.diff[it.commit.parent]
                    ?: repository.database.treeDiff(it.commit.parent, it.commit.oid, it.filter)
            )
        }

    fun items(): Sequence<Item> =
        revListStates().map { Item.JustCommit(it.commit) }

    fun commits(): Sequence<Commit> =
        revListStates().map { it.commit }

    private fun startCommits(startPoints: List<StartPoint>): List<Commit> {
        return startPoints
            .filterIsInstance<StartPoint.Rev>()
            .map { it.revision }
            .mapNotNull { repository.loadObject(it.oid) as? Commit }
    }

    private fun initialState(startPoints: List<StartPoint>): RevState? {
        val revsCommits = startCommits(startPoints).takeIf { it.isNotEmpty() }
            ?: startCommits(parseStartPoints(repository, listOf(Revision.HEAD)))

        val filter = startPoints
            .filterIsInstance<StartPoint.Prune>()
            .map { it.path }
            .let { PathFilter.build(it) }

        val initial = RevState(
            filter = filter,
            commit = revsCommits.firstOrNull() ?: return null,
            commits = revsCommits.associateBy { it.oid },
            queue = revsCommits.toSortedByDateDescendingSet()
        )

        return startPoints
            .filterIsInstance<StartPoint.Rev>()
            .fold(initial) { state, rev ->
                if (rev.interesting) {
                    state
                } else {
                    val commit = state.commits[rev.revision.oid] ?: return@fold state
                    state.markUninteresting(commit)
                }
            }
    }

    private fun revListStates(): Sequence<RevState> {
        val initial = initialState(revs) ?: return emptySequence()

        return initial
            .readAndFlagAllUninterested()
            .asSequence()
            .drop(1)
            .filterNot { it.flags.marked(it.commit.oid, Flag.UNINTERESTING) }
            .filterNot { it.flags.marked(it.commit.oid, Flag.TREESAME) }
    }

    sealed class StartPoint {
        data class Rev(val revision: Revision, val interesting: Boolean = true) : StartPoint()
        data class Prune(val path: Path) : StartPoint()
    }

    sealed class Item {
        abstract val commit: Commit
        val oid: ObjectId get() = commit.oid

        data class CommitWithPatch(override val commit: Commit, val patch: TreeDiffMap) : Item()
        data class JustCommit(override val commit: Commit) : Item()
    }

    private data class RevState(
        val filter: PathFilter,
        val commit: Commit,
        val commits: Map<ObjectId, Commit> = emptyMap(),
        val flags: Flags = Flags(),
        val queue: LinkedHashSet<Commit> = LinkedHashSet(),
        val limited: Boolean = false,
        val diff: Map<ObjectId?, TreeDiffMap> = emptyMap(),
    ) {

        fun markUninteresting(commit: Commit): RevState =
            copy(
                flags = flags.mark(commit.oid, Flag.UNINTERESTING).markParentsUninteresting(commit, commits),
                limited = true
            )

        fun stillInteresting(): Boolean {
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

        fun applyIf(predicate: Boolean, function: Flags.() -> Flags): Flags =
            if (predicate) this.function() else this

        fun marked(oid: ObjectId, flag: Flag): Boolean =
            flags[oid]?.contains(flag) == true

        fun markParentsUninteresting(commit: Commit, commits: Map<ObjectId, Commit>): Flags =
            generateSequence(
                commit.parents.firstOrNull() to commit.parents.drop(1)
            ) { (head, tail) ->
                val first = tail.firstOrNull() ?: return@generateSequence null
                first to tail.drop(1) + commits[head]?.parents.orEmpty()
            }
                .mapNotNull { (parent) -> parent }
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
        UNINTERESTING,
        TREESAME,
    }

    private fun RevState.readAndFlagAllUninterested(): RevState {
        return if (limited) {
            val uninterested = asSequence().last().flags.filterOnlyUninterested()
            copy(flags = uninterested, limited = false)
        } else {
            this
        }
    }

    private fun RevState.asSequence(): Sequence<RevState> =
        generateSequence(this) { it.step() }

    private fun RevState.step(): RevState? {
        if (!stillInteresting()) {
            return null
        }

        val head = queue.firstOrNull() ?: return null
        val oid = head.oid
        val tail = queue.drop(1).map { it.oid }

        val parentDiffs = if (filter === PathFilter.any) {
            null
        } else {
            (head.parents.takeIf { it.isNotEmpty() } ?: listOf(null)).map {
                it to repository.database.treeDiff(it, head.oid, filter)
            }
        }

        val emptyDiffParent = parentDiffs?.find { (_, diff) -> diff.isEmpty() }
        val emptyDiffParentOid = emptyDiffParent?.first

        val parents = if (emptyDiffParentOid != null) {
            listOf(emptyDiffParentOid)
        } else {
            head.parents
        }

        val queue = (tail + parents)
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

        val flags = flags
            .mark(oid, Flag.SEEN)
            .applyIf(flags.marked(oid, Flag.UNINTERESTING)) { markParentsUninteresting(head, commits) }
            .applyIf(emptyDiffParent != null) { mark(oid, Flag.TREESAME) }

        return copy(
            commit = head,
            queue = queue,
            commits = commits,
            flags = flags,
            diff = parentDiffs?.toMap().orEmpty()
        )
    }

    companion object {

        private fun Collection<Commit>.toSortedByDateDescendingSet() =
            LinkedHashSet(sortedByDescending { it.date }.distinctBy { it.oid })

        private val RANGE = Regex("^(.*)\\.\\.(.*)$")
        private val EXCLUDE = Regex("^\\^(.+)$")

        private fun parsePath(repository: Repository, rev: String): List<StartPoint>? =
            repository.resolvePath(rev)
                .takeIf { repository.workspace.statFileOrNull(rev) != null }
                ?.let { listOf(StartPoint.Prune(repository.relativePath(it.normalize()))) }

        private fun parseRange(repository: Repository, rev: String): List<StartPoint>? =
            RANGE.find(rev)?.destructured?.let { (uninteresting, interesting) ->
                listOf(
                    StartPoint.Rev(
                        revision = Revision(
                            repository,
                            uninteresting.takeIf { it.isNotBlank() } ?: Revision.HEAD
                        ),
                        interesting = false
                    ),
                    StartPoint.Rev(
                        Revision(repository, interesting.takeIf { it.isNotBlank() } ?: Revision.HEAD),
                        interesting = true
                    )
                )
            }

        private fun parseExclude(repository: Repository, rev: String): List<StartPoint>? =
            EXCLUDE.find(rev)?.destructured?.let { (uninteresting) ->
                listOf(
                    StartPoint.Rev(
                        revision = Revision(repository, uninteresting),
                        interesting = false
                    ),
                    StartPoint.Rev(
                        Revision(repository, Revision.HEAD),
                        interesting = true
                    )
                )
            }

        private fun parseRev(repository: Repository, rev: String): List<StartPoint> =
            parsePath(repository, rev)
                ?: parseRange(repository, rev)
                ?: parseExclude(repository, rev)
                ?: listOf(StartPoint.Rev(revision = Revision(repository, rev)))

        fun parseStartPoints(repository: Repository, revs: List<String>): List<StartPoint> =
            revs.flatMap { parseRev(repository, it) }
    }
}
