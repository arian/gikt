package com.github.arian.gikt

import com.github.arian.gikt.database.Author
import com.github.arian.gikt.database.Blob
import com.github.arian.gikt.database.Commit
import com.github.arian.gikt.database.Entry
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.database.Tree
import com.github.arian.gikt.database.toHexString
import com.github.arian.gikt.repository.Repository
import com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

internal class RevListTest {

    private val treeOid = ObjectId("abc12def12def12def12def12def12def12def12")

    private fun repository(): Repository {
        val fs = MemoryFileSystemBuilder.newLinux().build()
        val ws = fs.getPath("/gitk-objects").mkdirp()
        return Repository(ws)
    }

    private fun commit(
        repo: Repository,
        parents: List<ObjectId> = emptyList(),
        timeOffset: Long = 0L,
        tree: ObjectId = treeOid
    ): Commit {

        val zoneId = ZoneId.of("Europe/Amsterdam")

        val time = ZonedDateTime
            .now(Clock.fixed(Instant.parse("2019-08-14T10:08:22.00Z"), zoneId))
            .plusSeconds(timeOffset)

        val commit = Commit(
            parents = parents,
            message = "commit".toByteArray(),
            author = Author("arian", "arian@example.com", time),
            tree = tree
        )
        repo.database.store(commit)
        return commit
    }

    private fun commit(
        repo: Repository,
        parent: ObjectId?,
        timeOffset: Long = 0L,
        tree: ObjectId = treeOid
    ): Commit =
        commit(repo, listOfNotNull(parent), timeOffset, tree)

    private fun commit(
        repo: Repository,
        parent: ObjectId? = null,
        timeOffset: Long = 0L,
        tree: String
    ): Commit {
        val oid = tree.toByteArray().sha1().toHexString().let { ObjectId(it) }
        return commit(repo, listOfNotNull(parent), timeOffset, oid)
    }

    private fun assertRevList(expected: List<ObjectId>, actual: List<Commit>) =
        assertEquals(expected.map { it.short }, actual.map { it.oid.short })

    @Test
    fun `list linear commits`() {
        val repository = repository()

        val commit1 = commit(repository).oid
        val commit2 = commit(repository, commit1).oid
        val commit3 = commit(repository, commit2).oid

        val revList = RevList(repository, RevList.parseStartPoints(repository, listOf(commit3.hex)))

        val log = revList.commits().toList()

        assertRevList(
            listOf(
                commit3,
                commit2,
                commit1
            ),
            log
        )
    }

    @Test
    fun `multiple start refs`() {
        val repository = repository()

        val commit1 = commit(repository).oid
        val commit2 = commit(repository, commit1).oid
        val commit3 = commit(repository, commit2).oid

        val revList = RevList(
            repository,
            RevList.parseStartPoints(repository, listOf(commit2.hex, commit3.hex))
        )

        val log = revList.commits().toList()

        assertRevList(
            listOf(
                commit2,
                commit3,
                commit1
            ),
            log
        )
    }

    @Test
    fun `branched commits`() {
        val repository = repository()

        val commit1 = commit(repository).oid
        val commit2 = commit(repository, commit1, timeOffset = 1).oid
        val commit3 = commit(repository, commit1, timeOffset = 2).oid

        val revList = RevList(
            repository,
            RevList.parseStartPoints(repository, listOf(commit2.hex, commit3.hex))
        )

        val log = revList.commits().toList()

        assertRevList(
            listOf(
                commit3,
                commit2,
                commit1
            ),
            log
        )
    }

    @Test
    fun `branched commits sorted by time`() {
        val repository = repository()

        val commit1 = commit(repository).oid
        val commit2 = commit(repository, commit1, timeOffset = 2).oid
        val commit3 = commit(repository, commit1, timeOffset = 1).oid

        val revList = RevList(
            repository,
            RevList.parseStartPoints(repository, listOf(commit2.hex, commit3.hex))
        )

        val log = revList.commits().toList()

        assertRevList(
            listOf(
                commit2,
                commit3,
                commit1
            ),
            log
        )
    }

    @Test
    fun `commits from HEAD`() {
        val repository = repository()
        val commit1 = commit(repository).oid
        repository.refs.updateHead(commit1)
        val revList = RevList(repository, emptyList())
        val log = revList.commits().toList()
        assertRevList(listOf(commit1), log)
    }

    @Test
    fun `exclude uninteresting commits`() {
        val repository = repository()
        val commit1 = commit(repository).oid
        val commit2 = commit(repository, commit1).oid
        val revList = RevList(
            repository,
            RevList.parseStartPoints(repository, listOf("${commit1.short}..${commit2.short}"))
        )
        val log = revList.commits().toList()
        assertRevList(listOf(commit2), log)
    }

