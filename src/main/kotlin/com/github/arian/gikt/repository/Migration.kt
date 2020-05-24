package com.github.arian.gikt.repository

import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.database.TreeDiffMap
import com.github.arian.gikt.database.TreeEntry
import com.github.arian.gikt.parentPaths
import java.nio.file.Path

class Migration(private val repository: Repository, private val treeDiff: TreeDiffMap) {
    fun applyChanges() {
        val plan = planChanges()
        repository.workspace.applyMigration(this, plan)
    }

    fun blobData(objectId: ObjectId) =
        repository.loadObject(objectId).data

    private fun planChanges(): MigrationPlan {
        return treeDiff
            .entries
            .fold(MigrationPlan(), { plan, (_, diff) ->
                val (oldItem, newItem) = diff
                when {
                    oldItem == null && newItem != null -> {
                        plan.copy(
                            mkdirs = plan.mkdirs + newItem.name.parentPaths(),
                            create = plan.create + newItem
                        )
                    }
                    newItem == null && oldItem != null -> {
                        plan.copy(
                            rmdirs = plan.rmdirs + oldItem.name.parentPaths(),
                            delete = plan.delete + oldItem
                        )
                    }
                    newItem != null -> {
                        plan.copy(
                            mkdirs = plan.mkdirs + newItem.name.parentPaths(),
                            update = plan.update + newItem
                        )
                    }
                    else -> plan
                }
            })
            .let { it.copy(rmdirs = it.rmdirs - it.mkdirs) }
    }
}

data class MigrationPlan(
    val mkdirs: Set<Path> = emptySet(),
    val rmdirs: Set<Path> = emptySet(),
    val create: List<TreeEntry> = emptyList(),
    val delete: List<TreeEntry> = emptyList(),
    val update: List<TreeEntry> = emptyList()
)
