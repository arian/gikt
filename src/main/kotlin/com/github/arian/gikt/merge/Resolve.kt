package com.github.arian.gikt.merge

import com.github.arian.gikt.Mode
import com.github.arian.gikt.database.Blob
import com.github.arian.gikt.database.Entry
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.database.TreeDiffMap
import com.github.arian.gikt.database.TreeDiffMapValue
import com.github.arian.gikt.database.TreeEntry
import com.github.arian.gikt.index.Index
import com.github.arian.gikt.repository.Repository
import java.nio.file.Path

typealias Conflicts = Map<Path, Resolve.Conflict<TreeEntry>>

class Resolve(
    private val repository: Repository,
    private val inputs: Inputs,
) {

    fun execute(index: Index.Updater) {
        val (cleanDiffs, conflicts) = prepareTreeDiffs()

        val migration = repository.migration(cleanDiffs)
        migration.applyChanges(index)

        addConflictsToIndex(index, conflicts)
    }

    private fun addConflictsToIndex(index: Index.Updater, conflicts: Conflicts) {
        conflicts.forEach { (path, items) ->
            index.addConflictSet(path, items.toList())
        }
    }

    private fun prepareTreeDiffs(): Pair<TreeDiffMap, Conflicts> {
        val baseOid = inputs.baseOids.firstOrNull()
        val leftDiff = repository.database.treeDiff(baseOid, inputs.leftOid)
        val rightDiff = repository.database.treeDiff(baseOid, inputs.rightOid)

        val (diffs, allConflicts) = rightDiff.values
            .mapNotNull { diff -> samePathConflict(leftDiff, diff) }
            .unzip()

        val cleanDiffs: TreeDiffMap = diffs.associateBy { it.path }
        val conflicts: Conflicts = allConflicts.filterNotNull().associateBy { it.path }
        return Pair(cleanDiffs, conflicts)
    }

    private fun samePathConflict(
        leftDiffMap: TreeDiffMap,
        rightDiff: TreeDiffMapValue,
    ): Pair<TreeDiffMapValue, Conflict<TreeEntry>?>? {
        val path = rightDiff.path
        val leftDiff = leftDiffMap[path] ?: return rightDiff to null

        val conflict = when (rightDiff) {
            is TreeDiffMapValue.Addition -> when (leftDiff) {
                is TreeDiffMapValue.Addition -> Conflict.Both(path, null, leftDiff.new, rightDiff.new)
                is TreeDiffMapValue.Change -> error("incompatible diffs from base")
                is TreeDiffMapValue.Deletion -> error("incompatible diffs from base")
            }
            is TreeDiffMapValue.Change -> when (leftDiff) {
                is TreeDiffMapValue.Addition -> error("incompatible diffs from base")
                is TreeDiffMapValue.Change -> Conflict.Both(path, leftDiff.old, leftDiff.new, rightDiff.new)
                is TreeDiffMapValue.Deletion -> Conflict.Right(path, leftDiff.old, rightDiff.new)
            }
            is TreeDiffMapValue.Deletion -> when (leftDiff) {
                is TreeDiffMapValue.Addition -> error("incompatible diffs from base")
                is TreeDiffMapValue.Change -> Conflict.Left(path, leftDiff.old, leftDiff.new)
                is TreeDiffMapValue.Deletion -> return null
            }
        }

        if (conflict.sidesAreTheSame()) {
            return null
        }

        val mergeBlobsResult = mergeBlobs(conflict.map { it.oid })
        val mergeModesResult = mergeModes(conflict.map { it.mode })

        val entry = Entry(rightDiff.path, mergeModesResult.value, mergeBlobsResult.value)

        val diff = when (conflict) {
            is Conflict.Left -> TreeDiffMapValue.Change(conflict.left, entry)
            is Conflict.Right -> TreeDiffMapValue.Addition(entry)
            is Conflict.Both -> TreeDiffMapValue.Change(conflict.left, entry)
        }

        if (mergeBlobsResult.isOk && mergeModesResult.isOk) {
            return diff to null
        }

        return diff to conflict
    }

    private fun <T> merge3(
        conflict: Conflict<T>,
        orElse: (Conflict.Both<T>) -> MergeResult<T>
    ): MergeResult<T> {
        return when (conflict) {
            is Conflict.Left -> MergeResult(isOk = false, value = conflict.left)
            is Conflict.Right -> MergeResult(isOk = false, value = conflict.right)
            is Conflict.Both -> {
                if (conflict.left == conflict.base || conflict.left == conflict.right) {
                    MergeResult(isOk = true, value = conflict.right)
                } else if (conflict.right == conflict.base) {
                    MergeResult(isOk = true, value = conflict.left)
                } else {
                    orElse(conflict)
                }
            }
        }
    }

    private fun mergeBlobs(conflict: Conflict<ObjectId>): MergeResult<ObjectId> {
        return merge3(
            conflict,
            orElse = {
                val blob = Blob(mergedData(it.left, it.right))
                repository.database.store(blob)
                MergeResult(isOk = false, blob.oid)
            }
        )
    }

    private fun mergedData(leftOid: ObjectId, rightOid: ObjectId): ByteArray {
        val leftData = (repository.loadObject(leftOid) as? Blob)?.data ?: ByteArray(0)
        val rightData = (repository.loadObject(rightOid) as? Blob)?.data ?: ByteArray(0)

        return "<<<<<<< ${inputs.leftName}\n".toByteArray() +
            leftData + newlineForBlob(leftData) +
            "=======\n".toByteArray() +
            rightData + newlineForBlob(rightData) +
            ">>>>>>> ${inputs.rightName}\n".toByteArray()
    }

    private fun newlineForBlob(leftData: ByteArray): ByteArray {
        return if (leftData.last().toChar() == '\n') {
            ByteArray(0)
        } else {
            "\n".toByteArray()
        }
    }

    private fun mergeModes(conflict: Conflict<Mode>): MergeResult<Mode> {
        return merge3(conflict, orElse = { MergeResult(false, it.left) })
    }

    data class MergeResult<T>(
        val isOk: Boolean,
        val value: T,
    )

    sealed class Conflict<T> {
        abstract val path: Path
        abstract val base: T?
        abstract fun toList(): List<T?>

        data class Left<T>(override val path: Path, override val base: T?, val left: T) : Conflict<T>() {
            override fun toList(): List<T?> = listOf(base, left, null)
        }

        data class Right<T>(override val path: Path, override val base: T?, val right: T) : Conflict<T>() {
            override fun toList(): List<T?> = listOf(base, null, right)
        }

        data class Both<T>(
            override val path: Path,
            override val base: T?,
            val left: T,
            val right: T
        ) : Conflict<T>() {
            override fun toList(): List<T?> = listOf(base, left, right)
        }
    }

    private fun Conflict<TreeEntry>.sidesAreTheSame(): Boolean = when (this) {
        is Conflict.Left -> false
        is Conflict.Right -> false
        is Conflict.Both -> left.name == right.name && left.oid == right.oid && left.mode == right.mode
    }

    private fun <T, R> Conflict<T>.map(mapper: (T) -> R): Conflict<R> = when (this) {
        is Conflict.Left -> Conflict.Left(path, base?.let(mapper), mapper(left))
        is Conflict.Right -> Conflict.Right(path, base?.let(mapper), mapper(right))
        is Conflict.Both -> Conflict.Both(path, base?.let(mapper), mapper(left), mapper(right))
    }
}
