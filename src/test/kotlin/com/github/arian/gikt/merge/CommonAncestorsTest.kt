package com.github.arian.gikt.merge

import com.github.arian.gikt.database.Author
import com.github.arian.gikt.database.Commit
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.mkdirp
import com.github.arian.gikt.repository.Repository
import com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

internal class CommonAncestorsTest {

    private fun repository(): Repository {
        val fs = MemoryFileSystemBuilder.newLinux().build()
        val ws = fs.getPath("/gitk-objects").mkdirp()
        return Repository(ws)
    }

    private fun commit(
        repo: Repository,
        parents: List<ObjectId> = emptyList(),
        timeOffset: Long = 0L,
        tree: ObjectId = ObjectId("abc12def12def12def12def12def12def12def12")
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

    @Test
    fun `branched tree`() {
        val repository = repository()

        val commit1 = commit(repository).oid
        val commit2 = commit(repository, listOf(commit1), timeOffset = 1).oid
        val commit3 = commit(repository, listOf(commit1), timeOffset = 2).oid

        val commonAncestors = CommonAncestors(
            database = repository.database,
            one = commit2,
            two = commit3
        )

        val bca = commonAncestors.find()

        assertEquals(commit1, bca)
    }

    @Test
    fun `merged history with further commits`() {
        val repository = repository()

        val commitA = commit(repository).oid
        val commitB = commit(repository, listOf(commitA)).oid
        val commitC = commit(repository, listOf(commitA)).oid
        val commitD = commit(repository, listOf(commitB, commitC)).oid
        val commitE = commit(repository, listOf(commitD)).oid
        val commitF = commit(repository, listOf(commitC)).oid

        val commonAncestors = CommonAncestors(
            database = repository.database,
            one = commitE,
            two = commitF
        )

        val bca = commonAncestors.find()

        assertEquals(commitC, bca)
    }
}
