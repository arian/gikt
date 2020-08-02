package com.github.arian.gikt.repository

import com.github.arian.gikt.FileStat
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.database.TreeDiffMap
import com.github.arian.gikt.database.TreeDiffMapValue
import com.github.arian.gikt.database.TreeEntry
import com.github.arian.gikt.index.Index
import com.github.arian.gikt.parentPaths
import com.github.arian.gikt.repository.MigrationPlan.Conflicts
import java.nio.file.Path

class Migration(private val repository: Repository, private val treeDiff: TreeDiffMap) {

    private val inspector = Inspector(repository)

    fun applyChanges(index: Index.Updater): MigrationResult {
        val plan = planChanges(index)

        val errors = collectErrors(plan.conflicts)

        if (errors.isEmpty()) {
            updateWorkspace(plan)
            updateIndex(plan, index)
        }

        return MigrationResult(errors)
    }

    fun blobData(objectId: ObjectId) =
        repository.loadObject(objectId).data

    private fun updateWorkspace(plan: MigrationPlan) {
        repository.workspace.applyMigration(this, plan)
    }

    private fun updateIndex(plan: MigrationPlan, index: Index.Updater) {
        plan.delete.forEach { index.remove(it.name) }
        (plan.create + plan.update).forEach { entry ->
            val stat = repository.workspace.statFile(entry.name)
            index.add(entry.name, entry.oid, stat)
        }
    }

    private fun planChanges(index: Index.Loaded): MigrationPlan {
        return treeDiff
            .values
            .fold(MigrationPlan()) { plan, diff -> planEntry(index, plan, diff) }
            .let { it.copy(rmdirs = it.rmdirs - it.mkdirs) }
    }

    private fun planEntry(index: Index.Loaded, plan: MigrationPlan, diff: TreeDiffMapValue): MigrationPlan =
        when (diff) {
            is TreeDiffMapValue.Addition ->
                plan.copy(
                    mkdirs = plan.mkdirs + diff.new.name.parentPaths(),
                    create = plan.create + diff.new,
                    conflicts = plan.conflicts + checkOverrideAdditionConflicts(index, diff)
                )
            is TreeDiffMapValue.Deletion ->
                plan.copy(
                    rmdirs = plan.rmdirs + diff.old.name.parentPaths(),
                    delete = plan.delete + diff.old,
                    conflicts = plan.conflicts + checkOverrideDeletionConflicts(index, diff)
                )
            is TreeDiffMapValue.Change ->
                plan.copy(
                    mkdirs = plan.mkdirs + diff.new.name.parentPaths(),
                    update = plan.update + diff.new,
                    conflicts = plan.conflicts + checkOverrideChangeConflicts(index, diff)
                )
        }

    private fun checkOverrideAdditionConflicts(index: Index.Loaded, diff: TreeDiffMapValue.Addition): Conflicts {
        val stat = repository.workspace.statFileOrNull(diff.path)

        // some deeper file will be added, but there's an untracked file that would be a parent dir
        if (stat == null) {
            val untrackedParent = diff.path.parentPaths().any {
                val parentStat = repository.workspace.statFileOrNull(it)
                parentStat?.file == true && inspector.trackableFile(index, it, parentStat)
            }
            if (untrackedParent) {
                return Conflicts(untrackedOverwritten = listOf(diff.path))
            }
        }

        return checkWorkspaceConflicts(index, diff.path, stat)
    }

    private fun checkOverrideDeletionConflicts(index: Index.Loaded, diff: TreeDiffMapValue.Deletion): Conflicts {
        val stat = repository.workspace.statFileOrNull(diff.path)
        return checkWorkspaceConflicts(index, diff.path, stat)
    }

    private fun checkOverrideChangeConflicts(index: Index.Loaded, diff: TreeDiffMapValue.Change): Conflicts {
        val entry = repository.index.load()[diff.path.toString()]

        if (entry != null &&
            inspector.compareTreeToIndex(diff.old, entry) != null &&
            inspector.compareTreeToIndex(diff.new, entry) != null
        ) {
            return Conflicts(staleFile = listOf(diff.path))
        }

        val stat = repository.workspace.statFile(diff.path)
        return checkWorkspaceConflicts(index, diff.path, stat)
    }

    private fun checkWorkspaceConflicts(index: Index.Loaded, path: Path, stat: FileStat?): Conflicts {
        return when {
            stat == null -> Conflicts.EMPTY
            // same filename already exists locally
            stat.file -> {
                val entry = repository.index.load()[path.toString()]
                when (inspector.compareIndexToWorkspace(entry, stat)) {
                    is Inspector.WorkspaceChange.Untracked -> Conflicts(untrackedOverwritten = listOf(path))
                    is Inspector.WorkspaceChange.Modified -> Conflicts(staleFile = listOf(path))
                    null -> Conflicts.EMPTY
                }
            }
            // same path exists locally as a directory
            inspector.trackableFile(index, path, stat) -> Conflicts(staleDirectory = listOf(path))
            else -> Conflicts.EMPTY
        }
    }

    private fun collectErrors(conflicts: Conflicts): List<String> {
        fun error(header: String, footer: String, paths: Collection<Path>): List<String> =
            if (paths.isEmpty()) {
                emptyList()
            } else {
                val lines = paths.toSortedSet().map { "\t$it" }
                listOf((listOf(header) + lines + footer).joinToString(separator = "\n"))
            }

        return error(
            paths = conflicts.staleFile,
            header = "Your local changes to the following files would be overwritten by checkout:",
            footer = "Please commit your changes or stash them before you switch branches."
        ) +
            error(
                paths = conflicts.staleDirectory,
                header = "Updating the following directories would lose untracked files in them:",
                footer = ""
            ) +
            error(
                paths = conflicts.untrackedOverwritten,
                header = "The following untracked working tree files would be overwritten by checkout:",
                footer = "Please move or remove them before you switch branches."
            ) +
            error(
                paths = conflicts.untrackedRemoved,
                header = "The following untracked working tree files would be removed by checkout:",
                footer = "Please move or remove them before you switch branches."
            )
    }
}

data class MigrationPlan(
    val mkdirs: Set<Path> = emptySet(),
    val rmdirs: Set<Path> = emptySet(),
    val create: List<TreeEntry> = emptyList(),
    val delete: List<TreeEntry> = emptyList(),
    val update: List<TreeEntry> = emptyList(),
    val conflicts: Conflicts = Conflicts.EMPTY,
    val errors: List<String> = emptyList()
) {

    data class Conflicts(
        val staleFile: List<Path> = emptyList(),
        val staleDirectory: List<Path> = emptyList(),
        val untrackedOverwritten: List<Path> = emptyList(),
        // TODO when do you get this?
        val untrackedRemoved: List<Path> = emptyList()
    ) {
        operator fun plus(it: Conflicts) = copy(
            staleFile = staleFile + it.staleFile,
            staleDirectory = staleDirectory + it.staleDirectory,
            untrackedOverwritten = untrackedOverwritten + it.untrackedOverwritten,
            untrackedRemoved = untrackedRemoved + it.untrackedRemoved
        )

        companion object {
            internal val EMPTY = Conflicts()
        }
    }
}
class MigrationResult(val errors: List<String>)
