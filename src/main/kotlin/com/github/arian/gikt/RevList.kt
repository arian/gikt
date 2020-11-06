package com.github.arian.gikt

import com.github.arian.gikt.database.Commit
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.repository.Repository

class RevList(
    private val repository: Repository,
    private val start: Revision = Revision(repository, Revision.HEAD)
) {

    fun commits(): Sequence<Commit> {
        val head = start.resolve()
        return generateSequence(loadCommit(head)) { loadCommit(it.parent) }
    }

    private fun loadCommit(oid: ObjectId?): Commit? =
        oid?.let { repository.loadObject(it) as? Commit }
}
