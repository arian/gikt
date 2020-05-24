package com.github.arian.gikt

import com.github.arian.gikt.database.TreeEntry
import com.github.arian.gikt.repository.Migration
import com.github.arian.gikt.repository.MigrationPlan
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.AccessMode
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.NoSuchFileException
import java.nio.file.NotDirectoryException
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class Workspace(private val rootPath: Path) {

    private val ignore = listOf(
        ".git",
        ".idea",
        "out",
        "gikt.iml",
        "gikt.jar",
        "gikt-test.jar",
        ".gradle",
        "build"
    )

    private val rootIgnores = ignores(rootPath)

    private fun ignores(path: Path) = ignore.map { path.resolve(it) }.toSet()

    fun listFiles(path: Path = rootPath): Set<Path> {
        return listFilesIntern(path.normalize())
    }

    private fun listFilesIntern(p: Path): Set<Path> {
        val ignore = ignores(p) + rootIgnores
        val relative = p.relativeTo(rootPath)
        return when {
            ignore.contains(p) ->
                emptySet()
            p.isDirectory() ->
                (p.listFiles().toSet() - ignore).flatMap { listFiles(it) }.toSet()
            p.exists() ->
                setOf(relative)
            else ->
                throw MissingFile("pathspec '$relative' did not match any files")
        }
    }

    fun listDir() = listDirIntern(rootPath)

    fun listDir(path: Path) = listDirIntern(absolutePath(path))

    private fun listDirIntern(path: Path): Map<Path, FileStat> {
        val ignore = ignores(path) + rootIgnores
        val entries = path.listFiles().toSet() - ignore

        return entries
            .map {
                val p = it.relativeTo(rootPath)
                p to statFile(p)
            }
            .toMap()
    }

    fun readFile(it: String): ByteArray = readFileChecked(absolutePath(it))
    fun readFile(it: Path): ByteArray = readFileChecked(absolutePath(it))

    private fun readFileChecked(it: Path): ByteArray = try {
        it.checkAccess(AccessMode.READ).readBytes()
    } catch (e: AccessDeniedException) {
        throw NoPermission("open('${it.relativeTo(rootPath)}'): Permission denied")
    }

    fun statFile(it: Path): FileStat = statFileChecked(absolutePath(it))

    private fun statFileChecked(it: Path): FileStat = try {
        it.checkAccess(AccessMode.READ).stat()
    } catch (e: AccessDeniedException) {
        throw NoPermission("stat('${it.relativeTo(rootPath)}'): Permission denied")
    }

    private fun absolutePath(it: Path): Path = rootPath.resolve(it)
    private fun absolutePath(it: String): Path = rootPath.resolve(it)

    fun applyMigration(migration: Migration, plan: MigrationPlan) {
        plan.delete.forEach { absolutePath(it.name).delete() }
        plan.rmdirs.sorted().reversed().forEach {
            removeDirectory(it)
        }

        plan.mkdirs.sorted().forEach {
            makeDirectory(it)
        }

        applyChangeList(migration, plan.update)
        applyChangeList(migration, plan.create)
    }

    private fun removeDirectory(dirname: Path) {
        try {
            absolutePath(dirname).delete()
        } catch (e: IOException) {
            when (e) {
                is DirectoryNotEmptyException -> {}
                is NotDirectoryException -> {}
                is NoSuchFileException -> {}
                else -> throw e
            }
        }
    }

    private fun makeDirectory(dirname: Path) {
        val path = absolutePath(dirname)
        val stat = try {
            statFile(dirname)
        } catch (e: NoSuchFileException) {
            null
        }

        if (stat?.file == true) {
            path.delete()
        }
        if (stat?.directory != true) {
            path.mkdirp()
        }
    }

    private fun applyChangeList(migration: Migration, plan: List<TreeEntry>) {
        plan.forEach { entry ->
            val path = absolutePath(entry.name)

            try {
                path.deleteRecursively()
            } catch (e: NoSuchFileException) {}

            path.write(
                migration.blobData(entry.oid),
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE_NEW
            )

            when (entry.mode) {
                Mode.REGULAR -> path.makeUnExecutable()
                Mode.EXECUTABLE -> path.makeExecutable()
                Mode.TREE -> throw IllegalStateException("shouldn't be a tree")
            }
        }
    }

    class MissingFile(msg: String) : Exception(msg)
    class NoPermission(msg: String) : Exception(msg)
}
