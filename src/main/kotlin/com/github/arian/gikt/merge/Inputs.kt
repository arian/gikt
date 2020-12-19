package com.github.arian.gikt.merge

import com.github.arian.gikt.Revision
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.repository.Repository

class Inputs(
    private val repository: Repository,
    val leftName: String,
    val rightName: String,
) {

    val leftOid = Revision(repository, leftName).oid
    val rightOid = Revision(repository, rightName).oid

    val baseOids: List<ObjectId> by lazy {
        Bases(repository.database, leftOid, rightOid).find()
    }

    fun alreadyMerged(): Boolean =
        baseOids == listOf(rightOid)

    fun fastForward(): Boolean =
        baseOids == listOf(leftOid)
}
