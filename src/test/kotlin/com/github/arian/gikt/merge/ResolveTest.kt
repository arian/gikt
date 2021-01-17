package com.github.arian.gikt.merge

import com.github.arian.gikt.database.Author
import com.github.arian.gikt.database.Blob
import com.github.arian.gikt.database.Commit
import com.github.arian.gikt.database.Entry
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.database.Tree
import com.github.arian.gikt.delete
import com.github.arian.gikt.deleteRecursively
import com.github.arian.gikt.exists
import com.github.arian.gikt.index.Index
import com.github.arian.gikt.listFiles
import com.github.arian.gikt.mkdirp
import com.github.arian.gikt.relativeTo
import com.github.arian.gikt.repository.Repository
import com.github.arian.gikt.utf8
import com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
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
        rootPath.listFiles().filterNot { it.fileName.toString() == ".git" }.forEach { it.deleteRecursively() }
        repository.resolvePath(".git/index").takeIf { it.exists() }?.delete()
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

    private fun run(repository: Repository, left: String, right: String): Pair<Index.Loaded, List<String>> {
        val logs = mutableListOf<String>()
        val resolve = Resolve(
            repository,
            Inputs(repository, left, right),
            logs::add,
        )

        repository.index.loadForUpdate {
            resolve.execute(this)
            writeUpdates()
        }

        return repository.index.load() to logs
    }

    @Test
    fun `merge branches successfully`() {
        val repository = repository()
        val commitA = commit(repository, emptyList(), "a.txt" to "one")
        val commitB = commit(repository, listOf(commitA.oid), "a.txt" to "two")
        val commitC = commit(repository, listOf(commitA.oid), "a.txt" to "one", "b.txt" to "three")

        run(repository, commitC.oid.short, commitB.oid.short)

        assertEquals("two", repository.workspace.readFile("a.txt").utf8())
        assertEquals("three", repository.workspace.readFile("b.txt").utf8())
    }

    @Test
    fun `merge branches both changed same file`() {
        val repository = repository()
        val commitA = commit(repository, emptyList(), "a.txt" to "one")
        val commitB = commit(repository, listOf(commitA.oid), "a.txt" to "two")
        val commitC = commit(repository, listOf(commitA.oid), "a.txt" to "three")

        val (index, logs) = run(repository, commitC.oid.short, commitB.oid.short)

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

        assertTrue(index.hasConflicts())
        assertEquals(
            listOf("a.txt" to 1, "a.txt" to 2, "a.txt" to 3),
            index.toList().map { it.name to it.stage.toInt() }
        )

        assertEquals(
            listOf(
                "Auto-merging a.txt",
                "CONFLICT (content): Merge conflict in a.txt",
            ),
            logs
        )
    }

    @Test
    fun `merge branches deleted and changed same file`() {
        val repository = repository()
        val commitA = commit(repository, emptyList(), "a.txt" to "one")
        val commitB = commit(repository, listOf(commitA.oid), "a.txt" to "two")
        val commitC = commit(repository, listOf(commitA.oid))

        val (index, logs) = run(repository, commitC.oid.short, commitB.oid.short)

        assertTrue(index.hasConflicts())
        assertEquals(
            listOf("a.txt" to 1, "a.txt" to 3),
            index.toList().map { it.name to it.stage.toInt() }
        )

        assertEquals(
            listOf(
                "CONFLICT (modify/delete): a.txt deleted in ${commitC.oid.short} " +
                    "and modified in ${commitB.oid.short}. " +
                    "Version ${commitB.oid.short} of a.txt left in tree.",
            ),
            logs
        )
    }

    @Test
    fun `merge branches changed and deleted same file`() {
        val repository = repository()
        val commitA = commit(repository, emptyList(), "a.txt" to "one")
        val commitB = commit(repository, listOf(commitA.oid))
        val commitC = commit(repository, listOf(commitA.oid), "a.txt" to "two")

        val (index, logs) = run(repository, commitC.oid.short, commitB.oid.short)

        assertTrue(index.hasConflicts())
        assertEquals(
            listOf("a.txt" to 1, "a.txt" to 2),
            index.toList().map { it.name to it.stage.toInt() }
        )

        assertEquals(
            listOf(
                "CONFLICT (modify/delete): a.txt deleted in ${commitB.oid.short} " +
                    "and modified in ${commitC.oid.short}. " +
                    "Version ${commitC.oid.short} of a.txt left in tree.",
            ),
            logs
        )
    }

    @Test
    fun `merge branches both deleted same file without conflicts`() {
        val repository = repository()
        val commitA = commit(repository, emptyList(), "a.txt" to "one", "b.txt" to "two")
        val commitB = commit(repository, listOf(commitA.oid), "a.txt" to "one")
        val commitC = commit(repository, listOf(commitA.oid), "a.txt" to "one")

        val (index, logs) = run(repository, commitC.oid.short, commitB.oid.short)

        assertFalse(index.hasConflicts())
        assertEquals(listOf("a.txt"), index.toList().map { it.name })
        assertEquals(emptyList<String>(), logs)
    }

    @Test
    fun `merge branches both added the same file with different content`() {
        val repository = repository()
        val commitA = commit(repository, emptyList(), "a.txt" to "one")
        val commitB = commit(repository, listOf(commitA.oid), "a.txt" to "one", "b.txt" to "b")
        val commitC = commit(repository, listOf(commitA.oid), "a.txt" to "one", "b.txt" to "B")

        val (index, logs) = run(repository, commitC.oid.short, commitB.oid.short)

        assertTrue(index.hasConflicts())
        assertEquals(
            listOf(
                "a.txt" to 0,
                "b.txt" to 2,
                "b.txt" to 3,
            ),
            index.toList().map { it.name to it.stage.toInt() }
        )

        assertEquals(
            listOf(
                "Auto-merging b.txt",
                "CONFLICT (add/add): Merge conflict in b.txt",
            ),
            logs
        )
    }

    @Nested
    inner class MergeBranchesFileDirConflict {

        @Test
        fun `changed file on the left and created directory on the right`() {
            val repository = repository()
            val commitA = commit(repository, emptyList(), "a.txt" to "one")
            val commitB = commit(repository, listOf(commitA.oid), "a.txt/b.txt" to "two")
            val commitC = commit(repository, listOf(commitA.oid), "a.txt" to "three")

            val (index, logs) = run(repository, commitC.oid.short, commitB.oid.short)

            assertTrue(index.hasConflicts())
            assertEquals(
                listOf(
                    "a.txt" to 1,
                    "a.txt" to 2,
                    "a.txt/b.txt" to 0
                ),
                index.toList().map { it.name to it.stage.toInt() }
            )

            assertEquals(
                listOf(
                    "Adding a.txt/b.txt",
                    "CONFLICT (modify/delete): a.txt deleted in ${commitB.oid.short} " +
                        "and modified in ${commitC.oid.short}. " +
                        "Version ${commitC.oid.short} of a.txt left in tree at a.txt~${commitC.oid.short}.",
                ),
                logs
            )
        }

        @Test
        fun `created directory on the left and changed file on the right`() {
            val repository = repository()
            val commitA = commit(repository, emptyList(), "a.txt" to "one")
            val commitB = commit(repository, listOf(commitA.oid), "a.txt" to "three")
            val commitC = commit(repository, listOf(commitA.oid), "a.txt/b.txt" to "two")

            val (index, logs) = run(repository, commitC.oid.short, commitB.oid.short)

            assertTrue(index.hasConflicts())
            assertEquals(
                listOf(
                    "a.txt" to 1,
                    "a.txt" to 3,
                    "a.txt/b.txt" to 0
                ),
                index.toList().map { it.name to it.stage.toInt() }
            )

            assertEquals(
                listOf(
                    "Adding a.txt/b.txt",
                    "CONFLICT (modify/delete): a.txt deleted in ${commitC.oid.short} " +
                        "and modified in ${commitB.oid.short}. " +
                        "Version ${commitB.oid.short} of a.txt left in tree at a.txt~${commitB.oid.short}.",
                ),
                logs
            )
        }

        @Test
        fun `created file left and created directory right`() {
            val repository = repository()
            val commitA = commit(repository, emptyList(), "a.txt" to "one")
            val commitB = commit(repository, listOf(commitA.oid), "b" to "three")
            val commitC = commit(repository, listOf(commitA.oid), "b/c.txt" to "two")

            val (index, logs) = run(repository, commitB.oid.short, commitC.oid.short)

            assertTrue(index.hasConflicts())
            assertEquals(
                listOf(
                    "b" to 2,
                    "b/c.txt" to 0
                ),
                index.toList().map { it.name to it.stage.toInt() }
            )

            assertEquals(
                listOf(
                    "Adding b/c.txt",
                    "CONFLICT (file/directory): There is a directory with name b in ${commitC.oid.short}. " +
                        "Adding b as b~${commitB.oid.short}"
                ),
                logs
            )
        }

        @Test
        fun `created directory left and created file right`() {
            val repository = repository()
            val commitA = commit(repository, emptyList(), "a.txt" to "one")
            val commitB = commit(repository, listOf(commitA.oid), "b" to "three")
            val commitC = commit(repository, listOf(commitA.oid), "b/c.txt" to "two")

            val (index, logs) = run(repository, commitC.oid.short, commitB.oid.short)

            assertTrue(index.hasConflicts())
            assertEquals(
                listOf(
                    "b" to 3,
                    "b/c.txt" to 0
                ),
                index.toList().map { it.name to it.stage.toInt() }
            )

            assertEquals(
                listOf(
                    "Adding b/c.txt",
                    "CONFLICT (directory/file): There is a directory with name b in ${commitC.oid.short}. " +
                        "Adding b as b~${commitB.oid.short}"
                ),
                logs
            )
        }
    }
}
