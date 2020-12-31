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

internal class BasesTest {

    private fun repository(): Repository {
        val fs = MemoryFileSystemBuilder.newLinux().build()
        val ws = fs.getPath("/gitk-objects").mkdirp()
        return Repository(ws)
    }

    private fun commit(
        repo: Repository,
        parents: List<ObjectId> = emptyList(),
        timeOffset: Long = 0L,
    ): Commit {
        val zoneId = ZoneId.of("Europe/Amsterdam")

        val time = ZonedDateTime
            .now(Clock.fixed(Instant.parse("2019-08-14T10:08:22.00Z"), zoneId))
            .plusSeconds(timeOffset)

        val commit = Commit(
            parents = parents,
            message = "commit".toByteArray(),
            author = Author("arian", "arian@example.com", time),
            tree = ObjectId("abc12def12def12def12def12def12def12def12")
        )

        repo.database.store(commit)
        return commit
    }

    /**
     * ```
     * A   (B)    C         J    K*
     * o<--o<-----o<--------o<---o
     *      \      \       /
     *       \      o<----o
     *        \    D   (E)\
     *         o<---o<-----o<---o
     *         F    G      H    L*
     * ```
     */
    @Test
    fun `history with many candidates common ancestors with weird dates`() {
        val repository = repository()

        val commitA = commit(repository, timeOffset = 0).oid
        val commitB = commit(repository, timeOffset = 11, parents = listOf(commitA)).oid
        val commitC = commit(repository, timeOffset = 12, parents = listOf(commitB)).oid
        val commitD = commit(repository, timeOffset = 3, parents = listOf(commitC)).oid
        val commitE = commit(repository, timeOffset = 4, parents = listOf(commitD)).oid
        val commitF = commit(repository, timeOffset = 15, parents = listOf(commitB)).oid
        val commitG = commit(repository, timeOffset = 16, parents = listOf(commitF)).oid
        val commitH = commit(repository, timeOffset = 17, parents = listOf(commitG, commitE)).oid
        val commitJ = commit(repository, timeOffset = 8, parents = listOf(commitC, commitE)).oid
        val commitK = commit(repository, timeOffset = 9, parents = listOf(commitJ)).oid
        val commitL = commit(repository, timeOffset = 10, parents = listOf(commitH)).oid

        val bases = Bases(
            database = repository.database,
            one = commitK,
            two = commitL
        )

        assertEquals(listOf(commitE), bases.find())
    }
}
