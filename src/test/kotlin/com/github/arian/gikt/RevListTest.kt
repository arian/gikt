package com.github.arian.gikt

import com.github.arian.gikt.database.Author
import com.github.arian.gikt.database.Commit
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.database.toHexString
import com.github.arian.gikt.repository.Repository
import com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

internal class RevListTest {

    private fun repository(): Repository {
        val fs = MemoryFileSystemBuilder.newLinux().build()
        val ws = fs.getPath("gitk-objects").mkdirp()
        return Repository(ws)
    }

    private fun commit(
        repo: Repository,
        parent: ObjectId? = null,
        timeOffset: Long = 0L,
        tree: String? = null
    ): Commit {
        val zoneId = ZoneId.of("Europe/Amsterdam")

        val time = ZonedDateTime
            .now(Clock.fixed(Instant.parse("2019-08-14T10:08:22.00Z"), zoneId))
            .plusSeconds(timeOffset)

        val commit = Commit(
            parent = parent,
            message = "commit".toByteArray(),
            author = Author("arian", "arian@example.com", time),
            tree = ObjectId(
                tree?.toByteArray()?.sha1()?.toHexString()
                    ?: "abc12def12def12def12def12def12def12def12"
            )
        )
        repo.database.store(commit)
        return commit
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
}
