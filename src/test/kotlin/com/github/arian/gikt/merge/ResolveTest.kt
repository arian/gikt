package com.github.arian.gikt.merge

import com.github.arian.gikt.database.Author
import com.github.arian.gikt.database.Blob
import com.github.arian.gikt.database.Commit
import com.github.arian.gikt.database.Entry
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.database.Tree
import com.github.arian.gikt.mkdirp
import com.github.arian.gikt.relativeTo
import com.github.arian.gikt.repository.Repository
import com.github.arian.gikt.utf8
import com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

internal class ResolveTest {

    private fun repository(): Repository {
        val fs = MemoryFileSystemBuilder.newLinux().build()
        val ws = fs.getPath("/gitk-objects").mkdirp()
        ws.resolve(".git").mkdirp()
        return Repository(ws)
    }

    private fun commit(
        repo: Repository,
        parents: List<ObjectId>,
        vararg files: Pair<String, String>
    ): Commit {
        val tree = treeWithFiles(repo, *files)
        val zoneId = ZoneId.of("Europe/Amsterdam")
        val time = ZonedDateTime.now(Clock.fixed(Instant.parse("2019-08-14T10:08:22.00Z"), zoneId))
        return Commit(
            parents = parents,
            message = "commit".toByteArray(),
            author = Author("arian", "arian@example.com", time),
            tree = tree.oid
        ).also {
            repo.database.store(it)
        }
    }

    private fun treeWithFiles(repository: Repository, vararg files: Pair<String, String>): Tree {
        val rootPath = repository.resolvePath("")
        val entries = repository.index.loadForUpdate {
            files
                .map { (name, content) ->
                    val path = repository.resolvePath(name).relativeTo(rootPath)
                    val bytes = content.toByteArray()
                    repository.workspace.writeFile(path, bytes)
                    val stat = repository.workspace.statFile(path)
                    val blob = Blob(bytes)
                    add(path, blob.oid, stat)
                    repository.database.store(blob)
                    Entry(path, stat, blob.oid)
                }
                .also { writeUpdates() }
        }
        return Tree.build(rootPath, entries)
            .apply { traverse { repository.database.store(it) } }
            .also { repository.database.store(it) }
    }

    @Test
    fun `merge branches successfully`() {
        val repository = repository()
        val commitA = commit(repository, emptyList(), "a.txt" to "one")
        val commitB = commit(repository, listOf(commitA.oid), "a.txt" to "two")
        val commitC = commit(repository, listOf(commitA.oid), "a.txt" to "one", "b.txt" to "three")

        val resolve = Resolve(repository, Inputs(repository, commitC.oid.short, commitB.oid.short))

        repository.index.loadForUpdate {
            resolve.execute(this)
            writeUpdates()
        }

        assertEquals("two", repository.workspace.readFile("a.txt").utf8())
        assertEquals("three", repository.workspace.readFile("b.txt").utf8())
    }

    @Test
    fun `merge branches both changed same file`() {
        val repository = repository()
        val commitA = commit(repository, emptyList(), "a.txt" to "one")
        val commitB = commit(repository, listOf(commitA.oid), "a.txt" to "two")
        val commitC = commit(repository, listOf(commitA.oid), "a.txt" to "three")

        val resolve = Resolve(repository, Inputs(repository, commitC.oid.short, commitB.oid.short))

        repository.index.loadForUpdate {
            resolve.execute(this)
            writeUpdates()
        }

        assertEquals(
            """
            |<<<<<<< 31f0805
            |three
            |=======
            |two
            |>>>>>>> 6fdcee8
            |
            """.trimMargin(),
            repository.workspace.readFile("a.txt").utf8()
        )
    }
}
