package com.github.arian.gikt

import com.github.arian.gikt.database.Commit
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.repository.Repository

class RevList(
    private val repository: Repository,
    private val revs: List<Revision> = listOf(Revision(repository, Revision.HEAD))
) {

    constructor(repository: Repository, rev: Revision) : this(repository, listOf(rev))

    private data class RevState(
        val oid: ObjectId,
        val commits: Map<ObjectId, Commit> = emptyMap(),
        val flags: Flags = Flags(),
        val queue: List<ObjectId> = emptyList()
    ) {
        fun addCommit(commit: Commit): RevState = copy(
            oid = commit.oid,
            commits = commits + (commit.oid to commit)
        )
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

    fun commits(): Sequence<Commit> {
        return revs
            .asSequence()
            .flatMap { handleRevision(it) }
            .mapNotNull { it.commits[it.oid] }
    }

    private fun handleRevision(rev: Revision): Sequence<RevState> {
        val head = rev.resolve()
        val initial = loadCommit(RevState(oid = head), head)
        return generateSequence(initial) {
            loadCommit(it, it.commits[it.oid]?.parent)
        }
    }

    private fun loadCommit(state: RevState, oid: ObjectId?): RevState? =
        oid
            ?.let { repository.loadObject(it) as? Commit }
            ?.let { state.addCommit(it) }
}