    private inner class BranchedRepo(
        val repository: Repository = repository(),
        val commitA: ObjectId = commit(repository).oid,
        val commitB: ObjectId = commit(repository, commitA, timeOffset = 1).oid,
        val commitC: ObjectId = commit(repository, commitB, timeOffset = 3).oid,
        val commitD: ObjectId = commit(repository, commitC, timeOffset = 5).oid,
        val commitE: ObjectId = commit(repository, commitB, timeOffset = 2).oid,
        val commitF: ObjectId = commit(repository, commitE, timeOffset = 4).oid,
        val commitG: ObjectId = commit(repository, commitF, timeOffset = 6).oid
    ) {
        val main = listOf(commitD, commitC)
        val branch = listOf(commitG, commitF, commitE)

        fun revList(start: String) =
            RevList(repository, start(start))

        fun start(rev: String) =
            RevList.parseStartPoints(repository, listOf(rev))
    }

    @Test
    fun `show commits in other branch`() {
        val repo = BranchedRepo()
        repo.repository.refs.updateHead(repo.commitD)
        val log = repo.revList("${repo.commitG}..").commits().toList()
        assertRevList(repo.main, log)
    }

    @Test
    fun `show commits in this branch`() {
        val repo = BranchedRepo()
        repo.repository.refs.updateHead(repo.commitD)
        val log = repo.revList("..${repo.commitG}").commits().toList()
        assertRevList(repo.branch, log)
    }

    @Test
    fun `show commits in this branch with specific other starting point`() {
        val repo = BranchedRepo()
        repo.repository.refs.updateHead(repo.commitD)
        val log = repo.revList("${repo.commitC}..${repo.commitG}").commits().toList()
        assertRevList(repo.branch, log)
    }

    @Test
    fun `show commits in this branch parsed as revision`() {
        val repo = BranchedRepo()
        repo.repository.refs.updateHead(repo.commitD)
        val log = repo.revList("${repo.commitD}^..${repo.commitG}").commits().toList()
        assertRevList(repo.branch, log)
    }

    @Test
    fun `show commits in other branch using the caret notation`() {
        val repo = BranchedRepo()
        repo.repository.refs.updateHead(repo.commitD)
        val log = repo.revList("^${repo.commitG.short}").commits().toList()
        assertRevList(repo.main, log)
    }

    @Test
    fun `show commits in other branch with same timestamps`() {
        val repository = repository()

        val commitA = commit(repository).oid
        val commitB = commit(repository, commitA).oid

        val commitC = commit(repository, commitB, tree = "C").oid
        val commitD = commit(repository, commitC, tree = "D").oid

        val commitE = commit(repository, commitB, tree = "E").oid
        val commitF = commit(repository, commitE, tree = "F").oid
        val commitG = commit(repository, commitF, tree = "G").oid
        val commitH = commit(repository, commitG, tree = "H").oid
        val commitJ = commit(repository, commitH, tree = "J").oid
        val commitK = commit(repository, commitJ, tree = "K").oid

        val revList = RevList(
            repository,
            RevList.parseStartPoints(repository, listOf("${commitK.short}..${commitD.short}"))
        )
        val log = revList.commits().toList()
        assertRevList(listOf(commitD, commitC), log)
    }

    /**
     * ```
     * A   B   C   M
     * o<--o<--o<--o
     *  \         /
     *   o<--o<--+
     *   D   E
     * ```
     */
    private inner class RepoWithCommits(val repository: Repository = repository()) {

        val treeA = treeWithFiles(repository, "a.txt" to "", "foo/bar/b.txt" to "b")
        val commitA = commit(repository, tree = treeA.oid).oid

        val treeB = treeWithFiles(repository, "a.txt" to "b", "foo/bar/b.txt" to "b")
        val commitB = commit(repository, parent = commitA, tree = treeB.oid).oid

        val treeC = treeWithFiles(repository, "a.txt" to "b", "foo/bar/b.txt" to "c")
        val commitC = commit(repository, parent = commitB, tree = treeC.oid).oid

        val treeD = treeWithFiles(repository, "a.txt" to "", "d.txt" to "d")
        val commitD = commit(repository, parent = commitA, tree = treeD.oid).oid

        val treeE = treeWithFiles(repository, "a.txt" to "", "d.txt" to "e")
        val commitE = commit(repository, parent = commitD, tree = treeE.oid).oid

        val treeM = treeWithFiles(
            repository,
            "a.txt" to "b",
            "foo/bar/b.txt" to "c",
            "d.txt" to "e",
        )
        val commitM = commit(repository, parents = listOf(commitC, commitE), tree = treeM.oid).oid

        init {
            repository.resolvePath("a.txt").write("")
            repository.resolvePath("d.txt").write("")
            repository.resolvePath("foo/bar/b.txt").run {
                parent.mkdirp()
                write("")
            }
            repository.refs.updateHead(commitC)
        }

        private fun treeWithFiles(repository: Repository, vararg files: Pair<String, String>): Tree {
            val rootPath = repository.resolvePath("")
            val entries = files.map { (name, content) ->
                val blob = Blob(content.toByteArray())
                repository.database.store(blob)
                Entry(repository.resolvePath(name).relativeTo(rootPath), FileStat(), blob.oid)
            }

            val tree = Tree.build(rootPath, entries)
            tree.traverse { repository.database.store(it) }
            repository.database.store(tree)

            return tree
        }
    }

