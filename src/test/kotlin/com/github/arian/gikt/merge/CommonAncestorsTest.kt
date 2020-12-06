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

    /**
     * (A) B*
     * o<--o
     *  \
     *   o
     *   C*
     */
    @Test
    fun `branched tree`() {
        val repository = repository()

        val commitA = commit(
            repository,
            tree = ObjectId("abc12def12def12def12def12def12def12def11")
        ).oid
        val commitB = commit(
            repository,
            parents = listOf(commitA),
            tree = ObjectId("abc12def12def12def12def12def12def12def12")
        ).oid
        val commitC = commit(
            repository,
            parents = listOf(commitA),
            tree = ObjectId("abc12def12def12def12def12def12def12def13")
        ).oid

        val commonAncestors = CommonAncestors(
            database = repository.database,
            one = commitB,
            two = listOf(commitC)
        )

        val bca = commonAncestors.find()

        assertEquals(listOf(commitA), bca.results)
    }

    /**
     * A   B   D   E*
     * o<--o<--o<--o
     *  \     /
     *   +---o<--o
     *      (C)  F*
     */
    @Test
    fun `merged history with further commits`() {
        val repository = repository()

        val commitA = commit(repository).oid
        val commitB = commit(repository, timeOffset = 1, parents = listOf(commitA)).oid
        val commitC = commit(repository, timeOffset = 2, parents = listOf(commitA)).oid
        val commitD = commit(repository, timeOffset = 3, parents = listOf(commitB, commitC)).oid
        val commitE = commit(repository, timeOffset = 4, parents = listOf(commitD)).oid
        val commitF = commit(repository, timeOffset = 5, parents = listOf(commitC)).oid

        val commonAncestors = CommonAncestors(
            database = repository.database,
            one = commitE,
            two = listOf(commitF)
        )

        val bca = commonAncestors.find()

        assertEquals(listOf(commitC), bca.results)
    }

    /**
     * A   B   C    G    H*
     * o<--o<---o<---o<---o
     *      \       /
     *       o<---o<--o
     *     D     (E)  F*
     */
    @Test
    fun `branching and merging history`() {
        val repository = repository()

        val commitA = commit(repository, timeOffset = 0).oid
        val commitB = commit(repository, timeOffset = 1, parents = listOf(commitA)).oid
        val commitC = commit(repository, timeOffset = 2, parents = listOf(commitB)).oid
        val commitD = commit(repository, timeOffset = 3, parents = listOf(commitB)).oid
        val commitE = commit(repository, timeOffset = 4, parents = listOf(commitD)).oid
        val commitF = commit(repository, timeOffset = 5, parents = listOf(commitE)).oid
        val commitG = commit(repository, timeOffset = 6, parents = listOf(commitC, commitE)).oid
        val commitH = commit(repository, timeOffset = 7, parents = listOf(commitG)).oid

        val commonAncestors = CommonAncestors(
            database = repository.database,
            one = commitH,
            two = listOf(commitF)
        )

        val bca = commonAncestors.find()

        assertEquals(listOf(commitE), bca.results)
    }

    /**
     * A   B   C       J    K
     * o<--o<--o<------o<---o
     *      \         /
     *       o<--o<--o
     *    (D) \  E   F*
     *         o<----o
     *         G     H*
     */
    @Test
    fun `history with many candidates as common ancestor`() {
        val repository = repository()

        val commitA = commit(repository, timeOffset = 0).oid
        val commitB = commit(repository, timeOffset = 1, parents = listOf(commitA)).oid
        val commitC = commit(repository, timeOffset = 2, parents = listOf(commitB)).oid
        val commitD = commit(repository, timeOffset = 3, parents = listOf(commitB)).oid
        val commitE = commit(repository, timeOffset = 4, parents = listOf(commitD)).oid
        val commitF = commit(repository, timeOffset = 5, parents = listOf(commitE)).oid
        val commitG = commit(repository, timeOffset = 6, parents = listOf(commitD)).oid
        val commitH = commit(repository, timeOffset = 7, parents = listOf(commitG)).oid
        val commitJ = commit(repository, timeOffset = 8, parents = listOf(commitC, commitF)).oid
        val commitK = commit(repository, timeOffset = 9, parents = listOf(commitJ)).oid

        val commonAncestors = CommonAncestors(
            database = repository.database,
            one = commitK,
            two = listOf(commitH)
        )

        val bca = commonAncestors.find()

        assertEquals(listOf(commitD), bca.results)
    }

    /**
     * A   (B)    C         H    J*
     * o<--o<-----o<--------o<---o
     *      \      \       /
     *       \      o<----o
     *        \    D   (E)\
     *         o<----------o<---o
     *         F           G    K*
     */
    @Test
    fun `history with many candidate common ancestors with weird dates`() {
        val repository = repository()

        val commitA = commit(repository, timeOffset = 0).oid
        val commitB = commit(repository, timeOffset = 11, parents = listOf(commitA)).oid
        val commitC = commit(repository, timeOffset = 12, parents = listOf(commitB)).oid
        val commitD = commit(repository, timeOffset = 3, parents = listOf(commitC)).oid
        val commitE = commit(repository, timeOffset = 4, parents = listOf(commitD)).oid
        val commitF = commit(repository, timeOffset = 15, parents = listOf(commitB)).oid
        val commitG = commit(repository, timeOffset = 17, parents = listOf(commitF, commitE)).oid
        val commitH = commit(repository, timeOffset = 8, parents = listOf(commitC, commitE)).oid
        val commitJ = commit(repository, timeOffset = 9, parents = listOf(commitH)).oid
        val commitK = commit(repository, timeOffset = 10, parents = listOf(commitG)).oid

        val commonAncestors = CommonAncestors(
            database = repository.database,
            one = commitJ,
            two = listOf(commitK)
        )

        val bca = commonAncestors.find()

        assertEquals(listOf(commitB, commitE), bca.results)
    }
}
