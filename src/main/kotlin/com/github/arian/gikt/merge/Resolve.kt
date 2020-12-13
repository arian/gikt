package com.github.arian.gikt.merge

import com.github.arian.gikt.index.Index
import com.github.arian.gikt.repository.Repository

class Resolve(
    private val repository: Repository,
    private val inputs: Inputs,
) {

    fun execute(index: Index.Updater) {
        val baseOid = inputs.baseOids.firstOrNull()
        val mergeOid = inputs.rightOid
        val treeDiff = repository.database.treeDiff(baseOid, mergeOid)
        val migration = repository.migration(treeDiff)
        migration.applyChanges(index)
    }
}
