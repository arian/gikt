package com.github.arian.gikt.repository

import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.database.TreeDiffMap
import com.github.arian.gikt.database.TreeDiffMapValue
import com.github.arian.gikt.database.TreeEntry
import com.github.arian.gikt.index.Index
import com.github.arian.gikt.parentPaths
import java.nio.file.Path

class Migration(private val repository: Repository, private val treeDiff: TreeDiffMap) {
    fun applyChanges(index: Index.Updater) {
        val plan = planChanges()
        updateWorkspace(plan)
        updateIndex(plan, index)
    }

    private fun updateWorkspace(plan: MigrationPlan) {
        repository.workspace.applyMigration(this, plan)
    }

    private fun updateIndex(plan: MigrationPlan, index: Index.Updater) {
        plan.delete.forEach { index.remove(it.name) }

        fun add(entry: TreeEntry) {
            val stat = repository.workspace.statFile(entry.name)
            index.add(entry.name, entry.oid, stat)
        }

        plan.create.forEach { add(it) }
        plan.update.forEach { add(it) }
    }

    fun blobData(objectId: ObjectId) =
        repository.loadObject(objectId).data

    private fun planChanges(): MigrationPlan {
        return treeDiff
            .values
            .fold(MigrationPlan()) { plan, diff -> planEntry(plan, diff) }
            .let { it.copy(rmdirs = it.rmdirs - it.mkdirs) }
    }

    private fun planEntry(plan: MigrationPlan, diff: TreeDiffMapValue): MigrationPlan =
        when (diff) {
            is TreeDiffMapValue.Addition ->
                plan.copy(
                    mkdirs = plan.mkdirs + diff.new.name.parentPaths(),
                    create = plan.create + diff.new
                )
            is TreeDiffMapValue.Deletion ->
                plan.copy(
                    rmdirs = plan.rmdirs + diff.old.name.parentPaths(),
                    delete = plan.delete + diff.old
                )
            is TreeDiffMapValue.Change ->
                plan.copy(
                    mkdirs = plan.mkdirs + diff.new.name.parentPaths(),
                    update = plan.update + diff.new
                )
        }
}

data class MigrationPlan(
    val mkdirs: Set<Path> = emptySet(),
    val rmdirs: Set<Path> = emptySet(),
    val create: List<TreeEntry> = emptyList(),
    val delete: List<TreeEntry> = emptyList(),
    val update: List<TreeEntry> = emptyList()
)