    @Nested
    inner class FilterPaths {

        @Test
        fun `filter commits with a path`() {
            val repo = RepoWithCommits()

            val revList = RevList(
                repo.repository,
                RevList.parseStartPoints(
                    repo.repository,
                    listOf("a.txt", repo.commitC.hex)
                )
            )

            val log = revList.commits().toList()

            assertRevList(listOf(repo.commitB, repo.commitA), log)
        }

        @Test
        fun `filter commits with a directory name that matches everything in the path`() {
            val repo = RepoWithCommits()

            val revList = RevList(
                repo.repository,
                RevList.parseStartPoints(
                    repo.repository,
                    listOf("foo", repo.commitC.hex)
                )
            )

            val log = revList.commits().toList()

            assertRevList(listOf(repo.commitC, repo.commitA), log)
        }

        @Test
        fun `filter commits from HEAD`() {
            val repo = RepoWithCommits()
            val revList = RevList(repo.repository, RevList.parseStartPoints(repo.repository, listOf("a.txt")))
            val log = revList.commits().toList()
            assertRevList(listOf(repo.commitB, repo.commitA), log)
        }

        @Test
        fun `filter commits with relative root path from HEAD`() {
            val repo = RepoWithCommits()
            val revList = RevList(repo.repository, RevList.parseStartPoints(repo.repository, listOf(".")))
            val log = revList.commits().toList()
            assertRevList(listOf(repo.commitC, repo.commitB, repo.commitA), log)
        }

        @Test
        fun `filter commits with absolute root path from HEAD`() {
            val repo = RepoWithCommits()
            val rootPath = repo.repository.resolvePath("")
            val revList = RevList(
                repo.repository,
                RevList.parseStartPoints(repo.repository, listOf(rootPath.toString()))
            )
            val log = revList.commits().toList()
            assertRevList(listOf(repo.commitC, repo.commitB, repo.commitA), log)
        }
    }

    @Nested
    inner class MergedRepo {

        /**
         * ```
         * A   B      C        D
         * o<--o<-----o<-------o
         *  \   \               \
         *   \   o<--o<--o<--o<--o<--o
         *    \  E   D   F   G   H   J
         *     o<--o
         *     K   L
         * ```
         */
        private inner class Repo(
            val repository: Repository = repository(),
            val commitA: ObjectId = commit(repository).oid,
            val commitB: ObjectId = commit(repository, commitA, timeOffset = 1).oid,
            val commitC: ObjectId = commit(repository, commitB, timeOffset = 3).oid,
            val commitD: ObjectId = commit(repository, commitC, timeOffset = 5).oid,
            val commitE: ObjectId = commit(repository, commitB, timeOffset = 2).oid,
            val commitF: ObjectId = commit(repository, commitE, timeOffset = 4).oid,
            val commitG: ObjectId = commit(repository, commitF, timeOffset = 6).oid,
            val commitH: ObjectId = commit(repository, parents = listOf(commitD, commitG), timeOffset = 7).oid,
            val commitJ: ObjectId = commit(repository, commitH, timeOffset = 8).oid,
            val commitK: ObjectId = commit(repository, commitA, timeOffset = 9).oid,
            val commitL: ObjectId = commit(repository, commitK, timeOffset = 10).oid,
        ) {
            fun revList(start: String) =
                RevList(repository, start(start))

            fun start(rev: String) =
                RevList.parseStartPoints(repository, listOf(rev))
        }

        @Test
        fun `show log of merged branches by date descending`() {
            val testRepo = Repo()
            assertRevList(
                listOf(
                    testRepo.commitJ,
                    testRepo.commitH,
                    testRepo.commitG,
                    testRepo.commitD,
                    testRepo.commitF,
                    testRepo.commitC,
                    testRepo.commitE,
                    testRepo.commitB,
                    testRepo.commitA,
                ),
                testRepo.revList(testRepo.commitJ.hex).commits().toList()
            )
        }

        @Test
        fun `show log excluding all commits of branch with merge commits`() {
            val testRepo = Repo()
            assertRevList(
                listOf(
                    testRepo.commitL,
                    testRepo.commitK,
                ),
                testRepo.revList("${testRepo.commitJ.hex}..${testRepo.commitL}").commits().toList()
            )
        }

        @Test
        fun `prune treesame commits with file from first parent branch`() {
            val repo = RepoWithCommits()

            val revList = RevList(
                repo.repository,
                RevList.parseStartPoints(
                    repo.repository,
                    listOf("a.txt", repo.commitM.hex)
                )
            )

            val log = revList.commits().toList()

            assertRevList(listOf(repo.commitB, repo.commitA), log)
        }

        @Test
        fun `prune treesame commits with file from second parent branch`() {
            val repo = RepoWithCommits()

            val revList = RevList(
                repo.repository,
                RevList.parseStartPoints(
                    repo.repository,
                    listOf("d.txt", repo.commitM.hex)
                )
            )

            val log = revList.commits().toList()

            assertRevList(listOf(repo.commitE, repo.commitD), log)
        }
    }
}
