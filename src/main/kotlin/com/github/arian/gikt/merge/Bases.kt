package com.github.arian.gikt.merge

import com.github.arian.gikt.database.Database
import com.github.arian.gikt.database.ObjectId

class Bases(
    private val database: Database,
    one: ObjectId,
    two: ObjectId
) {

    private val common = CommonAncestors(database, one, listOf(two))

    fun find(): List<ObjectId> {
        val commits = common.find().results

        if (commits.size <= 1) {
            return commits
        }

        val redundant = commits.fold(emptySet<ObjectId>()) { rs, commit ->
            if (rs.contains(commit)) {
                return@fold rs
            }

            val others = commits - commit - rs
            val common = CommonAncestors(database, commit, others).find()

            val parent1 = others.filter { common.marked(it, CommonAncestors.Flag.PARENT1) }
            val parent2 = listOfNotNull(commit.takeIf { common.marked(it, CommonAncestors.Flag.PARENT2) })

            rs + parent1 + parent2
        }

        return commits - redundant
    }
}
