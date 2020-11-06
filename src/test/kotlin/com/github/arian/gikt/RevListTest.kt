package com.github.arian.gikt

import com.github.arian.gikt.database.Author
import com.github.arian.gikt.database.Commit
import com.github.arian.gikt.database.ObjectId
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

    private fun commit(repo: Repository, parent: ObjectId? = null): Commit {
        val zoneId = ZoneId.of("Europe/Amsterdam")
        val time = ZonedDateTime.now(Clock.fixed(Instant.parse("2019-08-14T10:08:22.00Z"), zoneId))
        val commit = Commit(
            parent = parent,
            message = "commit".toByteArray(),
            author = Author("arian", "arian@example.com", time),
            tree = ObjectId("abc12def12def12def12def12def12def12def12")
        )
        repo.database.store(commit)
        return commit
    }

    @Test
    fun `list linear commits`() {
        val repository = repository()

        val commit1 = commit(repository)
        val commit2 = commit(repository, commit1.oid)
        val commit3 = commit(repository, commit2.oid)

        val revList = RevList(repository, Revision(repository, commit3.oid.hex))

        val log = revList.commits().toList()

        assertEquals(
            listOf(
                commit3,
                commit2,
                commit1
            ),
            log
        )
    }
}
